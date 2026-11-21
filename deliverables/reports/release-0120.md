# Billmivo 0.12.0 release checkpoint

21 November 2026 · Android version code 13

## Implemented

Polish covers all 387 interface strings and both plural groups, including setup, reminders, sharing, reports, purchase states and privacy controls. Settings offers **Polski**, matching device languages select Polish, and language-only new bills default to PLN. Existing records retain their explicit currency. Polish adds the `one`, `few`, `many` and `other` forms.

Shortcuts support 7/30/90 number words, five navigation actions, comma-decimal drafts, exact-name payment review and dated reminders. Weekday recognition accepts natural cases such as **w środę**, **w sobotę**, **w niedzielę** and **we wtorek**, alongside today, tomorrow and ISO dates. Payment parsing preserves a bill name beginning with **Rachunek**, rather than stripping part of the saved name.

The local listing package adds Polish title, descriptions, feature bullets, search terms, support copy, release notes and eight screenshot headlines. Source is `D:/App Projects/Billmivo`, with the existing Compose UI, local Room repository, offline assistant and opt-in Amazon SDK build. No new backend service is required.

## Verification

| Check | Status | Evidence |
| --- | --- | --- |
| Localized resources | PASS | 387/387 strings, placeholder parity and required plural categories match across ten interface languages. |
| JVM tests | PASS | All 29 tests passed. New cases cover Polish currency, three forecast number words, all five routes, exact payment names, decimal drafts, all seven weekdays, today/tomorrow and invalid dates. |
| Fire regression | PASS | Both initial and final builds passed all 14 tests on Fire HD 10 (2021), KFTRWI, Fire OS 7 / API 28, 1920 × 1200 landscape. |
| Polish runtime quantities | PASS | The final capture verified both plural groups for 1, 2, 5, 12 and 22 using the Fire's actual Android resources. |
| Polish command reviews | PASS | Typed commands opened the exact water bill's payment review and a Friday reminder review. Both were cancelled, and the repository snapshot remained unchanged. |
| Store assets | PASS | Eight distinct Polish Fire screenshots composed and inspected. The validator passed nine storefront sets, 72 compositions in total, with native dimensions and distinct raw scenes. |
| Builds | PASS | Debug, instrumentation, offline release and production-mode Amazon release variants assembled. Minimum API remains 28 and target API remains 35. Release APKs are unsigned. |
| Receipt verifier | PASS | All seven local Node tests passed. |
| 0.11.0 private purchase and restore | PASS | Amazon-installed code 12 passed a fresh £7.56 test checkout, automatic activation and separate Portuguese, Dutch and Swedish restore checks. The original device-language setting was restored. |
| 0.12.0 private billing | PASS | Uploaded the original code-13 APK to Test 1; Amazon installed it on the Fire. A fresh £7.56 test purchase activated Premium automatically. Separate Polish restore completed successfully. Device-language selection was restored and app lock remained off. |
| New draft descriptions | PASS | Portuguese (Brazil), Dutch, Swedish and Polish descriptions were filled in App Setup and displayed Complete. |
| Portuguese draft images | PASS | All eight actual Portuguese screenshots uploaded in scene order; all eight thumbnails loaded. Universal icons and promotion retained. |
| Other new image locales | BLOCKED | The Console image picker does not offer Dutch, Polish or Swedish. Their real localized assets are retained locally. |

Logs are under `deliverables/android/build-logs`, including `verify-0120-final.txt`, `verify-0120-amazon-final.txt`, `fire-0120-final/fire-instrumentation.txt` and `capture-0120-pl-PL-final.txt`. The Fire capture fixture remains separate from the ordinary regression suite and only seeds the isolated debug package after checking for known fictional records.

## Language and voice scope

The ten interface languages are English, French, Spanish, Arabic, German, Italian, Brazilian Portuguese, Dutch, Swedish and Polish. Arabic retains RTL support. Microphone recognition requires a device speech service; the tested Fire exposes none. The Polish voice dialog displayed its translated unavailable state and retained typed and touch controls. The device test exercised typed review flows. Audible Polish TTS output and a compatible microphone recognizer are **NOT TESTED**.

Hindi, Japanese, Korean, Simplified Chinese, Traditional Chinese, Turkish and Indonesian remain unimplemented. Additional requested regional variants also need wording review and actual app captures.

