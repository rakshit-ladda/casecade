# Two-minute phone demo

## Before presenting

- Install the latest debug APK built at `frontend/android/app/build/outputs/apk/debug/app-debug.apk`. Remove an older conflicting installation only if Android reports a signature/version conflict.
- On the Redmi, grant AttentionTwin Notification access and notification permission. Allow Autostart and set Battery to no restrictions if HyperOS offers those controls.
- If demonstrating DND, grant Notification Policy access and note the phone's current interruption filter so restoration can be checked.
- Keep the phone awake, network available for a real WhatsApp message, and focus timer short. Ask a friend in advance to send a harmless “hello.”
- Do not use seeded/demo records as if they were real incoming WhatsApp messages.

## Tap-by-tap (about 2 minutes)

**0:00–0:20** — Open Today. Point out the focus timer and the simple status. Say that the listener runs after Android posts alerts.

**0:20–0:40** — Open Settings → Apps. Select WhatsApp and set Block in focus. If the product starts with Allow, explain that the user explicitly chooses the rule.

**0:40–1:00** — Return to Today and start a short focus session. Optionally enable Silence alerts during focus if policy access was granted.

**1:00–1:25** — Have the friend send “hello.” Open Diagnostics/Inbox and show the app, decision and reason. Be clear: this real-device flow has not yet been validated on the project's Redmi, and a transient alert can appear before snoozing unless DND suppresses it.

**1:25–1:45** — Show Pause or end focus. Explain that a newly arriving notification after focus ends is allowed. Android determines the scheduled return time of a snoozed item; Release now cannot unsnooze early.

**1:45–2:00** — Optional: show Lab comparison, label it experimental, and state offline personal probability is unknown. Close with user control and the local notification path.

## Fallback if live delivery fails

Use the in-app “Send test notification” button; it exercises the notification-listener path with a simulated package. Label it a test notification, not a real WhatsApp delivery. If listener access or HyperOS stops the service, show Diagnostics and explain the failure honestly, then use a pre-recorded real-device capture only if it is clearly labelled as a prior recording. Demo data is synthetic and must be called synthetic.

## What a successful live proof means

One received WhatsApp alert is held and visible in the app's local Inbox with Block in focus as the reason. It does not prove that every notification is silent, that content is retained, or that the behavior survives reboot/force-stop. Test sound/banner separately with DND on and off.
