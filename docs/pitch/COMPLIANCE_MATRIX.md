# AttentionTwin: mission-to-evidence matrix

Audit basis: repository at HEAD 92068a1, source inspection, the frontend build run recorded in FEATURES_VERIFIED.md, and the project owner's Gradle output. A feature is not labelled phone-verified unless tested on the Redmi device.

| Mission goal | Current implementation | Status | How to demonstrate / limit |
|---|---|---|---|
| Let people choose what interrupts focus | Per-app Allow, Block in focus, Smart and Important roles; current default is Allow | Implemented; phone untested | Settings → Apps → choose WhatsApp → Block. Defaults should not be described as blocking out of the box. |
| Hold a blocked app notification during focus | Listener classifies role and snoozes, then records the event; fallback cancel exists | Implemented; Android timing untested | Start focus, send real message, inspect Inbox and diagnostics. A banner may flash before listener action. |
| Keep urgent communications available | OTP/verification, urgent words, VIP and selected system categories have exceptions | Implemented in code; real-world matching untested | Use test cases or configured VIP/urgent text; never claim all emergencies are recognized. |
| Silence sound, vibration and heads-up | Optional DND priority policy with notification-policy access and prior-filter restoration | Implemented; device policy untested | Grant policy access, enable silence, observe; verify prior DND state is restored after end/reboot. |
| Focus timer and schedules | Timed focus, daily windows, pause, break/release controls, boot rescheduling | Implemented; HyperOS behavior untested | Set short timer and verify start/end; reboot and inspect schedule. |
| Give a useful inbox and history | Local event store, held items, release state and diagnostics | Implemented; persistence/device behavior untested | Trigger notification, inspect Inbox. Message body is optional preview and is not a complete mirror of WhatsApp. |
| Explain app impact with counterfactuals | Lab comparison supports offline qualitative rules; optional backend has action-aware synthetic model | Partial / experimental | Show as a comparison, not a personalized causal prediction or actual notification routing. |
| Estimate personal focus windows and fatigue | Placeholder concepts only; no validated individualized learning or fatigue measurement | Not implemented | State as future work. |
| Work without laptop backend | Native listener/focus path and offline Lab comparison | Implemented; phone untested | Disconnect laptop backend and test native controls; offline Lab has unknown personal probabilities. |
| Validate with real users | No real-user dataset or trial evidence | Not implemented | Do not quote user-impact, retention, or accuracy claims as real-world results. |

## Dataset and model honesty

The project did not train on the named public datasets. The backend artifact documents 6,400 synthetic training and 1,600 synthetic test examples generated for 10 simulated identities across 14 simulated days. Its reported accuracy 0.714 and ROC-AUC 0.770 apply only to a synthetic chronological split and predict an open within five minutes. They are not personal accuracy or evidence of reducing interruptions. Native notification handling is local; optional Lab/backend requests are a separate path and may send explicitly supplied comparison context.

## Safe demo claims

Say “AttentionTwin lets you choose which app alerts to hold during a focus session, with exceptions and an optional Android silence policy.” Say “the research motivates protecting recovery time” rather than “we save everyone 20 minutes.” Show the Lab as experimental decision support, not an autonomous learned personal twin.
