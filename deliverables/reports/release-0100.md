# Billmivo 0.10.0 release checkpoint

21 November 2026 · Android version code 11

## Italian localisation

Italian covers the interface, guided setup, notifications, reports, privacy controls, backup messages and purchase states. The device language selects Italian automatically, and Settings offers a manual override. New bills default to EUR for a language-only Italian locale. Existing bill currencies and user-entered names keep their saved values.

Italian shortcuts support seven-, thirty- and ninety-day forecasts, navigation, bill drafts, payment review and dated reminders. Decimal input uses the comma separator. Drafts and payment commands open a review before saving. Speech capture and spoken output depend on the installed device services.

The Italian Amazon listing includes a title, descriptions, feature bullets, search phrases, support copy and eight screenshot headlines. Amazon lists Italian among its supported storefront languages. [Amazon localisation guide](https://developer.amazon.com/docs/app-submission/distribute-your-app-internationally.html)

## Verification

- All 383 string keys, placeholders and plural groups match across the six interface languages. The 24 JVM tests include Italian decimal input, default currency, forecast ranges, navigation, payment review, weekday reminders and bill drafts.
- All 14 instrumentation tests passed on the Fire HD 10. Both offline and Amazon release variants built successfully.
- GitHub Actions also passed the offline build, JVM tests, Amazon SDK compile, localisation validation and receipt-verifier tests for source commit `a11de0c`.
- Captured and inspected eight Italian screens on the Fire HD 10, including forecasts, reminders, spending, amount changes, voice fallback, household shares and the calendar. The asset validator checked all five storefront sets, including distinct raw captures and image dimensions.
- Entering “Segna Affitto come pagato” selected the fictional rent bill and opened its Italian payment review. The review was cancelled.
- With the user entering the PIN directly into Fire OS, the Amazon-installed 0.9.0 enabled app lock, showed the locked page after a process restart, and reopened after successful credential confirmation. Its window had the secure flag enabled. The user then authenticated to restore app lock to its original off setting. The separate two-minute background timeout still needs verification.

## Amazon listing draft

Saved complete descriptions for English (U.S.), English (U.K.), French, Spanish, German and Italian in Amazon App Setup. Their image groups include two icons, eight screenshots and the promotional image. The default U.S. group uses the existing UK English screenshots. German, French, Spanish and Italian groups use their respective translated app captures.

The six implemented interface languages are selected in the draft. Version 0.10.0 is available as a local Amazon APK and preparation archive.

## Private purchase and restore

Updated the existing one-tester private track to 0.10.0, version code 11, with Italian enabled and the copied export certification. The Fire HD 10 installed the update directly from Amazon, reported `com.amazon.venezia` as installer and used SDK sandbox mode `none`.

After resetting the existing tester’s previous entitlement, the Italian paywall reported no verified purchase. A fresh checkout at the displayed £7.56 test price activated Premium automatically. **Ripristina acquisto Amazon** started a separate Amazon check and returned **Premium è attivo**. The original device-language setting was restored after the test. Amazon documents that Live App Testing purchases are not charged. [Amazon IAP testing overview](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html)

## Remaining checks

The Fire exposes no Android speech recognizer. Microphone input needs compatible hardware. Detailed VoiceView navigation, the app-lock background timeout and additional requested languages remain open. The screenshot script captured the first Italian scenes, then encountered intermittent empty accessibility trees on Fire OS. The remaining scenes were captured and inspected manually.

## Release scope

Version 0.10.0 passed the existing private Amazon purchase and restore workflow on the Fire HD 10.

The app language set now includes English, French, Spanish, Arabic, German and Italian. Further requested languages need translation, device review and their storefront asset sets. The public Amazon listing also needs the final publisher details, privacy notice URL, pricing, market and device choices, and submission authorisation.
