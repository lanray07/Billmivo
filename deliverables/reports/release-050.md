# Billmivo 0.5.0 release checkpoint

16 November 2026 · Android package `com.billmivo.app` · version code 6

## Private Amazon Live App Testing

Amazon delivered 0.4.0 (version code 5) through Test 1, **Billmivo 0.4.0 forecast and encrypted backup**. The Fire HD 10 (2021, `KFTRWI`, Fire OS 7 / Android 28) reported Amazon Appstore as the installer, with SDK sandbox mode disabled. The previously held private test entitlement was reset for the intended Fire tester only. The app then showed there was no verified purchase.

The Amazon checkout displayed £7.56 and confirmed completion. Billmivo activated Premium automatically. A separate Restore Amazon purchase action started an Amazon check and returned to active Premium. The production verifier reported configured mode. Amazon says Live App Testing purchases incur no charge. [Amazon IAP testing modes](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html)

Amazon subsequently delivered 0.5.0 (version code 6) through the same private test. The Fire HD 10 verified the Amazon installer and disabled SDK sandbox. With the previous private entitlement reset, backing out of checkout left Premium locked and showed that the purchase was incomplete. A new £7.56 test checkout activated Premium. A separate restore started a fresh Amazon check and returned to active Premium. The Console listed one tester. The [0.4.0 checkpoint](release-040.md) and [Amazon runbook](../../store/README.md) document the setup.

## 0.5.0 changes

- Pause and resume recurring bills without deleting historical payments or earlier balances. The original recurrence dates are retained. A paused date with a recorded payment remains visible and any partial balance remains due.
- Add custom categories, rename them by stable ID, and move related bills and providers to Other when removing a category. The category list includes specific household categories alongside the original broad groups.
- Record bill notes and a default payment method, then add a method and notes to each payment. Payment history keeps each recorded date and amount.
- Review a uniquely named confirmed payment from a typed or spoken command. Payment entry still requires explicit confirmation. The shortcut searches overdue bills and the next 30 days and declines ambiguous matches.
- View 12 months of actual recorded payments by category, provider and household payer, plus separate confirmed and estimated 30-day obligations. Amounts use the selected currency and decimal arithmetic. Chart bars show each group’s share of recorded payments.
- Preserve older Room data with the version 3-to-4 migration. Encrypted backups from 0.4.0 remain restorable. New encrypted backups preserve custom categories, pause history and payment details.

## Verification

| Check | Result |
| --- | --- |
| Android build | Offline debug APK, instrumentation APK and both unsigned release APKs built. Release lint vital passed for the offline and Amazon variants. |
| Automated tests | 20 Kotlin/JVM tests, 11 Fire HD 10 instrumentation tests, and 7 Node receipt-verifier tests passed. |
| Airplane mode | Four Fire tests passed with airplane mode enabled and Wi-Fi disabled. They covered forecast, bill entry, payment, and persisted records. The original radio settings were restored. |
| RTL layout | Visually verified Arabic navigation, the forecast with distinct confirmed and estimated totals, and Custom categories on the Fire HD 10. |
| Typed payment review | The English shortcut found a fictional confirmed overdue water bill and opened its payment form for review. The form included the date, payer, method and notes and required Record payment to write a new transaction. |
| Locale validation | 366 of 366 Android string keys, placeholders and plural forms matched across English, French, Spanish and Arabic. |

## Open acceptance items

- English, French, Spanish and Arabic have matching Android resource keys, placeholders and plurals. The wider regional locale rollout still needs reviewed translations and actual localized captures for any newly approved storefront languages. See the [locale matrix](../../store/localisation.md).
- The Fire HD 10 has no compatible push-to-talk recognizer for this app. Its Ivona engine started an Android speech audio track using English Read aloud on the 0.4.0 store build. Audible intelligibility, other speech languages, microphone denial on a capable device, TalkBack, and device-credential authentication require separate checks.
- The local assistant remains offline. Optional remote inference has no configured endpoint. Named payment review covers the four shipped command languages. Natural-language reminder dates and wider speech dictionaries remain open.
- The [0.6.0 checkpoint](release-060.md) tracks cancellation and later voice work. The Amazon screenshot pack reflects 0.4.0 in English, French and Spanish.
- Public release still requires finalized publisher/privacy details, the approved public privacy notice URL, pricing and distribution decisions, a final device selection check and explicit public submission authorization.

The versioned artifacts are under `deliverables/android/debug` and `deliverables/android/release`. Device evidence and build logs are under `deliverables/android/build-logs`. Build commands and Amazon configuration are in the [Amazon runbook](../../store/README.md).
