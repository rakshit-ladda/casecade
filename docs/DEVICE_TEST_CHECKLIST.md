# Redmi Note 13 Pro verification checklist

**Status: not run on a physical phone.** The latest Android source changes are not included in a rebuilt APK because no Java/JDK is available in this build environment. Do not treat these steps as passed until the exact APK is installed and checked on the Redmi.

Before starting, note the phone model, Android/HyperOS version, APK version/commit, time, and whether Notification Access, DND access, Autostart, and unrestricted battery use are enabled. Use a harmless test account and do not capture private message content.

| Test | Steps | Expected result | Status |
|---|---|---|---|
| Notification access | Enable AttentionTwin in Android Notification Access settings, return to the app | Phone check-up shows access enabled; listener connects | Not run |
| Background setup | Allow AttentionTwin Autostart and set battery use to unrestricted | Listener keeps working with the app closed; HyperOS may still impose limits | Not run |
| Focus ON, blocked app | Set WhatsApp to Block in focus, start focus, ask a friend to send “hello” | With DND on, no sound/banner; alert is held and appears at the configured break | Not run |
| Focus ON, Allow role | Set WhatsApp to Allow, keep focus on, send “hello” | Alert comes through normally; no snooze or cancel action | Not run |
| Focus OFF | End focus and send a new WhatsApp message | Alert comes through normally, even if an earlier snooze marker exists | Not run |
| Pause AttentionTwin | Turn on the master Pause control during focus and send an alert | No new blocking; DND owned by AttentionTwin is restored | Not run |
| DND restore | Enable Silence alerts during focus, grant policy access, start/end focus | Calls and alarms are allowed while active; previous filter/policy returns at end | Not run |
| Release everything | Hold a test alert and use the release button | Saved previews/digest appear; Android may still return its own snooze at its scheduled time | Not run |
| Screen locked | Lock phone, run focus, send an alert from a blocked app | Behavior matches the configured role; listener remains connected | Not run |
| Reboot | Reboot while focus and DND are active | AttentionTwin restores/reconciles DND and re-arms schedules after boot | Not run |
| Force-stop | Force-stop AttentionTwin, then send an alert | No app-side blocking while stopped; reopening should reconnect and show accurate status | Not run |
| Play Protect/sideload | Install the newly built APK directly, not from WhatsApp preview | Android installation result recorded; dismissing Play Protect is not proof of safety | Not run |

## Honest behavior limits

- The notification listener runs after Android posts an alert. Without DND, the first banner can flash before snoozing.
- AttentionTwin cannot pull an Android-snoozed notification forward early. It can show a digest and allow the original notification when Android re-posts it.
- WhatsApp keeps message content. Blocking hides its notification alert; it does not delete the message.
- Autostart and battery behavior depend on HyperOS settings and have not been verified here.
