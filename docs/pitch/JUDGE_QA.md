# Judge questions and honest answers

## 1. Does it stop a WhatsApp notification before Android shows it?

No. NotificationListenerService receives the alert after Android posts it. AttentionTwin can snooze/cancel afterward. A brief banner may flash. Optional DND can suppress sound, vibration and heads-up when policy access and system behavior permit.

## 2. Is “Block in focus” probabilistic?

No. In the decision code, Block during active focus deterministically holds, unless a safety exception applies. The estimate is for Smart decisions only.

## 3. What is the default for WhatsApp?

The code's current role default is Allow. The user changes it in the app list. Do not claim WhatsApp blocks by default.

## 4. What does Smart learn about me?

The current system is a prototype, not a validated personal learner. The backend's action-aware model is trained on synthetic examples; offline Lab uses qualitative rules and unknown personal probabilities.

## 5. Are your accuracy numbers from real people?

No. The metadata reports a synthetic chronological test split: 1,600 examples from simulated identities/days, with an open-within-five-minutes label. Those metrics do not establish real-user accuracy or causal impact.

## 6. Does the counterfactual prove what would have happened if we deferred?

No. It compares modeled scenarios. There is no randomized user-level counterfactual validation. In the backend, some costs are hand-coded heuristics and the recommendation is a rule.

## 7. Does the Lab comparison actually suppress notifications?

No. The Lab is decision support. Offline it is qualitative; a configured backend returns a comparison. Neither comparison itself routes or suppresses the notification.

## 8. What exceptions exist?

Code includes call/alarm categories, verification-code/OTP patterns, configured urgent words, VIP matching, and selected system-package handling. Matching is imperfect; users should test their own notification formats.

## 9. Can a user release a snoozed notification early?

Android exposes no app API for unsnoozing early. Release now can restore any saved preview and show a digest, but the original notification returns according to Android's schedule.

## 10. Is message content uploaded to the laptop backend?

The native listener path handles notifications locally and does not send the notification body to the backend. The optional Lab/backend path is separate and may send explicitly supplied comparison context. The app should not be described as globally offline if configured backend features are used.

## 11. What happens if notification access is revoked?

The service cannot process new notifications. Diagnostics/settings are intended to help the user restore access, but the full Redmi recovery flow has not been tested.

## 12. Does this survive force-stop or reboot on Redmi HyperOS?

Not yet demonstrated. The project has rebind and boot/schedule code, but Xiaomi autostart, battery restrictions, force-stop and reboot behavior require physical testing.

## 13. How do you prevent being stuck in Do Not Disturb?

The code stores the prior interruption filter and tracks whether AttentionTwin owns the change, then restores it when focus ends or on recovery. Unit tests cover the ownership predicate. Real-device restore paths still need testing, especially after force-stop/reboot.

## 14. Does it drain battery?

We have not measured battery impact. The listener is event-driven and schedules focus boundaries, but that is not a substitute for profiling on target devices.

## 15. Does it measure fatigue or cognitive load?

No. Those are roadmap concepts/placeholders. There is no validated fatigue sensor or cognitive-state inference in the current product.

## 16. Does it find my best focus hours?

No. It supports timers and schedules. Individualized focus-window learning is not implemented.

## 17. Does it detect that I prolonged a task because of an interruption?

No reliable causal measurement is implemented. Some events such as open/click are tracked, but completion time and counterfactual task duration are not established.

## 18. Why not just use Android Do Not Disturb?

DND silences broadly according to system rules. AttentionTwin adds per-app roles, explicit exceptions, focus timing and an Inbox/reason record. DND is optional and remains subject to Android policy.

## 19. What if an urgent WhatsApp message is held?

An urgent word or VIP exception may allow it, but recognition is not perfect. The product cannot promise that every urgent message will be identified. Users can keep an app on Allow or pause focus.

## 20. What can you prove in this demo?

The current source and unit-test/build outputs support the decision logic and Android packaging. A simulated test notification exercises the listener path. Only a real friend-to-phone WhatsApp test can demonstrate that path on the target Redmi, and that remains outstanding.

## 21. Did you use public datasets such as AmI or UbiqLog?

No. They are listed as possible references in project documentation, but this prototype's model artifact is synthetic. No public dataset training result is claimed.

## 22. Why not show the model's ROC-AUC as proof?

Because it measures separation on synthetic held-out examples, not performance on real people or benefit from the product. It is useful as a development check, not a headline impact claim.

## 23. Is the app available on the Play Store?

This is a debug APK prototype. Store publication, policy review, signing and release-quality testing are not established here.

## 24. What is the biggest remaining risk?

Reliable behavior across the target phone's Android/HyperOS lifecycle: listener connection, transient presentation, snooze/repost timing, DND restoration and boot/force-stop handling.

## 25. What would you do next?

First run a structured Redmi test matrix with real notifications, access changes, DND, lock screen, reboot, force-stop, autostart and battery settings. Then, with consent, measure focus completion and self-reported interruption/recovery outcomes before making personalization or impact claims.