## Privacy and release checks

This language change introduces no permissions, analytics service or cloud bill upload. Existing encrypted backup, local deletion, private reminder delivery, report generation, household sharing and financial calculations passed the Fire regression suite. These checks do not constitute a full security or legal assessment. Signing secrets and verifier credentials remain outside the repository; `store/receipt-verifier/.env.example` documents the server environment.

The deployed production verifier remains the existing endpoint. Amazon LAT uses production RVS; its test purchases are not charged. [Amazon IAP testing overview](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html)

The 0.11.0 private submission retained 12 selected Fire tablets. The Console still warns that target API 35 does not meet the minimum for multiple newer devices. Newer Fire OS/device compatibility requires a separate target-SDK assessment and hardware verification. [Amazon device filtering](https://developer.amazon.com/docs/app-submission/device-filtering-and-compatibility.html)

The public App Setup draft is incomplete. Its target-audience age choices, 13 content-rating fields, data-privacy answers and public privacy-policy URL still require completion, along with final publisher details and review of pricing, markets and eligible devices. The image-locale picker does not offer Dutch, Polish or Swedish, despite their availability in the description picker. Local assets are retained; no substitute English screenshots are claimed as localized. Public submission remains **BLOCKED**, and has not been attempted.

Detailed VoiceView review, the short-background app-lock check and other device/orientation coverage remain **NOT TESTED**. The separate more-than-two-minute app-lock test passed on the Amazon-installed 0.10.0 and its original off setting was restored; see [the hardware report](app-lock-timeout.md).

## Build artifacts and instructions

- Debug APK — `deliverables/android/debug/Billmivo-0.12.0-debug.apk`.
- Instrumentation APK — `deliverables/android/debug/Billmivo-0.12.0-androidTest.apk`.
- Offline release — `deliverables/android/release/Billmivo-0.12.0-release-unsigned.apk`.
- Amazon release — `deliverables/android/release/Billmivo-0.12.0-amazon-release-unsigned.apk`.
- Preparation archive — `deliverables/android/release/Billmivo-0.12.0-amazon-preparation.zip`.
- Screenshots — eight PNG files in `deliverables/store-assets/amazon/pl-PL`; all nine sets are listed in `asset-manifest.json`.

Use JDK 17, Gradle 8.11.1, Android SDK 35 and build-tools 35.0.0. Build the offline variants with `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease`; use the Amazon properties and existing production verifier URL documented in `store/README.md` for the Amazon release. The local build used the cached SDK's aapt2 override. Run `tools/check-locales.ps1`, `tools/test-fire.ps1`, the explicit Polish capture and `tools/validate-amazon-package.py --raw deliverables/store-assets/amazon-raw` for the corresponding checks.

The master prompt is not complete. The next work is the remaining seven languages, vendor image-locale limitations, broader accessibility/device checks and current-version private billing, before completing and authorizing a public release.

## Private LAT follow-up — 21 November 2026

Amazon accepted **Billmivo 0.12.0 Polish** and showed **Test in-progress**. The exact code-13 APK uploaded has SHA-256 `7b00507cd0693859718b2f8adcbae81a2c199c88239675faeba43736584ed471`. The existing Test 1 and its single previously approved tester were retained. Polish was added to the copied binary-language flags; 12 Fire tablets, no Fire TV and no Automotive devices remained selected. The existing export certification was copied, with no cryptographic changes.

The existing tester entitlement was reset at 12:42 PM. The Fire downloaded the update from Amazon Appstore; package inspection confirmed version 0.12.0/code 13 and installer `com.amazon.venezia`. The Polish paywall first showed no verified purchase and £7.56. After the approved test checkout, Premium activated automatically. A separate restore displayed **Sprawdzanie w Amazon...** and returned **Premium jest aktywne.** The device-language setting was restored and the privacy screen showed **Enable app lock**, confirming its original off setting.

Screenshots are under `deliverables/android/build-logs`: `amazon-lat-0120-live.jpg`, `lat-0120-pl-no-entitlement.png`, `lat-0120-pl-purchase-active.png`, `lat-0120-pl-restore-checking.png`, `lat-0120-pl-restore-active.png` and `lat-0120-lock-off.png`. Purchase success validates the production-mode verifier path for this private Amazon build; it does not verify public availability or any 0.13.0 purchase flow.
