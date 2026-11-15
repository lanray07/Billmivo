# Billmivo

Billmivo is a native Android household bill tracker. The initial build centres on the Bill Forecast Dashboard. It uses local Room records and starts with an empty household.

## Build

Use JDK 17, Android SDK platform 35 and build-tools 35.0.0. Set `ANDROID_HOME` to your SDK directory or let Android Studio create `local.properties`. No environment secrets are required.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest
.\tools\check-locales.ps1
```

The debug package is `com.billmivo.app.debug`. The minimum Android API is 28. Release packages use `com.billmivo.app` and require signing before installation or submission. Keep signing credentials outside source control.

## Forecast behaviour

- The 7-, 30- and 90-day windows include today. Custom ranges include both endpoints. Overdue balances appear in a separate section.
- The total includes unpaid balances. Confirmed and estimated subtotals use exact decimal arithmetic, and totals are separate for each currency.
- Monthly schedules stay anchored to the original day. The engine uses the last valid day in shorter months. The same rule applies for quarterly and yearly schedules.
- Variable bills use the expected amount you enter. Confirming an occurrence updates only that due date. Recording a partial payment leaves the balance in the forecast. Skipped and fully paid occurrences drop out of the forecast.
- Edits create a new schedule version after protected occurrences. Payment records retain their original amounts and dates. Confirm deletion to remove a bill and its history.
- The spending chart uses actual recorded payment dates. Its headline compares matching days of the current and previous month. The chart shows the current month to date and five preceding calendar months.
- Bill amount changes compare confirmed occurrences of the same schedule and frequency. The display gives the amounts and dates without assigning a cause.

## Architecture

Jetpack Compose renders the responsive dashboard, bills, calendar, insights and settings. `BillsModel` observes Room invalidations and applies transactional changes. `Forecast.kt` contains date and money calculations with no Android dependency. `BillDatabase.kt` contains the Room schema and repository. Local database and preferences are kept in app-private storage. Android backup is disabled and the app declares no Internet permission. Speech is optional, uses the installed device speech service, and opens the microphone only when you request it. Spoken financial totals require an explicit read-aloud action.

## Languages and voice

The dashboard build includes English, French, Spanish and Arabic. The UI follows the device language by default and offers a manual override. Arabic uses right-to-left layout. Currency and date formatting follow the active locale. The forecast shortcut understands 7, 30 and 90 as digits or supported local number words, with touch and typed controls as alternatives. Microphone recognition and speech output depend on the installed engine and locale.

## Release scope

Remaining release work includes notification reminders, household allocations, provider and receipt management, exports, remote AI, Amazon purchasing, the rest of the launch locale matrix and complete store submissions. See [the dashboard delivery report](deliverables/reports/dashboard-delivery.md) for verified hardware, build results and limitations.
