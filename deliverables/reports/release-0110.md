# Billmivo 0.11.0 release checkpoint

21 November 2026 · Android version code 12

## Language expansion

Brazilian Portuguese, Dutch and Swedish now cover all 386 interface strings and both plural groups. This includes onboarding, reminders, household sharing, reports, backup, privacy controls and purchase states. Settings offers the three new languages, and matching device languages select them automatically. The Portuguese selector requests `pt-BR`. Other Portuguese regions currently share the Brazilian wording.

The command parser supports localized forecast ranges, navigation, payment review, dated reminders and bill drafts. New language-only bills default to BRL, EUR and SEK respectively. Saved bill currencies remain explicit. The three new JVM cases check currency defaults, number words, comma decimals, Friday reminders, navigation, payment review and drafts.

Store metadata includes titles, descriptions, feature bullets, search phrases, support copy, release notes and eight translated screenshot headlines for `pt-BR`, `nl-NL` and `sv-SE`.

## Verification

| Check | Status | Evidence |
| --- | --- | --- |
| Resource parity | PASS | `tools/check-locales.ps1` reports 386/386 strings with matching placeholders and plural groups in all nine interface languages. |
| JVM tests | PASS | 27 tests passed, including the three new locale command cases. |
| Offline builds | PASS | Debug, instrumentation and unsigned release APKs assembled. Release lint vital completed. |
| Amazon release build | PASS | Production-mode SDK variant assembled with the configured verifier URL. APK reports version 0.11.0, code 12, minimum API 28 and target API 35. |
| Fire regression reruns | PASS | Two complete reruns each passed all 14 tests on the Fire HD 10 (2021), KFTRWI, Fire OS 7, API 28, build PS7331.4463N, landscape 1920 × 1200. The final run used the final debug and instrumentation APKs. |
| Initial reminder checks | FAIL | The initial full run had two eight-second timeouts waiting for a notification to disappear after its action. Both reminder tests then passed in isolation, and the complete suite passed on rerun. The initial intermittent failure remains recorded in `fire-0110/fire-instrumentation.txt`; its cause has not been established. |
| Receipt verifier | PASS | All seven local Node tests passed for validation, production and sandbox routing, malformed receipts, throttling and missing credentials. |
| New localized captures | PASS | Three successful Fire capture runs produced 24 distinct screenshots. Reviewed translated UI, BRL/EUR/SEK formats, headlines, dialog rendering and visible household totals. The package validator passed all eight storefront sets, 64 compositions in total. |
| Purchase and restore on 0.11.0 | NOT TESTED | The new build has not been uploaded to Amazon Live App Testing. The Amazon-installed 0.10.0 previously passed fresh purchase and separate restore on this tablet. |

Logs are under `deliverables/android/build-logs`. The successful Fire suites are `fire-0110-repeat/fire-instrumentation.txt` and `fire-0110-final/fire-instrumentation.txt`; the separate reminder rerun is `fire-0110/reminder-repeat.txt`.

The first GitHub run passed the offline and Amazon builds but exposed a locale-checker assumption after the final selector tag changed to `pt-BR`. The checker expected an exact resource directory. It now resolves Android region tags through matching BCP 47, regional or parent-language directories, and local validation passes for `pt-BR` using the shared `values-pt` resources. The earlier CI failure remains visible in the workflow history.

## Capture automation

`StoreScreenshotCapture` seeds only the separate debug package with fictional records, then navigates the translated application through Compose semantics. Android captures the actual Fire display. The fixture requires an explicit `demo=true` argument and refuses a household containing non-preview records. The normal Fire regression command excludes both preview fixtures.

`tools/capture-store-set.ps1` retrieves eight scenes and composes the localized store assets. It checks successful instrumentation and rejects duplicate raw scenes. Captures preserve a visible distinction between confirmed and estimated amounts, overdue bills, recorded spending and planned household shares. The fixture restores the original app locale when it finishes.

## Voice compatibility

| Capability | Status | Scope |
| --- | --- | --- |
| New locale command parsing | PASS | Verified by JVM tests for Portuguese, Dutch and Swedish. |
| Fire voice dialog and touch alternative | PASS | The three locale capture runs opened the translated unavailable-recognition dialog and changed the forecast to seven days through the touch controls. |
| Microphone recognition on this Fire | BLOCKED | The tablet exposes no Android speech-recognition service. A compatible recognizer or another supported device is required. |
| Audible output in the three new languages | NOT TESTED | Installed IVONA/Pico services do not establish verified language or audible-output support. |

## Privacy and security checks

The offline release has no Internet permission. The Amazon release adds Internet access for receipt verification. Both request microphone access for the optional voice action, notification permission where applicable, and boot completion for reminder scheduling. No new permissions or external services were introduced by this change.

The deployed receipt verifier's health endpoint returned `status=configured` and `mode=production`. This confirms its configuration is present; it does not replace a current-version Amazon purchase test.

The Fire backup test verified encrypted export, rejection of incorrect passwords and tampered data, restore of records and attachments, legacy metadata migration and local erasure. These are functional checks rather than a full security assessment. Production signing credentials and the receipt-verifier shared secret remain outside version control. The verifier environment template is `store/receipt-verifier/.env.example`.

## Artifacts and setup

- Source is `D:/App Projects/Billmivo`, using Jetpack Compose, local Room storage and the existing Amazon SDK variant.
- Debug APK is `deliverables/android/debug/Billmivo-0.11.0-debug.apk`.
- Instrumentation APK is `deliverables/android/debug/Billmivo-0.11.0-androidTest.apk`.
- Offline release is `deliverables/android/release/Billmivo-0.11.0-release-unsigned.apk`.
- Amazon release is `deliverables/android/release/Billmivo-0.11.0-amazon-release-unsigned.apk`.
- The preparation archive is `deliverables/android/release/Billmivo-0.11.0-amazon-preparation.zip`. It contains the Amazon APK, all eight storefront asset sets, metadata and this checkpoint.
- Build with JDK 17, Gradle 8.11.1, Android SDK 35 and build-tools 35.0.0. The local build used offline dependency caches and the SDK's matching aapt2 executable.

Standard build and validation commands are documented in the root README and `store/README.md`. No new infrastructure configuration is required for these locales. Amazon purchasing retains the existing one-time `billmivo_premium` entitlement and server-side verification.

## Remaining master prompt work

| Requirement | Status | Next work |
| --- | --- | --- |
| Additional priority interface languages | NOT TESTED | Polish, Hindi, Japanese, Korean, Simplified Chinese, Traditional Chinese, Turkish and Indonesian still need implementation and device review. |
| Additional regional wording and storefronts | NOT TESTED | Prepare the remaining requested regional variants and their actual localized app captures. |
| App-lock background timeout | NOT TESTED | Cold-start locking was verified previously. The two-minute background check requires the owner to enter the existing PIN directly. The pending credential prompt was cancelled; no timeout pass is claimed. |
| Detailed assistive-technology review | NOT TESTED | Complete VoiceView navigation and focus review in the supported locales. |
| Other devices and orientations | NOT TESTED | Broaden beyond the verified Fire HD 10 landscape configuration, including newer Fire OS versions. |
| Current-version private billing | NOT TESTED | Upload 0.11.0 to the existing private track, install through Amazon, then test purchase and restore. |
| Public release | BLOCKED | Final publisher details, public privacy URL, pricing, markets, eligible devices and authorization for public submission remain required. |

The offline assistant remains a local rule-based organization and draft-review feature. It does not use a cloud language model. This release introduces no new assistant behavior or analytics service.

The master prompt is not complete, and this checkpoint does not certify readiness for public submission.
