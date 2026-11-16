# Billmivo 0.9.0 release checkpoint

16 November 2026 · Android version code 10

## Private Amazon testing

Amazon accepted version code 10 into the existing single-tester Live App Testing track. The Fire HD 10 (2021, `KFTRWI`, Fire OS 7 / Android API 28) installed 0.9.0 directly from the Amazon Appstore with SDK sandbox mode set to `none`. After resetting the previous private entitlement, Billmivo reported that the account had no verified purchase. A fresh checkout at the displayed £7.56 test price activated Premium automatically. **Restore Amazon purchase** started a separate Amazon check and returned to **Premium is active**. Amazon documents that Live App Testing purchases carry no charge. [Amazon IAP testing modes](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html)

## Forecast and accessibility

- The selected 7-, 30- or 90-day forecast now includes dated one-off reminders and recurring alerts by the day they are scheduled to notify. That includes reminders for overdue bills and alerts that arrive before the end of the forecast for bills due after it. Cancelled and settled occurrences, paused reminders and bills in other currencies stay out of the selected forecast.
- Upcoming bills, planned reminders and spending chart bars expose their action as a button to assistive technology. Screen sections expose heading semantics, and Settings distinguishes the device theme from the device language in all five supported locales.
- VoiceView highlighted the dashboard’s Voice control on the Amazon-installed 0.9.0 build. The Fire’s accessibility settings were restored after the check. Gesture navigation and speech output still need direct verification. [Amazon VoiceView guide](https://digprjsurvey.amazon.co.uk/csad/help/node/201829330)

## Verification

- The offline and Amazon release APKs built successfully and report version code 10. The offline APK has no Internet permission.
- All 23 JVM tests, 14 instrumentation tests on the Fire HD 10 and seven receipt-verifier tests passed. The Fire suite checks that an overdue bill’s one-off reminder shows its notification date and opens the original occurrence. The test runner includes the whole regression suite and excludes the store screenshot fixture.
- All 382 Android string keys, placeholders and plural forms matched across English, French, Spanish, Arabic and German.
- On the Fire HD 10, selecting **Enable app lock** opened Fire OS's existing PIN challenge. Cancelling returned to Privacy and backup with app lock still off. With speech recognition unavailable on this tablet, the touch shortcut displayed the selected seven-day forecast.
- On the Amazon-installed 0.9.0 build, **Read aloud** connected to the Fire’s IVONA text-to-speech service and produced an active audio track owned by that service. Audio was verified at the Android playback level. This tablet exposes no Android speech recognition service.
- The Amazon package validator checked eight existing 1920 × 1200 captures per storefront locale, their listing copy, the two icons and the promotional image. The local `Billmivo-0.9.0-amazon-preparation.zip` bundles the tested APK, store assets and release documentation.

## Outstanding release checks

- Verify successful PIN authentication and subsequent relocking, push-to-talk on supported hardware and detailed assistive navigation on Fire. The isolated Android emulator did not register with ADB in software mode on this host.
- The app supports English, French, Spanish, Arabic and German. Eight listing screenshots exist for each of the four Amazon storefront locales prepared so far. Additional languages still need translation and in-context review. See [localisation preparation](../../store/localisation.md).
- Public Amazon submission requires the final publisher details, published privacy notice, pricing, device eligibility and market selection. Public submission is a separate release step.
