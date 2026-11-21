# Billmivo 0.13.0 release checkpoint

21 November 2026 · Android version code 14

## Implemented

Turkish covers all 388 strings and both plural groups across the dashboard, onboarding, reminders, household sharing, reports, assistant, billing, privacy controls and accessibility labels. Device-language selection and the manual **Türkçe** option use `tr`; language-only new bills default to TRY. Existing records retain their explicit currency. Dates and amounts use the active locale.

Typed shortcuts support 7/30/90 number words, five navigation commands, comma-decimal drafts, exact-name payment review and reminders using weekdays, today, tomorrow or ISO dates. Turkish dotted and dotless I are handled through locale-aware matching. For example, **EV İNTERNETİ İÇİN ÖDEMEYİ İNCELE** opens payment review for **Ev interneti**. **Elektrik ödemesini cuma günü hatırlat** opens a Friday reminder review. These commands require review before saving.

The local store package includes Turkish title, descriptions, feature bullets, search terms, support copy, release notes and eight screenshot headlines. The architecture remains Kotlin/Compose, local Room records, an offline assistant and an opt-in Amazon SDK build. No backend service or permission was added.

## Verification

| Check | Status | Evidence |
| --- | --- | --- |
| Resource coverage | PASS | Eleven languages each match 388/388 strings, placeholders and required plural forms. |
| JVM tests | PASS | All 31 tests passed. Turkish cases cover three forecast number words, all five routes, dotted/dotless capital I, exact record lookup, local currency/decimal drafts, seven weekdays, today/tomorrow, ISO dates and invalid dates. |
| Fire regression | PASS | All 14 tests passed on KFTRWI Fire HD 10 (2021), Fire OS 7 / API 28, in 74.324 seconds. |
| Turkish runtime quantities | PASS | Both plural groups checked for 0, 1, 2, 5 and 22 on actual Android resources. |
| Turkish review flows | PASS | The Fire opened the exact internet payment review and the correct Friday reminder review; both were cancelled and records compared unchanged. |
| Voice fallback | PASS | The Turkish unavailable-speech message was visible and typed/touch alternatives worked. This Fire has no compatible speech recognizer. |
| Store artwork | PASS | Eight actual Turkish scenes captured at 1920 × 1200, composed and visually inspected. Ten local storefront sets / 80 scenes pass validation with distinct raw images. |
| Offline builds | PASS | Debug, instrumentation and unsigned release assembled; release vital lint passed. |
| Amazon build | PASS | Unsigned production-mode release assembled with sandbox disabled and the existing HTTPS receipt verifier. Release vital lint passed. |
| Receipt verifier | PASS | All seven local Node tests passed. |
| 0.12.0 LAT billing | PASS | Original code-13 APK uploaded to existing Test 1, installed by Amazon; fresh purchase activated Premium automatically and separate Polish restore succeeded. |
| 0.13.0 LAT billing | NOT TESTED | This locally built code-14 APK has not been uploaded or installed through Amazon. |
| Turkish Console listing | BLOCKED | The current description selector does not offer Turkish. Local metadata and screenshots remain prepared. No alternate locale was overwritten. |

Logs are in `deliverables/android/build-logs`: `verify-0130-final.txt`, `verify-0130-amazon.txt`, `fire-0130/fire-instrumentation.txt`, `capture-0130-tr-TR.txt`, `locales-0130.txt`, `receipt-verifier-0130.txt` and `store-assets-0130.txt`. Screenshot capture is separate from regression tests and only replaces known fictional records in the isolated debug package.

## Language and voice scope

Implemented interface languages: English, French, Spanish, Arabic, German, Italian, Brazilian Portuguese, Dutch, Swedish, Polish and Turkish. Arabic supports RTL. The ten local storefront sets include UK English and all listed non-Arabic languages. Arabic storefront copy/captures, additional regional variants and six requested interface languages remain: Hindi, Japanese, Korean, Simplified Chinese, Traditional Chinese and Indonesian.

Audible Turkish text-to-speech and microphone recognition on a compatible device remain **NOT TESTED**. The app requests microphone access only when used, explains device speech processing and retains typed/touch alternatives. Financial totals are read only after an explicit read-aloud action.

## Privacy and release checks

The change adds no permissions, analytics service, cloud bill upload or billing behavior. The offline release omits Internet permission; the Amazon release includes it for purchase verification. The Amazon public key belongs only in the Amazon package. Secrets and signing material remain outside source control. The verifier environment template is `store/receipt-verifier/.env.example`. Existing financial calculations, reminders, sharing, documents, reports, encrypted backup and data workflows passed the hardware regression suite; this is not a complete security or legal assessment.

The Amazon-installed 0.12.0 uses the existing production-mode receipt verifier. The tester's test entitlement was reset, the Polish paywall showed no verified purchase, and the approved £7.56 test checkout activated Premium automatically. Separate Restore displayed **Sprawdzanie w Amazon...** before returning **Premium jest aktywne.** Device-language selection was restored and app lock remained off. See [the 0.12.0 follow-up](release-0120.md).

The private test retains 12 Fire tablets; API 35 still triggers Amazon's newer-device eligibility warning. Minimum API is 28 and target/compile API is 35. A target-SDK update and newer-device testing require a separate assessment. Amazon's description picker lacks Turkish; the image picker lacks Dutch, Polish and Swedish. Genuine local assets are retained.

The public draft still needs target-audience choices, 13 rating answers, privacy/data-transfer answers, a public privacy-policy URL, final publisher details and final pricing/market/device review. No public submission was attempted. VoiceView review, other devices/orientations and the short-background app-lock check remain **NOT TESTED**. The more-than-two-minute hardware app-lock check passed previously; its original off setting was restored. See [app-lock evidence](app-lock-timeout.md).

## Artifacts and setup

- Debug: `deliverables/android/debug/Billmivo-0.13.0-debug.apk`.
- Instrumentation: `deliverables/android/debug/Billmivo-0.13.0-androidTest.apk`.
- Offline release: `deliverables/android/release/Billmivo-0.13.0-release-unsigned.apk`.
- Amazon release: `deliverables/android/release/Billmivo-0.13.0-amazon-release-unsigned.apk`.
- Preparation archive: `deliverables/android/release/Billmivo-0.13.0-amazon-preparation.zip`.
- Turkish screenshots: eight files in `deliverables/store-assets/amazon/tr-TR`; all 80 scene hashes are in `asset-manifest.json`.

Use JDK 17, Gradle 8.11.1, Android SDK 35 and build-tools 35.0.0. `README.md` documents the architecture and offline build. `store/README.md` documents the Amazon build and verifier configuration. Run `tools/check-locales.ps1`, `tools/test-fire.ps1`, `tools/capture-store-set.ps1 -Locale tr-TR` and `tools/validate-amazon-package.py --raw deliverables/store-assets/amazon-raw` for the corresponding checks. Release APKs are unsigned; keep keys outside Git and use the store's authorized signing process.

The master prompt remains incomplete. Next work is the six remaining interface languages, regional and Arabic store assets, vendor locale limitations, broader accessibility/device testing and current-version Amazon billing before completing and authorizing public release.
