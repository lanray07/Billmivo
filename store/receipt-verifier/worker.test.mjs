import test from "node:test";
import assert from "node:assert/strict";
import { handleRequest } from "./worker.mjs";

const origin = "https://verifier.example";
const sandbox = {
    AMAZON_RVS_MODE: "sandbox",
    AMAZON_RVS_LIMIT: { limit: async () => ({ success: true }) },
};
const receipt = { userId: "amazon-test-user", receiptId: "amazon-test-receipt" };

function post(body = JSON.stringify(receipt), headers = { "Content-Type": "application/json" }) {
    return new Request(`${origin}/verify-amazon`, { method: "POST", headers, body });
}

const unexpected = () => assert.fail("An upstream request was not expected");

test("sandbox uses Amazon's sandbox and needs no production secret", async () => {
    const response = await handleRequest(post(), sandbox, async (url) => {
        assert.match(url, /\/sandbox\/version\/1.0\/.*\/billmivo-sandbox\//);
        return Response.json({ ...receipt, productId: "billmivo_premium", productType: "ENTITLED", cancelDate: null });
    });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("Cache-Control"), "no-store");
    assert.deepEqual(await response.json(), { entitled: true, mode: "sandbox" });
});

test("production stays unavailable without its secret and calls the production API when configured", async () => {
    const prod = { ...sandbox, AMAZON_RVS_MODE: "production" };
    const health = new Request(`${origin}/healthz`);
    assert.equal((await handleRequest(health, prod)).status, 503);
    assert.equal((await handleRequest(post(), prod, unexpected)).status, 503);
    const configured = { ...prod, AMAZON_RVS_SHARED_SECRET: "test-production-secret" };
    assert.deepEqual(await (await handleRequest(health, configured)).json(), { status: "configured", mode: "production" });
    const response = await handleRequest(post(), configured, async (url) => {
        assert.match(url, /^https:\/\/appstore-sdk\.amazon\.com\/version\/1.0\/.*\/test-production-secret\//);
        return new Response(null, { status: 410 });
    });
    assert.deepEqual(await response.json(), { entitled: false, mode: "production" });
});

test("an invalid request or throttle does not reach Amazon", async () => {
    assert.equal((await handleRequest(new Request(`${origin}/verify-amazon`), sandbox, unexpected)).status, 405);
    assert.equal((await handleRequest(post("{}", {}), sandbox, unexpected)).status, 415);
    assert.equal((await handleRequest(post("{"), sandbox, unexpected)).status, 400);
    assert.equal((await handleRequest(post("x".repeat(4097)), sandbox, unexpected)).status, 413);
    const throttled = { ...sandbox, AMAZON_RVS_LIMIT: { limit: async () => ({ success: false }) } };
    const response = await handleRequest(post(), throttled, unexpected);
    assert.equal(response.status, 429);
    assert.equal(response.headers.get("Retry-After"), "60");
});
