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

On 15 November 2026, the Fire HD 10 (2021, Fire OS 7) loaded the Billmivo SKU from a local App Tester catalog. A fresh checkout for a second sandbox account activated Premium after Cloudflare verified the receipt with Amazon's sandbox RVS. With an earlier sandbox receipt and no cached entitlement, the in-app Restore button independently activated Premium. Changing sandbox accounts cleared the prior account's entitlement. The local US$9.99 amount was test data. Run Live App Testing with the Console catalog and production verifier before release.

Amazon's guides describe signing for its test workflows and note that Amazon applies its signature to uploaded apps. Keep signing credentials out of source control. [Amazon IAP testing setup](https://developer.amazon.com/docs/in-app-purchasing/iap-get-started.html) · [Amazon binary submission](https://developer.amazon.com/docs/app-submission/submitting-apps-to-amazon-appstore.html)

### Receipt verifier

The Cloudflare Worker under `store/receipt-verifier` separates sandbox and production. The sandbox at `https://billmivo-receipts-sandbox.lanraybanks.workers.dev` uses Amazon's sandbox RVS. Amazon accepts a non-secret placeholder in sandbox mode. The app sends the Amazon receipt ID and Amazon user ID in a JSON POST to `/verify-amazon`. The Worker disables invocation logging, rate-limits by connecting IP, and sends JSON with `Cache-Control: no-store`. [Amazon sandbox RVS](https://developer.amazon.com/docs/in-app-purchasing/rvs-cloud-sandbox.html)

For production, open the [Amazon Shared Key page](https://developer.amazon.com/sdk/shared-key.html) and copy the IAP shared key. In the Cloudflare Dashboard, go to **Workers & Pages → billmivo-receipts-production → Settings → Variables and Secrets → Add**, select **Secret**, name it `AMAZON_RVS_SHARED_SECRET`, and deploy the change. Under **Domains & Routes**, add a dedicated HTTPS hostname in an existing Cloudflare-managed zone. Send the verifier hostname back for the build. Keep the shared key in Cloudflare. Check `GET /healthz` for `configured` and `production`; this confirms configuration only. A Live App Testing receipt verifies the upstream shared key and catalog. [Cloudflare secrets](https://developers.cloudflare.com/workers/configuration/secrets/) · [Cloudflare production routing](https://developers.cloudflare.com/workers/configuration/routing/workers-dev/)

Deploy the sandbox with `npx wrangler@4.162.0 deploy --config store/receipt-verifier/wrangler.toml --env=""`. For production updates, use `--env production` and preserve the configured domain. A standalone Node server is also available for a single instance behind HTTPS with `AMAZON_RVS_MODE` and `AMAZON_RVS_SHARED_SECRET` configured by the host. Run `node --test 'store/receipt-verifier/*.test.mjs'` for verifier and Worker tests. [Amazon production RVS](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html)

See [localisation preparation](localisation.md) for regional scope and the translation workflow.
