# Billmivo 0.4.0 master-prompt checkpoint

16 November 2026 · Android package `com.billmivo.app` · native Android and Amazon Fire tablet scope

| Area | Status | Evidence |
| --- | --- | --- |
| Offline build | PASS | JDK 17, Gradle 8.11.1, Android SDK 35. Debug, test and unsigned release APKs assembled. Release lint vital passed. |
| Forecast and money | PASS | JVM tests cover recurrence boundaries, exact amounts and allocation rounding. Fire tests cover changing forecast range, displaying estimates separately and recording payment. |
| Bills, reminders, providers, documents, reports and local assistant | PASS | Fire HD 10 instrumentation executed payment, reminders and snooze, migration, household sharing, provider contact, attachment, CSV and readable-PDF paths. The assistant opened a reviewable draft. |
| Encrypted backup, restore and deletion | PASS | On API 28, a backup round-tripped a bill, partial payment and document. A wrong passphrase and a changed authentication tag left current records intact. Erase removed records and the isolated attachment. |
| Interface localisation | PASS | 333 of 333 Android strings, placeholders and plural keys in English, French, Spanish and Arabic. Source extraction for an additional locale produced 370 review entries. |
| Automated test totals | PASS | 18 Kotlin JVM tests, 10 Fire HD 10 instrumentation tests, and 7 Node receipt verifier tests. |
| Live App Testing purchase and restore for the prior build | PASS | Private 0.3.0 purchase and 0.3.1 production restore passed on the same Fire HD 10. Version 0.3.1 also passed a 75-second sandbox checkout. See the [Amazon runbook](../../store/README.md). |
| New locale coverage beyond English, French, Spanish and Arabic | BLOCKED | Production expansion needs a translation provider configuration and native-language verification. The development-time pipeline does not use private bill data. |
| Voice input, audible speech, and lock authentication matrix | NOT TESTED | Parser shortcuts were exercised in all four languages. Push-to-talk, audible TTS by locale, TalkBack and the device-credential challenge require compatible hardware or a configured device. |
| Public Amazon release | BLOCKED | Public publisher privacy details, a published privacy notice URL, final production distribution and pricing decisions, and explicit authorisation are still required. |

The Fire HD 10 tested was model `KFTRWI`, Fire OS 7, Android API 28. The offline build uses Room, app-private attachments, opt-in reminders, deterministic forecasts, and a local assistant. The paid Amazon edition uses Amazon Appstore SDK and sends Amazon’s app-specific user ID and receipt ID to the HTTPS verifier. Optional remote inference is absent. Production receipt verification does not send bills, household records, audio, or documents.

This checkpoint adds guided setup and preferred currency, calendar views and filters, voice and typed shortcuts, dashboard planning panels, and privacy controls. The remaining product work includes editing user-defined categories, richer bill and payment notes and method labels, additional recurrence lifecycle controls, expanded speech intents and device QA, broader visual and insight coverage, and completing the worldwide storefront rollout. Full master-prompt release readiness has not passed.

## Reproduce

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
.\gradlew.bat :app:assembleRelease -PbillmivoAmazon=true -PbillmivoVerifierUrl=https://billmivo-receipts-production.lanraybanks.workers.dev/verify-amazon
.\tools\check-locales.ps1
node --test 'store/receipt-verifier/*.test.mjs'
```

Use the matching debug and test APKs with Android instrumentation. Logs are under `deliverables/android/build-logs`. Version 0.4.0 uses the existing Room schema and preserves the migration paths from versions 1 and 2.
