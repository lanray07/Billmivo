# Billmivo 0.9.0 release checkpoint

16 November 2026 · Android version code 10

## Forecast and accessibility

- The selected 7-, 30- or 90-day forecast now includes dated one-off reminders and recurring alerts by the day they are scheduled to notify. That includes reminders for overdue bills and alerts that arrive before the end of the forecast for bills due after it. Cancelled and settled occurrences, paused reminders and bills in other currencies stay out of the selected forecast.
- Upcoming bills, planned reminders and spending chart bars expose their action as a button to assistive technology. Screen sections expose heading semantics, and Settings distinguishes the device theme from the device language in all five supported locales.
- The existing [0.8.0 private test](release-080.md) verified Amazon delivery, a fresh purchase and a separate restore in German on the Fire HD 10. Version 0.9.0 is a local candidate.

## Verification

- The offline and Amazon release APKs built successfully and report version code 10. The offline APK has no Internet permission.
- All 23 JVM tests, 13 instrumentation tests on the Fire HD 10 and seven receipt-verifier tests passed. The forecast tests cover reminder date boundaries and checking the selected bill through its accessible action.
- All 382 Android string keys, placeholders and plural forms matched across English, French, Spanish, Arabic and German.
- On the Fire HD 10, selecting **Enable app lock** opened Fire OS's existing PIN challenge. Cancelling returned to Privacy and backup with app lock still off. With speech recognition unavailable on this tablet, the touch shortcut displayed the selected seven-day forecast.

## Outstanding release checks

- Verify successful PIN authentication and subsequent relocking, push-to-talk on supported hardware and detailed assistive navigation on Fire. The isolated Android emulator did not register with ADB in software mode on this host.
- The app supports English, French, Spanish, Arabic and German. Eight listing screenshots exist for each of the four Amazon storefront locales prepared so far. Additional languages still need translation and in-context review. See [localisation preparation](../../store/localisation.md).
- Public Amazon submission requires the final publisher details, published privacy notice, pricing, device eligibility and market selection. Public submission is a separate release step.
