# Amazon Appstore release preparation

## Listing pack

Use `listings.json` for the display titles, store descriptions, feature bullets and keywords. The United Kingdom, France and Spain sets have eight Fire tablet screenshots each. Provide the English copy as the default storefront description as well. Arabic is a supported app language. The locale matrix records storefront availability separately.

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

1. In an authorized Amazon Developer Console draft, create the entitlement SKU `billmivo_premium` and configure marketplace prices.
2. Put the app's public `AppstoreAuthenticationKey.pem` at `app/src/amazon/assets/AppstoreAuthenticationKey.pem`. Keep the RVS shared secret on the server. [Appstore SDK integration](https://developer.amazon.com/docs/appstore-sdk/integrate-appstore-sdk.html)
3. Deploy the verifier behind HTTPS in an authorized environment using the server settings below. Keep separate public endpoints for test and production. The client checks the verification mode and uses an app-private entitlement cache.
4. Build a test APK, install Amazon App Tester in the test environment, and run successful purchase, pending, cancelled, rejected receipt, refunded, account switch, restore, offline reopen, and failed-verifier cases on a supported Fire tablet.
5. Build with the production verifier URL and production key, verify the manifest and release binary, then run Live App Testing or the applicable Amazon test programme. Upload and submission are separate console actions. [Amazon IAP and receipt fulfillment](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html)

```powershell
.\gradlew.bat :app:assembleDebug -PbillmivoAmazon=true -PbillmivoAmazonSandbox=true -PbillmivoVerifierUrl=https://YOUR-SANDBOX-HOST/verify-amazon
.\gradlew.bat :app:assembleRelease -PbillmivoAmazon=true -PbillmivoVerifierUrl=https://YOUR-PRODUCTION-HOST/verify-amazon
```

Amazon's guides describe signing for its test workflows and note that Amazon applies its signature to uploaded apps. Keep signing credentials out of source control. [Amazon IAP testing setup](https://developer.amazon.com/docs/in-app-purchasing/iap-get-started.html) · [Amazon binary submission](https://developer.amazon.com/docs/app-submission/submitting-apps-to-amazon-appstore.html)

### Receipt verifier

The server under `store/receipt-verifier` uses Node’s built-in HTTP and Fetch APIs. Configure `AMAZON_RVS_MODE` and `AMAZON_RVS_SHARED_SECRET` through your hosting secret store. Start it behind a TLS-terminating host with `node store/receipt-verifier/server.mjs`. Its in-process limit is designed for a single instance. Use shared upstream throttling if deploying multiple instances. The app sends only the Amazon receipt ID and Amazon user ID to this endpoint. The server does not log or retain them.

Run `node --test store/receipt-verifier/verify.test.mjs` for the local verification tests. They exercise acceptance, refusals, cancellations, sandbox isolation and upstream failures. Real purchasing, restoration and fulfillment require an authenticated Amazon test setup. [Receipt Verification Service](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html)

See [localisation preparation](localisation.md) for regional scope and the translation workflow.
