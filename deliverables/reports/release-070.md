# Billmivo 0.7.0 release checkpoint

16 November 2026 · Android package `com.billmivo.app` · version code 8

## Private Amazon testing

Amazon Appstore delivered 0.6.0, version code 7, to the Fire HD 10 (2021, `KFTRWI`, Fire OS 7 / Android 28). The private track retained one tester. The app showed no verified purchase after the prior test entitlement was reset. A fresh £7.56 test checkout activated Premium, and a separate Restore Amazon purchase check returned to active Premium. Live App Testing purchases incur no charge. [Amazon IAP testing modes](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html)

## Dated reminders

- Open an unpaid bill occurrence and choose One-off reminder to set a date. The reminder uses the saved notification hour and respects quiet hours. It requires bill reminders and system notifications to be enabled.
- Enter or speak a command such as “remind me to pay electricity on Friday”. The four shipped command languages support a named bill and an upcoming weekday or ISO date. The app shows the matching bill, due date and proposed reminder date for review before saving.
- A new date replaces the one-off reminder for that occurrence. One-off reminders can be removed in Reminders. Paying the balance, skipping or cancelling the occurrence, or archiving the bill removes its reminder. Revised schedules carry the reminder forward when the same due date still exists.
- The local database migrates from schema 5 to 6. Encrypted backups include dated reminders and the reader continues to support the previous archive schemas.

## Verification

- Offline and Amazon release builds passed lint. All 21 JVM tests, 13 Fire HD 10 instrumentation tests and seven Node receipt-verifier tests passed. One-off reminder coverage includes Android notification delivery without a recurring rule, privacy previews, snooze, automatic removal after payment, and encrypted backup round-trip.
- On the Fire HD 10, a typed Friday command matched a fictional bill due Saturday, 21 November. After confirmation, the Reminders screen listed Friday, 20 November. The test reminder was removed afterward.
- Amazon VoiceView highlighted the voice-shortcut control during a Fire HD 10 dashboard navigation smoke test. The tablet’s original accessibility settings were restored. [Amazon VoiceView guide](https://digprjsurvey.amazon.co.uk/csad/help/node/201829330)
- All 380 Android resource keys, placeholders and plurals matched across English, French, Spanish and Arabic. Translation extraction includes the new command and its bill-name and date placeholders.

## Remaining work

- The prepared 0.7.0 Amazon binary still needs a private Appstore delivery and checkout test. The authorized private test was 0.6.0.
- Wider interface locales and storefront screenshots require translation and in-context review. The app currently ships English, French, Spanish and Arabic and has an English, French and Spanish store asset pack. See the [locale matrix](../../store/localisation.md).
- This Fire HD 10 uses typed commands and can start playback through the OS speech output engine. Actual microphone input needs a device with a compatible speech recognizer. Full screen-reader traversal and activation, audible intelligibility, and a configured device-credential test remain open.
- Public release needs the approved privacy notice URL, finalized publisher details, pricing, distribution and explicit public submission authorization.
