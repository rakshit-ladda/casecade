# AttentionTwin roadmap

Roadmap order is based on risk reduction. Later research features depend on reliable, consented data and must not be presented as built today.

## Now: verify the phone fundamentals

- Exercise real WhatsApp, Instagram and Gmail notifications with focus off, Allow, Block and Smart roles.
- Verify listener disconnect/rebind, notification access revoked/restored, permission prompts and diagnostics.
- Measure whether DND suppresses sound, vibration and heads-up on Redmi HyperOS and restores the prior interruption filter after normal end, app restart, force-stop and reboot.
- Verify snooze, reposts, group summaries, Inbox persistence and return-at-break timing.
- Test lock screen, reboot, force-stop, autostart, battery restrictions and network-off native operation.
- Package a signed release build only after debug behavior and store policy/privacy review are complete.

## Next: consent-first personal outcomes

- Make opt-in and data retention controls explicit; collect the minimum event fields needed for evaluation.
- Let users record whether a held alert was useful, urgent, distracting, or missed.
- Track session completion and voluntary short self-reports; avoid inferring mental state from app use.
- Define an evaluation protocol and baseline before recruiting participants.

## Research: validate estimates instead of assuming impact

- Compare model estimates with observed, consented outcomes on held-out participants and time periods.
- Run a low-risk within-person trial only after ethics/consent review, with user override and safety exceptions.
- Separate prediction quality from causal impact: evaluate whether a policy changes interruption burden and task completion, not just whether a model predicts opens.
- Report uncertainty, subgroup limitations, missingness and adverse cases.
- Audit dataset provenance, licensing and compatibility before any external dataset is used.

## Later: personalization and platform expansion

- Consider learned focus windows only after enough consented data and a validated benefit.
- Consider richer interruption/recovery or fatigue-related measures only with a safe, transparent, validated method; current fatigue features are not implemented.
- Assess iOS feasibility separately because platform notification controls differ substantially; do not promise feature parity.

## Release gates

1. No known DND state can be left altered by AttentionTwin through tested lifecycle transitions.
2. User can find and recover from missing listener access.
3. Critical messages, exceptions, and user override behavior are tested on target devices.
4. Privacy notice, retention, deletion and any backend data flow are documented accurately.
5. Claims about savings or personalization are supported by real-user evaluation, otherwise remain explicitly hypothetical.
