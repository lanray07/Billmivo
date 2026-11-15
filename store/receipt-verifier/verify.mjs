const SKU = "billmivo_premium";
const AMAZON = "https://appstore-sdk.amazon.com";
const identifier = (value, max) => typeof value === "string" && value.length <= max &&
    /^[A-Za-z0-9_+=:-]+$/.test(value);

const answer = (status, body) => ({
    status,
    body,
    headers: { "Cache-Control": "no-store", "Content-Type": "application/json; charset=utf-8" },
});

export async function verifyReceipt(input, options, request = fetch) {
    const { sharedSecret, mode } = options;
    if (!sharedSecret || !["sandbox", "production"].includes(mode)) {
        return answer(503, { error: "verification_unavailable" });
    }
    const { userId, receiptId } = input ?? {};
    if (!identifier(userId, 512) || !identifier(receiptId, 2048)) {
        return answer(400, { error: "invalid_request" });
    }
    const endpoint = mode === "sandbox" ? AMAZON + "/sandbox" : AMAZON;
    const url = endpoint + "/version/1.0/verifyReceiptId/developer/" + encodeURIComponent(sharedSecret) +
        "/user/" + encodeURIComponent(userId) + "/receiptId/" + encodeURIComponent(receiptId);

    try {
        const response = await request(url, { redirect: "error", signal: AbortSignal.timeout(7000) });
        if ([400, 410, 497].includes(response.status)) {
            return answer(200, { entitled: false, mode });
        }
        if (response.status !== 200) return answer(503, { error: "verification_unavailable" });

        const receipt = await response.json();
        if (!receipt || typeof receipt !== "object" || !Object.hasOwn(receipt, "cancelDate")) {
            return answer(503, { error: "verification_unavailable" });
        }
        const entitled = receipt.receiptId === receiptId && receipt.productId === SKU &&
            receipt.productType === "ENTITLED" && receipt.cancelDate === null &&
            (mode === "sandbox" || receipt.testTransaction !== true && receipt.betaProduct !== true);
        return answer(200, { entitled, mode });
    } catch {
        return answer(503, { error: "verification_unavailable" });
    }
}
