# Reminders and household sharing

Version 0.2.0 was tested on 15 November 2026 on the connected Fire HD 10 (KFTRWI, Fire OS 7, Android API 28).

| Check | Result |
| --- | --- |
| Android packaging | Debug, instrumented test and unsigned release APKs assembled. Release lint vital completed. |
| JVM | 14 tests passed, including cent allocation, percentages, weights, fixed amounts, due-state suppression and quiet hours. |
| Reminders on hardware | A real Android alarm delivered a private notification while the activity was closed. The notification action cleared it for snooze. A clock-controlled check verified redisplay after an hour and cancellation after payment. |
| Household UI on hardware | Selected two members, assigned responsibility, saved an equal split and saw £18.01 and £18.00 from a £36.01 bill. |
| Upgrade and payer attribution | Migrated a version-one SQLite fixture, retained its payment with an unknown payer, and recorded a new partial payment by a selected member without assigning that payment to the responsible member. |
| Regression tests | Dashboard and repository tests passed in the device suite. The existing bill-entry test passed when rerun alone after losing its Compose hierarchy during the combined run. |
| Localisation | English, French, Spanish and Arabic passed structural checks for all 193 strings, placeholders and plural forms. |

Notifications are opt-in. Delivery is inexact and may be affected by device settings and battery optimisation. Rules include a configurable advance window, the due date, one day overdue, and confirmed recurring price increases. Boot, app replacement, and clock or timezone changes trigger a refresh. Reboot delivery, Android 13 notification prompts, and an hour-long wall-clock snooze were outside the hardware test.

Sharing stays on one device. Membership and split rules are recorded in Room. Changes apply to bills due from the effective date forward. Fixed shares requiring adjustment after a variable bill changes are flagged for review. Amounts reconcile by currency, including rounding in currencies with zero or three fractional digits.
