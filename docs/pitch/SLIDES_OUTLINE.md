# AttentionTwin — 9-slide pitch outline

Keep screens legible from the back of a room. Use phone screenshots only when captured from the real build; label test and seeded content. Speaker notes are suggested phrasing, not additional product claims.

## 1. Attention has a recovery cost

**On slide:** “The interruption is the glance. The cost can be getting back.” Source: Iqbal & Horvitz, CHI 2007; 27 information workers, desktop field study.

**Notes:** Their study reported nearly 10 minutes on alert-caused switches on average, then about 10–15 minutes to return to focused work. This is motivating evidence, not a measured AttentionTwin outcome or phone-specific guarantee.

## 2. The user needs control, not another black box

**On slide:** “Choose what waits. Keep what matters.” Three app roles: Allow / Block in focus / Smart.

**Notes:** Allow is current default. The user configures the rule. Calls, alarms, OTP/verification, urgent terms and VIP contacts have exception logic; no classifier can identify every emergency.

## 3. A simple focus flow

**On slide:** “Pick apps → Start focus → Review held alerts.” Place a real Today or Settings screenshot.

**Notes:** Call out the app list, timer and local Inbox. Use a screenshot from the current APK; do not fake a delivered message.

## 4. What happens to an alert

**On slide:** “Android posts → AttentionTwin checks role and exceptions → Allow or hold.” Show the listener runs after post.

**Notes:** Snoozing may happen after a brief visible flash. Optional Android DND can suppress sound/vibration/heads-up if access and device policy allow it. Android controls return timing.

## 5. Deterministic safety rule

**On slide:** “Block means hold during focus. Exceptions stay available.”

**Notes:** Cost estimates cannot override Block. Smart uses estimates only for the Smart decision path. This behavior has unit-test evidence; Redmi delivery still requires physical testing.

## 6. Counterfactual is a prototype

**On slide:** “Compare possible choices before choosing.” Example label: “Illustration only — not a measured personal result.”

**Notes:** Offline Lab is qualitative and reports personal probability as unknown. Optional backend uses synthetic training data. Its comparisons do not cause the phone to suppress an alert. Avoid presenting synthetic benchmark scores as user outcomes.

## 7. Built for local control

**On slide:** diagram from ARCHITECTURE.mmd; “Native notification decisions stay on device.”

**Notes:** Native listener path is local. Lab can be configured to call a backend with explicitly supplied comparison context. Do not claim the whole app never uses a network.

## 8. What is proven and what is next

**On slide:** “Build and Android unit-test task passed; real Redmi reliability study next.”

**Notes:** Cite the supplied Gradle success and this audit's frontend build. No physical Redmi, reboot, force-stop, lock-screen, or Play Protect result is available. No real-user impact validation exists.

## 9. Ask

**On slide:** “Help us validate focus that respects the person.” Ask: Redmi test participants, notification reliability feedback, and mentors for consent-first evaluation.

**Notes:** Roadmap starts with reliability and opt-in measurement before personalization or impact claims.

## Worked example for slide 6 (clearly illustrative)

Assume a social context and a hypothetical model input P(open within five minutes) = 0.60. The backend's heuristic example uses 14 minutes of browsing and 12 minutes of deep-work resumption. Then ALLOW is illustrated as 0.60 × 14 + 0.60 × 12 = 15.6 expected minutes; DEFER uses a fixed 25-minute delay; SUPPRESS is shown as a comparison. These are illustrative heuristic calculations, not the offline personal model output, not a causal estimate, and not a measured user result. Do not use this numerical example unless the slide clearly keeps all those qualifications visible.
