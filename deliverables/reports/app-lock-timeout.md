# Fire HD 10 app-lock timeout check

21 November 2026

## Verified configuration

Amazon-installed Billmivo 0.10.0, package `com.billmivo.app`, on the Fire HD 10 (2021), KFTRWI, Fire OS 7 / API 28. The test exercised the existing device credential through Fire OS. The owner entered the PIN directly on the tablet.

The lock implementation in `MainActivity.kt` and `PrivacyPage.kt` has no changes between the 0.10.0 source checkpoint and the 0.11.0 checkpoint. This is hardware evidence for the installed 0.10.0 binary; it does not establish that Amazon has delivered 0.11.0.

## Results

| Check | Status | Evidence |
| --- | --- | --- |
| Enable app lock | PASS | After the owner authenticated, the privacy page displayed **Disable app lock**. Window Manager reported the `SECURE` window flag. |
| Background timeout | PASS | Sent the app to the Fire home screen, waited 136.86 seconds measured against device uptime, and brought its existing task forward. Process ID 7448 was unchanged. The fresh accessibility tree displayed **Billmivo is locked**, the device-credential explanation and **Unlock**. No force-stop or process restart was used during this interval. |
| Short background return | NOT TESTED | Returned after 36.33 seconds, but Fire OS returned no accessibility root. No unlocked-state assertion is made for this check. |
| Reopen after timed lock | PASS | The owner entered the credential directly and confirmed that Billmivo was unlocked. |
| Restore original app-lock setting | PASS | After the owner authenticated the disable action, the privacy page displayed **Enable app lock** and Window Manager no longer reported `SECURE` for the app window. |
| Persist the restored setting | PASS | Force-stopped and relaunched the app after disabling the lock. A fresh accessibility tree displayed **Bill forecast** and **0 open bills**, with no locked page or credential prompt. |
| Restore temporary display setting | PASS | Kept the charging tablet awake during the timed interval, then restored `stay_on_while_plugged_in` to its original value, `2`. |

The elapsed interval verifies relocking after more than two minutes. It does not measure the exact 120-second boundary. The source uses elapsed realtime and applies the lock when the activity starts after at least 120,000 milliseconds away.

Evidence is local under `deliverables/android/build-logs/app-lock-timeout-0110.json`, `app-lock-timed-locked.xml`, `app-lock-off.xml` and `app-lock-off-restarted.xml`. The enable-state dump is also on the tablet at `/data/local/tmp/billmivo-lock-enabled.xml`. No PIN value was requested, inspected or recorded.

The tablet was left on the unlocked dashboard with app lock off, matching its original setting. No application code changed during this check.
