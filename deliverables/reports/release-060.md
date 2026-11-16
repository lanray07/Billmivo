# Billmivo 0.6.0 release checkpoint

16 November 2026 · Android package `com.billmivo.app` · version code 7

## Private Amazon testing

Version 0.5.0, version code 6, was delivered by Amazon Appstore to the Fire HD 10 (2021, `KFTRWI`, Fire OS 7 / Android 28). The existing private track retained one tester. With the old private test entitlement reset, cancelling the first checkout kept Premium locked. A fresh £7.56 checkout completed, automatically activated Premium and returned to the Premium screen. A separate restore started a fresh Amazon check and returned to active Premium. Amazon documents that Live App Testing purchases incur no charge. [Amazon IAP testing modes](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html)

Amazon later delivered 0.6.0, version code 7, through the same private track. After resetting the single tester’s old entitlement, Billmivo showed no verified purchase. A fresh £7.56 checkout activated Premium. A separate Restore Amazon purchase check also returned Premium active. The public listing stayed a draft.

## App changes

- Cancel a single unpaid bill occurrence. A cancelled bill remains visible in the calendar and monthly report, has no remaining balance, and leaves forecasts and reminders. Future occurrences of the same bill stay scheduled. Partial and completed payments are protected.
- Room migration 4 to 5 preserves existing adjustments. Encrypted backups retain the cancelled status, and readers accept the preceding schemas.
- Ask to read upcoming payments in English, French, Spanish or Arabic. The preview lists up to ten upcoming payments in the selected currency with their date, amount and confirmation status. Reading aloud requires a separate action and an enabled speech engine. The preview links to the 30-day upcoming calendar.

## Verification

- Built the offline debug and test APKs and both unsigned release variants. Release lint passed for both variants.
- Passed 20 JVM tests, 12 Fire HD 10 instrumentation tests and seven Node receipt-verifier tests. Cancellation tests protect partially paid balances, exclude cancelled occurrences from reminders, and preserve the separate skipped status.
- On the Fire HD 10, cancelling a fictional €29.95 bill left it visible in the cancelled calendar and reduced the 30-day forecast from €1,209.95 to €1,180.00. The typed upcoming-payment shortcut showed separate confirmed and estimated items, opened the Upcoming calendar, and started an Android speech audio track after the Read aloud button was pressed.
- All 370 Android resource keys, placeholders and plurals matched in the four shipped languages. The translation tool extracted the new command phrases for review.

## Work still open

- The [0.7.0 checkpoint](release-070.md) tracks later changes and remaining release work.
- The app ships English, French, Spanish and Arabic. Wider locales need translations and in-context review. The existing Amazon screenshot set covers English, French and Spanish for 0.4.0. See the [locale matrix](../../store/localisation.md).
- This Fire HD 10 has no compatible push-to-talk recognizer. Other hardware is needed for microphone-permission and speech input checks. VoiceView or TalkBack and the configured device-credential flow still need dedicated verification.
- Public release needs final publisher/privacy details and an approved published privacy notice, final pricing and marketplace decisions, and explicit public submission authorization.
