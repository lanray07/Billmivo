import { createServer } from "node:http";
import { verifyReceipt } from "./verify.mjs";

const MAX_BODY = 4096;
const PORT = Number(process.env.PORT ?? 8080);
const config = { sharedSecret: process.env.AMAZON_RVS_SHARED_SECRET, mode: process.env.AMAZON_RVS_MODE };
const attempts = new Map();
let second = -1;
let upstreamCalls = 0;

function send(response, status, body, headers = {}) {
    response.writeHead(status, { "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store", ...headers });
    response.end(JSON.stringify(body));
}

function permitted(address, now) {
    const currentSecond = Math.floor(now / 1000);
    if (second !== currentSecond) {
        second = currentSecond;
        upstreamCalls = 0;
    }
    if (upstreamCalls >= 5) return false;
    const times = (attempts.get(address) ?? []).filter(time => now - time < 60000);
    if (times.length >= 20) return false;
    attempts.set(address, [...times, now]);
    upstreamCalls += 1;
    return true;
}

const cleanup = setInterval(() => {
    const cutoff = Date.now() - 60000;
    for (const [address, times] of attempts) {
        if (times.at(-1) < cutoff) attempts.delete(address);
    }
}, 60000);
cleanup.unref();

const server = createServer({ requestTimeout: 10000, headersTimeout: 5000 }, async (request, response) => {
    if (request.url !== "/verify-amazon" || request.method !== "POST") {
        request.resume();
        send(response, 404, { error: "not_found" });
        return;
    }
    if (!(request.headers["content-type"] ?? "").match(/^application\/json(?:;|$)/i)) {
        request.resume();
        send(response, 415, { error: "json_required" });
        return;
    }
    try {
        const parts = [];
        let length = 0;
        for await (const part of request) {
            length += part.length;
            if (length > MAX_BODY) {
                send(response, 413, { error: "request_too_large" }, { Connection: "close" });
                return;
            }
            parts.push(part);
        }
        let input;
        try {
            input = JSON.parse(Buffer.concat(parts).toString("utf8"));
        } catch {
            send(response, 400, { error: "invalid_json" });
            return;
        }
        if (!permitted(request.socket.remoteAddress ?? "unknown", Date.now())) {
            send(response, 429, { error: "try_again_later" }, { "Retry-After": "60" });
            return;
        }
        const result = await verifyReceipt(input, config);
        send(response, result.status, result.body, result.headers);
    } catch {
        if (!response.headersSent && !response.destroyed) send(response, 503, { error: "verification_unavailable" });
    }
});

if (!config.sharedSecret || !["sandbox", "production"].includes(config.mode)) {
    throw new Error("Set AMAZON_RVS_SHARED_SECRET and AMAZON_RVS_MODE before starting verification.");
}
server.listen(PORT);
