# Amazon Appstore release preparation

## Listing pack

Use `listings.json` for the display titles, store descriptions, feature bullets and keywords. The United Kingdom, France and Spain sets have eight Fire tablet screenshots each. Provide the English copy as the default storefront description as well. Arabic is a supported app language. The locale matrix records storefront availability separately.

Amazon stopped accepting new Android mobile listings and ended Appstore distribution for non-Fire Android phones in 2025. The Android build can still be used for phone compatibility testing. The submission artwork targets Fire tablets. [Amazon device distribution announcement](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program)

Set the app category to Finance if that category appears in the Developer Console. Use the storefront’s category picker as the source of truth. Keywords are mapped to actual features and search phrases. There are no price claims, endorsements or unsupported service claims. [Amazon's listing fields and media rules](https://developer.amazon.com/docs/app-submission/appstore-details.html)

Both required transparent icons and the 1920 × 1200 screenshot compositions are under `deliverables/store-assets/amazon`. The artwork is generated from the original Billmivo mark and the actual Fire app with fictional household data. Raw captures are retained locally. Use `python tools/validate-amazon-package.py --raw deliverables/store-assets/amazon-raw` to validate the package. [Fire tablet test criteria](https://developer.amazon.com/docs/app-testing/test-criteria.html)

The package contains a privacy notice draft. A publisher still needs to supply the legal name, support details and privacy contact, review the disclosures, and provide the final public URL during submission. Distribution eligibility, age rating, and the app label should be confirmed in the Console before wider release.

## Offline build

The default Gradle build has offline bill tracking, notification reminders, sharing, provider records, attachment import and reports. It has no Amazon SDK or Internet permission. It can be tested without a developer account and keeps local data after upgrades. The release APK is unsigned.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
```

## Amazon Premium build

The opt-in Amazon build uses Amazon Appstore SDK 3.0.9. It retrieves the localized price, supports purchase and restore, verifies receipts with a server before fulfillment, and respects cancellation and sandbox status. A verified entitlement is cached for three days. Existing records and allocations remain readable after an account change or entitlement lapse. The free plan supports ten active recurring bills, with one-time bills and reminders available. The one-time Premium item adds unlimited recurring bills, advanced forecast periods and editing of household splits.

1. Open **My Apps → Billmivo → In-App Items → Add Single IAP → Entitlement**. Name the item `Billmivo Premium` and enter the case-sensitive SKU `billmivo_premium`. In **Description & Images**, describe the one-time upgrade and its three benefits. In **Pricing**, select **Yes** and enter your approved price and marketplace overrides. Save the draft. Choose **Export Multiple IAPs → JSON** to verify the actual listing in App Tester. [Amazon's in-app item instructions](https://developer.amazon.com/docs/in-app-purchasing/iap-create-and-submit-iap-items.html)
2. Under **Upload Your App File → Additional information → View public key**, download Billmivo's `AppstoreAuthenticationKey.pem`. Put it at `app/src/amazon/assets/AppstoreAuthenticationKey.pem` for the production build. Git ignores that local path. [Amazon's public key instructions](https://developer.amazon.com/docs/appstore-sdk/appstore-sdk-troubleshooting.html)
3. Install **Amazon App Tester** from the Fire tablet's Appstore. Grant its overlay permission, put the exported catalog at `/sdcard/amazon.sdktester.json`, and enable the tablet's debug sandbox mode. A protected debug APK also works with a locally authored test catalog. Check the SKU in App Tester's **IAP Items in JSON File** before purchasing. [Amazon App Tester setup](https://developer.amazon.com/docs/in-app-purchasing/iap-install-and-configure-app-tester.html)
4. For Live App Testing, finish the production verifier setup below. Build with the production verifier URL and the Billmivo public key. Verify the signed test binary and set the tablet's sandbox mode to `none`. In the Console, submit the IAP to **Test** and use **Live App Testing** to invite the Fire tablet's test account. Verify purchase and restore there with the production RVS endpoint. [Amazon Live App Testing for IAP](https://developer.amazon.com/docs/in-app-purchasing/iap-lat.html)

```powershell
.\gradlew.bat :app:assembleDebug -PbillmivoAmazon=true -PbillmivoAmazonSandbox=true -PbillmivoVerifierUrl=https://billmivo-receipts-sandbox.lanraybanks.workers.dev/verify-amazon
.\gradlew.bat :app:assembleRelease -PbillmivoAmazon=true -PbillmivoVerifierUrl=https://YOUR-PRODUCTION-HOST/verify-amazon
adb shell appops set com.amazon.sdktestclient SYSTEM_ALERT_WINDOW allow
adb push .\amazon.sdktester.json /sdcard/amazon.sdktester.json
adb shell setprop debug.amazon.sandboxmode debug
```

On 15 November 2026, the Fire HD 10 (2021, Fire OS 7) loaded the Billmivo SKU from a local App Tester catalog. A fresh checkout for a second sandbox account activated Premium after Cloudflare verified the receipt with Amazon's sandbox RVS. App Tester recorded the transaction as `FULFILLED`. With an earlier sandbox receipt and no cached entitlement, the in-app Restore button independently activated Premium. Changing sandbox accounts cleared the prior account's entitlement. The local US$9.99 amount was test data. Run Live App Testing with the Console catalog and production verifier before release.

The `com.billmivo.app` 0.3.0 paid APK passed the release build and was distributed through the private **Billmivo Fire HD 10 purchase and restore** track. The Console approved the one-time US$9.99 entitlement for testing. On 16 November 2026, the Amazon-installed build completed the test checkout on the Fire HD 10 (2021), model `KFTRWI`, at the localized £7.56 test price. The app timed out waiting for checkout. Tapping **Restore Amazon purchase** activated Premium against production RVS without making another purchase. Version 0.3.1 gives the Amazon checkout five minutes and starts a separate 45-second deadline for verification after the callback. If checkout times out, the app checks purchase history automatically. The same tablet activated Premium without manual restore after keeping the App Tester checkout open for 75 seconds on 0.3.1. Amazon delivered 0.3.1 through the same private track, and the upgraded app verified the existing test purchase through production RVS when **Restore Amazon purchase** was tapped. Keep the test entitlement separate from any decision about public pricing or availability.

On 16 November 2026, **Billmivo 0.4.0 forecast and encrypted backup** went live in the same private test. The Fire HD 10 updated from the Appstore and reported version code 5. In **Manage testers**, the previous entitlement was reset for the single intended Fire tester, and the app confirmed there was no verified purchase. A fresh checkout displayed £7.56, Amazon confirmed completion, and Premium activated with no restore action. **Restore Amazon purchase** subsequently initiated a separate Amazon check and returned to active Premium. Amazon documents that Live App Testing purchases are not charged. [Amazon IAP testing modes](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html) · [Resetting tester entitlements](https://developer.amazon.com/docs/app-testing/manage-testers.html)

The copied private test retained its 12 Fire tablet selections. Amazon warns that target API 35 excludes devices with a minimum target of 36. It lists API 36 for Fire OS 16 and API 28 for Fire OS 7. Verify the final list of eligible tablets in the Console for the release APK. [Amazon target API requirements](https://developer.amazon.com/docs/app-submission/device-filtering-and-compatibility.html#minimum-api-levels-for-targetsdkversion)

Amazon's guides describe signing for its test workflows and note that Amazon applies its signature to uploaded apps. Keep signing credentials out of source control. [Amazon IAP testing setup](https://developer.amazon.com/docs/in-app-purchasing/iap-get-started.html) · [Amazon binary submission](https://developer.amazon.com/docs/app-submission/submitting-apps-to-amazon-appstore.html)

### Receipt verifier

The Cloudflare Worker under `store/receipt-verifier` separates sandbox and production. The sandbox at `https://billmivo-receipts-sandbox.lanraybanks.workers.dev` uses Amazon's sandbox RVS. Amazon accepts a non-secret placeholder in sandbox mode. The app sends the Amazon receipt ID and Amazon user ID in a JSON POST to `/verify-amazon`. The Worker disables invocation logging, rate-limits by connecting IP, and sends JSON with `Cache-Control: no-store`. [Amazon sandbox RVS](https://developer.amazon.com/docs/in-app-purchasing/rvs-cloud-sandbox.html)

For production RVS and Live App Testing, open the [Amazon Shared Key page](https://developer.amazon.com/sdk/shared-key.html) and copy the IAP shared key. In the Cloudflare Dashboard, go to **Workers & Pages → billmivo-receipts-production → Settings → Variables and Secrets → Add**, select **Secret**, name it `AMAZON_RVS_SHARED_SECRET`, and deploy the change. Keep the shared key in Cloudflare. The Live App Testing build can use `https://billmivo-receipts-production.lanraybanks.workers.dev/verify-amazon`. Its `/healthz` route returns 503 until the secret is set and then identifies the mode as `production`. A Live App Testing receipt verifies the upstream shared key and catalog. For the public release, Cloudflare recommends adding a dedicated HTTPS hostname in a Cloudflare-managed zone under **Domains & Routes**. [Cloudflare secrets](https://developers.cloudflare.com/workers/configuration/secrets/) · [Cloudflare production routing](https://developers.cloudflare.com/workers/configuration/routing/workers-dev/)

Deploy the sandbox with `npx wrangler@4.162.0 deploy --config store/receipt-verifier/wrangler.toml --env=""`. For production updates, use `--env production` and preserve the configured domain. A standalone Node server is also available for a single instance behind HTTPS with `AMAZON_RVS_MODE` and `AMAZON_RVS_SHARED_SECRET` configured by the host. Run `node --test 'store/receipt-verifier/*.test.mjs'` for verifier and Worker tests. [Amazon production RVS](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html)

See [localisation preparation](localisation.md) for regional scope and the translation workflow.

## Local records and backups

The optional app lock uses the OS credential prompt on compatible devices with a screen lock. It applies on a cold launch and after two minutes away from Billmivo. The app blocks app-window screenshots while its lock is enabled. The setting does not change the device’s credential or encrypt the live Room database.

The `.billmivo` archive uses AES-256-GCM with a random nonce and a key derived from the passphrase with PBKDF2-HMAC-SHA-256, 600,000 iterations and a random salt. The encrypted payload contains Room records and attachment bytes. The payload is limited to 24 MiB and no passphrase is stored. A restore verifies authentication, enforces input bounds and replaces records in a transaction. Purchases, display preferences and notification permission are device-specific. The app uses the system file picker for user-chosen destinations and leaves external copies under the user’s control.
