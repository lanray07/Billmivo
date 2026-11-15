import { verifyReceipt } from "./verify.mjs";

const MAX_BODY = 4096;

function json(status, body, headers = {}) {
    return Response.json(body, { status, headers: { "Cache-Control": "no-store", ...headers } });
}

async function readJson(body) {
    if (!body) return json(400, { error: "invalid_json" });
    const reader = body.getReader();
    const bytes = new Uint8Array(MAX_BODY);
    let length = 0;
    try {
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            if (length + value.length > MAX_BODY) {
                await reader.cancel();
                return json(413, { error: "request_too_large" });
            }
            bytes.set(value, length);
            length += value.length;
        }
        return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes.subarray(0, length)));
    } catch {
        return json(400, { error: "invalid_json" });
    } finally {
        reader.releaseLock();
    }
}

export async function handleRequest(request, env, upstream = fetch) {
    const { pathname, search } = new URL(request.url);
    const { AMAZON_RVS_MODE: mode, AMAZON_RVS_SHARED_SECRET: productionSecret } = env;
    // Amazon accepts any nonempty value in its sandbox. Production uses its own secret binding.
    const sharedSecret = mode === "sandbox" ? "billmivo-sandbox" : productionSecret;
    const configured = !!sharedSecret && (mode === "sandbox" || mode === "production");

    if (pathname === "/healthz" && !search && request.method === "GET") {
        return configured ? json(200, { status: "configured", mode }) : json(503, { status: "unavailable" });
    }
    if (pathname !== "/verify-amazon" || search) return json(404, { error: "not_found" });
    if (request.method !== "POST") return json(405, { error: "method_not_allowed" }, { Allow: "POST" });
    if (!configured) return json(503, { error: "verification_unavailable" });
    if (!/^application\/json(?:;|$)/i.test(request.headers.get("Content-Type") ?? "")) {
        return json(415, { error: "json_required" });
    }
    if (Number(request.headers.get("Content-Length")) > MAX_BODY) {
        return json(413, { error: "request_too_large" });
    }
    const input = await readJson(request.body);
    if (input instanceof Response) return input;
    try {
        const address = request.headers.get("CF-Connecting-IP") ?? "unknown";
        const { success } = await env.AMAZON_RVS_LIMIT.limit({ key: address });
        if (!success) return json(429, { error: "try_again_later" }, { "Retry-After": "60" });
    } catch {
        return json(503, { error: "verification_unavailable" });
    }
    const result = await verifyReceipt(input, { sharedSecret, mode }, upstream);
    return json(result.status, result.body, result.headers);
}

export default { fetch: (request, env) => handleRequest(request, env) };
