# Billmivo 0.3.0 release preparation

15 November 2026 · Fire tablet · Android package `com.billmivo.app`

## Verified

| Area | Status | Evidence |
| --- | --- | --- |
| Offline build | PASS | JDK 17, Gradle 8.11.1, Android SDK 35. Debug, Android test and unsigned release APKs assembled. Release lint vital completed. |
| Kotlin tests | PASS | 16 JVM tests for forecast boundaries, exact money, split rounding, reminder policy and draft parsing. |
| Fire tablet | PASS | Nine tests on Fire HD 10 (2021, KFTRWI, Fire OS 7, Android API 28). The full group had seven passing tests, followed by separate passing bill-entry and assistant tests. |
| Reminders | PASS | Android delivered a private notification while the activity was closed. Snooze dismissed it and resumed on a controlled clock. Recording the payment cancelled it. |
| Household sharing | PASS | Equal-share flow and penny reconciliation passed in Compose. Migration retained version-one bills and payments. Assigning responsibility and recording an actual payer remain distinct. |
| Providers, attachments and reports | PASS | Linked a provider and contact note, copied an attachment from a content URI, verified private storage and the viewer handoff, generated a PDF readable by Android PDF Renderer, and verified CSV formula escaping. Deleting a provider retained payments. Deleting the bill removed its document copy. |
| Local assistant | PASS | A localized typed draft produced a bill preview for review. The initial action opened a separate review path. Existing bill entry still requires a save. |
| Amazon SDK build | PASS | Debug variant built with Amazon SDK 3.0.9 and sandbox configuration. The SDK emitted D8 stack-map warnings during compilation. |
| Amazon receipt verifier | PASS | Four local Node tests covered expected entitlements, wrong products, cancellations, throttling and production/sandbox separation. |
| Interface resources | PASS | English, French, Spanish and Arabic each have 285 of 285 strings. Both plural groups and format arguments passed validation. |
| Store assets | PASS | Eight distinct, current 1920 × 1200 Fire HD 10 scenes for each of en-GB, fr-FR and es-ES. The 114 × 114 and 512 × 512 icons passed transparency and dimension checks. |
| Paid purchase, restore and refund validation | BLOCKED | The Appstore public key, configured entitlement SKU, authenticated Fire testing and hosted HTTPS receipt verifier are still needed. |
| Device and speech matrix | NOT TESTED | Additional Fire models, Fire OS 8, phones, notification permission prompts on API 33+, TalkBack and language-specific audible output require further hardware QA. The tested Fire OS 7 showed the touch fallback for speech recognition. |

Device logs and Gradle logs are local under `deliverables/android/build-logs`. Reproduce the Fire tests with `tools/test-fire.ps1` using the offline debug and matching Android test APK. The test fixture uses fictional bills. No real purchases or external financial records were used.

## APKs and store pack

- `deliverables/android/debug/Billmivo-0.3.0-debug.apk`
- `deliverables/android/release/Billmivo-0.3.0-release-unsigned.apk`
- `deliverables/android/release/Billmivo-0.3.0-amazon-preparation.zip` for the offline APK and listing preparation files
- `deliverables/store-assets/amazon` for the icon sizes, screenshot sets and image manifest
- `store/listings.json` for listing metadata and `store/privacy-draft.md` for publisher review

The offline release binary uses Room with version-one and version-two migrations, app-private attachment copies and opt-in notifications. It declares no Internet permission. The separate Amazon build adds Internet access for receipt verification, and the Appstore receives no household bill or document data from it. The report exporter opens a user-controlled save or share destination after confirmation.

## Release work that remains

The paid release needs an authorized Developer Console draft and entitlement, public Appstore authentication PEM, separate sandbox and production verifier endpoints, Amazon purchase and restore tests, approved publisher privacy and support details, and submission. See [the Amazon runbook](../../store/README.md).

The wider interface and storefront rollout is still in progress. The translation pipeline is ready for credentials and language review. Broader voice commands, optional remote AI, local app lock, encrypted backup and additional device and accessibility checks remain product work.
