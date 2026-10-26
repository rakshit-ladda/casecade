# Three-minute pitch script

**One-sentence pitch:** AttentionTwin gives Android users a simple, explainable way to choose which app alerts wait during focus, while keeping urgent exceptions and the user in control.

## 0:00–0:30 — The problem

“You sit down to finish something important. A message arrives, you check it for a moment, and getting back into the task takes longer than the glance itself. A field study by Iqbal and Horvitz observed 27 information workers over two weeks; after alert-caused task switches, people spent nearly ten minutes on the intervening activity on average, then needed roughly another ten to fifteen minutes to return to focused work. That is evidence about desktop work in that study, not a promise about every phone user. It does show why the interruption and recovery both matter.”

## 0:30–0:55 — The product

“AttentionTwin makes the choice concrete. Pick an app such as WhatsApp, choose Block in focus, and start a focus session. Allow remains the safe default. Calls, alarms, verification codes, urgent words and VIP contacts have exception paths. If you want, Android’s Do Not Disturb policy can also silence sound, vibration and heads-up alerts while focus is active.”

## 0:55–1:35 — Demo

“Here I set WhatsApp to Block in focus and start a short session. This test notification goes through Android’s notification-listener route. The app holds it and records why, so I can inspect the decision in the Inbox. I can pause focus or end it when I choose. On a real message, Android may briefly present the alert before our listener can act; DND is the option intended to prevent that sound or heads-up presentation. Android controls when a snoozed notification returns, so Release now can show the saved inbox information but cannot force Android to unsnooze early.”

## 1:35–2:10 — How it works

“The phone’s notification listener checks the package, focus state, app role and safety exceptions after Android posts a notification. Block is deterministic: the estimate cannot overrule it. Smart may use an estimate to choose whether to defer or allow. The native handling path can run locally without the laptop backend. The Lab comparison is separate: offline it is a qualitative rule-based comparison with unknown personal probabilities. The optional backend uses a model trained and tested on synthetic data, so we treat that as a prototype, not proven personal intelligence.”

## 2:10–2:40 — What is different

“We make the intervention visible and reversible: app-by-app choice, a reason for the decision, a local inbox, and a focus timer rather than a black-box promise that every interruption disappears. We also show the actual Android constraints instead of hiding them. The feature worth evaluating is whether people can keep the alerts they care about available while reducing unwanted interruptions.”

## 2:40–3:00 — Close

“Our next step is a real-device reliability study on Redmi HyperOS, followed by an opt-in pilot measuring held alerts, exceptions, focus completion and self-reported recovery. We will only claim reduced disruption if that evidence supports it. AttentionTwin: focus with control over what waits.”

**Source:** S. T. Iqbal and E. Horvitz, “Disruption and Recovery of Computing Tasks: Field Study, Analysis, and Directions,” CHI 2007, [Microsoft Research paper](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/11/CHI_2007_Iqbal_Horvitz-1.pdf). Study context and sample are stated in the script; results are not generalized as a phone-specific effect.
