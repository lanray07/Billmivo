# Billmivo dashboard delivery

15 November 2026 · Android 0.1.0

## Verified

| Check | Status | Evidence |
| --- | --- | --- |
| Android packaging | PASS | Debug, Android test and unsigned release APKs assembled. Release lint vital completed. |
| Forecast and input logic | PASS | 9 JVM tests. Coverage includes inclusive forecast periods, month-end and leap-year recurrence, currency separation, partial payments, comparable spending days and localized input parsing. |
| Fire tablet workflows | PASS | 3 instrumentation tests on Fire HD 10 (2021, KFTRWI), Fire OS 7, API 28, build PS7331.4463N. Verified the bill entry and payment journey, forecast controls, and transactional updates to paid balances and schedule history. |
| Landscape dashboard | PASS | 1920 × 1200 device captures reviewed with empty and fictional household records. |
| Localization resource validation | PASS | English, French, Spanish and Arabic each have 135 of 135 strings. Format parameters and plural categories passed automated checks. |
| Arabic tablet layout | PASS | Switched the app to Arabic on Fire OS 7, reviewed the mirrored navigation, chart, date range and numbers, then restored the tablet's device-language setting in Billmivo. |
| Forecast voice touch fallback | PASS | The Fire tablet displayed the unavailable-recognition state and applied a 90-day forecast by touch. |
| On-device speech recognition | BLOCKED | The tested Fire tablet exposes no Android speech-recognition service. |
| Read-aloud output | NOT TESTED | The tablet exposes IVONA and Pico TTS services. Audible output and language coverage remain unverified. |
| Other tablet and phone configurations | NOT TESTED | Landscape was verified on the connected Fire HD 10. Portrait, Fire OS 8, and physical Android phones remain unverified. |
| Amazon submission | BLOCKED | The release APK still needs production signing, store assets and listing metadata, and the remaining capabilities and release checks in the brief. |

## Dashboard review

- **Forecast hierarchy.** The default 30-day range, exact inclusive dates, open bill count, and balance are visible at the top. The 7-day, 90-day, and custom controls are next to the active range.
- **Amount certainty.** Confirmed and estimated labels accompany their respective totals and bill rows. The balance strip uses proportional segments for the two subtotals. The fictional example displays £1,011.95 confirmed and £198.00 estimated, totalling £1,209.95.
- **Urgency.** The £37.50 overdue water bill is visible separately below household spending and stays out of the upcoming balance. The upcoming list uses due-date order.
- **Spending.** The chart sits next to the forecast at landscape tablet widths. The current month is marked as month to date. In the fictional fixture, November payments total £883.80 and the same-day comparison is £17.30 higher than October. The source is recorded payments grouped by payment date.
- **Accessibility and empty state.** Status labels accompany colour. Bars expose month, amount and selection through semantics. Historical bars use a higher-contrast shade. The empty dashboard provides a single prominent first-bill action. Full assistive-technology verification remains open.

## Build and files

Build used JDK 17, Gradle 8.11.1, Android SDK 35 and build-tools 35.0.0.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleRelease
.\tools\check-locales.ps1
```

The workstation build used the matching Gradle binary with its cache and Android SDK on D: because C: ran out of space. The project's wrapper configuration selects the same version. The debug APK is signed with a debug certificate. The release APK is unsigned. The APKs and the Gradle, JVM, locale and Fire logs are under `deliverables/android`.

The Fire device captures are raw en-GB app screenshots. The populated view uses the separate debug fixture and fictional bills. The fixture is available only in the Android test package. Production installations begin empty.

## Privacy and release

Bill and payment data lives in app-private Room storage. Automatic backup is disabled. The manifest requests microphone access for the optional voice workflow and declares no Internet permission. Speech processing is handled by the device service when supported. Users control spoken playback and confirm permanent deletion. There are no app service credentials or external account requirements for the dashboard.

Remaining scope includes reminders, household allocations, provider and document management, reporting and backup, an expanded assistant, Amazon purchasing and entitlement tests, the rest of the launch locale matrix, accessibility and device coverage, and the complete store package. Fire tablet distribution remains available through Amazon. Amazon discontinued its Appstore for general Android mobile devices on 20 August 2025. [Amazon distribution announcement](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program)
