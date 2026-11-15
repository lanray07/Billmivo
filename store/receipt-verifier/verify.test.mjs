import test from "node:test";
import assert from "node:assert/strict";
import { verifyReceipt } from "./verify.mjs";

const input = { userId: "test-user", receiptId: "test-receipt" };
const production = { sharedSecret: "test-only-value", mode: "production" };
const valid = { receiptId: input.receiptId, productId: "billmivo_premium",
    productType: "ENTITLED", cancelDate: null, testTransaction: false };
const succeeds = (data) => async () => new Response(JSON.stringify(data), { status: 200 });

test("permits only the expected verified entitlement", async () => {
    const result = await verifyReceipt(input, production, succeeds(valid));
    assert.deepEqual(result.body, { entitled: true, mode: "production" });
    assert.equal(result.headers["Cache-Control"], "no-store");
    for (const item of [{ productId: "different-sku" }, { receiptId: "different-receipt" },
        { productType: "CONSUMABLE" }, { cancelDate: 1500000000000 }]) {
        assert.equal((await verifyReceipt(input, production, succeeds({ ...valid, ...item }))).body.entitled, false);
    }
});

test("separates testing receipts from production", async () => {
    const sample = { ...valid, testTransaction: true };
    assert.equal((await verifyReceipt(input, production, succeeds(sample))).body.entitled, false);
    assert.equal((await verifyReceipt(input, { ...production, mode: "sandbox" }, succeeds(sample))).body.entitled, true);
});

test("rejections, throttling, malformation and unavailable credentials have different outcomes", async () => {
    for (const status of [400, 410, 497]) {
        assert.equal((await verifyReceipt(input, production, async () => new Response(null, { status }))).body.entitled, false);
    }
    for (const status of [429, 496, 500]) {
        assert.equal((await verifyReceipt(input, production, async () => new Response(null, { status }))).status, 503);
    }
    assert.equal((await verifyReceipt(input, production, succeeds({ receiptId: input.receiptId }))).status, 503);
    assert.equal((await verifyReceipt(input, { ...production, sharedSecret: "" }, succeeds(valid))).status, 503);
});

test("rejects malformed identifiers before contacting Amazon and refuses redirects", async () => {
    const malicious = await verifyReceipt({ ...input, userId: "../other" }, production, () => {
        throw new Error("must not call upstream");
    });
    assert.equal(malicious.status, 400);
    const validUser = { ...input, userId: "user+A==" };
    const result = await verifyReceipt(validUser, { ...production, mode: "sandbox" }, async (url, opts) => {
        assert.ok(url.startsWith("https://appstore-sdk.amazon.com/sandbox/"));
        assert.ok(url.includes("/user/user%2BA%3D%3D/"));
        assert.equal(opts.redirect, "error");
        return new Response(JSON.stringify(valid), { status: 200 });
    });
    assert.equal(result.body.entitled, true);
});
