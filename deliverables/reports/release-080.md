# Billmivo 0.8.0 release checkpoint

16 November 2026 · Android package `com.billmivo.app` · version code 9

## Private Amazon testing

Amazon delivered version 0.7.0, version code 8, through the existing one-person private track. The Fire HD 10 (2021, `KFTRWI`, Fire OS 7 / Android 28) reported Amazon Appstore as the installer and SDK sandbox mode disabled. With the previous private entitlement reset, the app showed no verified purchase. A new £7.56 test checkout activated Premium, and a separate restore returned Premium active. The Amazon-installed build is still 0.7.0. [Amazon IAP testing modes](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html)

## German localisation

- Added Deutsch to the app language selector and automatic device-language support. German resources cover bills, calendars, reports, onboarding, reminders, payment and privacy screens. Saved amounts keep their original currencies. German without an explicit region defaults to EUR for new bills.
- Added German forecast-range, payment, bill-draft, dated reminder and navigation commands. Tests cover the comma decimal separator, language-only EUR default, the Friday date calculation and bill draft parsing.
- Prepared German Amazon title, descriptions, keywords, feature bullets and screenshot headlines. Amazon lists German as both a storefront localisation and a Fire tablet language. [Amazon international distribution guide](https://developer.amazon.com/docs/app-submission/distribute-your-app-internationally.html)

## Verification and assets

- Both release variants passed Android lint. All 22 JVM tests, 13 Fire HD 10 instrumentation tests and seven receipt-verifier tests passed. Amazon and offline APKs both contain the German locale. The offline variant has no Internet permission.
- All 381 Android resource keys, placeholders and plural forms matched across the five supported languages. On the Fire HD 10, the German forecast displayed comma decimals, euro amounts, German date formatting, and separate confirmed and estimated totals. Entering “Markiere Miete als bezahlt” opened the German payment review for the correct fictional bill. No payment was saved.
- Captured eight distinct German screens on 0.8.0 and inspected the rendered 1920 × 1200 listing images. The package validator checks the German set alongside the existing English, French and Spanish sets, both icons, the promotional image and the listing text. The other storefront sets retain their earlier Fire captures.
- The local `Billmivo-0.8.0-amazon-preparation.zip` in `deliverables/android/release` contains the unsigned Amazon APK, store media, release copy and the release documentation.

## Open release work

- The 0.8.0 Amazon build needs its own private delivery and billing verification. The public listing remains a draft pending an approved published privacy notice, publisher details, final pricing and distribution, and explicit submission authorisation.
- Wider locales beyond English, French, Spanish, Arabic and German still need translation and in-context review. Shipped language and storefront coverage are listed in [localisation preparation](../../store/localisation.md).
- Microphone testing needs a compatible speech recognizer. Detailed assistive-navigation checks, audible German speech and a configured device-credential challenge still need verification. The Fire HD 10’s existing VoiceView focus smoke test is documented in the [0.7.0 checkpoint](release-070.md).
