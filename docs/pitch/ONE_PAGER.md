# AttentionTwin

## Focus with control over what waits

AttentionTwin is an Android prototype that lets people choose which app notifications wait during a focus session. It combines per-app roles, focus timing, an optional Android silence policy, and a local Inbox that explains held decisions.

## The problem

Digital interruptions can consume both the interruption itself and the time needed to resume the original task. Iqbal and Horvitz's 2007 field study of 27 information workers reported nearly ten minutes spent on alert-caused switches on average, followed by roughly ten to fifteen minutes to return to focused work. That study was on desktop work; it motivates the problem but does not measure AttentionTwin or predict every phone user's experience.

## How it works

1. Choose an app role: Allow, Block in focus, Smart, or Important.
2. Start a timer or use a schedule.
3. Android posts an alert; the notification listener checks focus, role, and safety exceptions.
4. Block in focus is deterministic. Smart may use estimates to allow or defer.
5. Review the local Inbox and pause or end focus when needed.

Calls, alarms, OTP/verification codes, configured urgent words and VIP contacts have exception logic. Optional Do Not Disturb can silence alerts if the user grants Android policy access.

## What the prototype can claim

- Android app roles and timed focus exist in code.
- Block during active focus is covered by Android unit tests.
- Frontend production build passed; the project owner supplied successful Android unit-test/build output.
- Native notification handling is local and can run without the laptop backend.

## What it cannot claim yet

The listener runs after Android posts the notification, so an alert may briefly flash. Snoozed notifications return on Android's schedule and cannot be unsnoozed early by the app. The Lab's offline comparison is qualitative and has no personal probability; the optional backend model uses synthetic data. Redmi HyperOS reliability and real-user impact remain untested.

## Why now / what next

Smartphones are where work, school and personal messaging meet. AttentionTwin focuses on a small user-controlled intervention that can be tested on-device. Next: verify listener/DND/snooze behavior on Redmi HyperOS, then run a consent-first pilot measuring focus completion and self-reported recovery before making personalization or impact claims.

## Team

AttentionTwin project team — add confirmed team member names before sharing externally. No team identities are inferred here.

**Research:** S. T. Iqbal and E. Horvitz, “Disruption and Recovery of Computing Tasks: Field Study, Analysis, and Directions,” CHI 2007, [Microsoft Research](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/11/CHI_2007_Iqbal_Horvitz-1.pdf).
