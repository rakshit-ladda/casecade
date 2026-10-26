# AttentionTwin: evidence-first feature audit

Audit date: 26 October 2026. This is an audit of the repository at HEAD 92068a1 and the successful build output supplied by the project owner. “Implemented and verified” means source plus a completed unit/build command; it does not mean verified on a Redmi phone. “Implemented but untested on a phone” means code exists but OEM/device behavior remains unproven.

## Feature audit

| Feature | Status | Evidence | Honest limitation |
|---|---|---|---|
| Android notification listener | Implemented but untested on a phone | `frontend/android/app/src/main/AndroidManifest.xml`, FocusListenerService.onNotificationPosted | NotificationListenerService runs after Android posts the alert. No pre-display interception. |
| Allow / Block in focus / Smart / Important roles | Implemented but untested on a phone | FocusPlugin.getApps/setRole; FocusUtils.roleFor; AppNew.tsx role picker | Default is Allow. Actual package discovery and delivery need phone testing. |
| Focus timer, open-ended focus, daily windows | Implemented but untested on a phone | FocusPlugin.setFocus; FocusUtils.isFocusActive/inDailyWindow | Clock changes, schedule edges, and HyperOS behavior are untested on hardware. |
| Focus OFF or Allow leaves the notification alone | Implemented and verified | FocusListenerService.processNotification; DecisionEngineTest.focusOffAllowsBlockedApp and focusOnAllowRoleLeavesNotificationAvailable | Unit-level behavior and package build pass; real WhatsApp delivery is not yet verified. |
| Block role always holds during active focus | Implemented and verified | FocusListenerService.processNotification; DecisionEngineTest.blockInFocusHoldsEvenHighImportance and focusOnBlockRoleAlwaysHoldsRegardlessOfImportanceAndHistory | Android snooze behavior and visible alert timing remain device-dependent. |
| Engine exception fails open | Implemented and verified | DecisionEngine.evaluateForAppRoleSafely; DecisionEngineTest.engineFailureFallsOpen | A failure after a block has already snoozed the alert cannot undo that Android action. |
| Immediate snooze, fallback cancel, dedupe/re-post handling | Partial | FocusListenerService.processNotification; FocusUtils.releaseExpiredSnoozeMarkers | Snooze executes before event persistence for Block. OEM re-post timing and per-chat/group-summary behavior have not been tested on phone. |
| Break-time scheduling and session-end release | Implemented but untested on a phone | FocusScheduler.scheduleNext; BreakReceiver.onReceive; Store.releaseExpired | Uses Android alarms and snooze release markers; exact delivery time depends on Android. |
| Re-arm after reboot or package replacement | Implemented but untested on a phone | BootReceiver.onReceive; AndroidManifest.xml | The receiver is present; reboot recovery has not been observed on the Redmi. |
| Calls, alarms, OTP, urgent words, system apps, VIP exceptions | Implemented but untested on a phone | FocusListenerService.isNonActionable; DecisionEngine.hardExceptionReason; FocusUtils.isVip; DecisionEngineTest.otpCallAndAlarmAreAlwaysAllowed and urgentWordAlwaysAllows | Some exception paths have unit coverage; actual notification-category metadata and VIP matching vary by source app. |
| Optional DND silencing and restoration | Implemented but untested on a phone | DndController.reconcile/restoreNow; FocusPlugin.restoreDndNow; DecisionEngineTest.dndRestoresOnlyWhenAttentionTwinOwnsIt | Requires Notification Policy Access. This tests the ownership predicate, not Android’s real filter changes on the Redmi. |
| Pause AttentionTwin master switch | Implemented but untested on a phone | FocusPlugin.setPaused; FocusUtils.isFocusActive; AppNew.tsx Today and Settings controls | Phone behavior and DND restoration after tapping Pause need testing. |
| Release everything control | Partial | FocusPlugin.releaseNow/postHeldDigest; AppNew.tsx Today/Inbox/Settings | It restores saved previews and posts a digest. Android does not expose an API to pull an active snooze forward; original alerts can return only when Android re-posts them. |
| Suppress action and Inbox | Partial | FocusListenerService SUPPRESS branch; Store; AppNew.tsx Inbox | Native suppression is only reachable through Smart estimates and Keep previews; with previews off it falls back to defer. Suppression cancels the alert and stores a preview only when the user opts in. |
| Diagnostics and listener health | Implemented but untested on a phone | FocusPlugin.getState; DiagnosticsCard and NotRunningBanner in AppNew.tsx | Autostart is not queryable through a public Android API, so the UI gives a settings shortcut rather than a verified state. |
| Logcat decisions and listener rebind | Implemented but untested on a phone | FocusListenerService.recordCallback/onListenerDisconnected/recheckAndRebind | Requires device-side log inspection; no Redmi log capture has been supplied. |
| Local Smart decision estimate | Implemented but untested on a phone | DecisionEngine.evaluate/evaluateForAppRole; Store.stats | Uses simple category priors, local counts, and hand-authored costs. It is not the backend scikit-learn model and is not validated human behavior. |
| Counterfactual ALLOW / DEFER / SUPPRESS in the optional Lab | Simulated or demo only | `backend/app/ml/counterfactual_engine.py`; `frontend/src/components/CounterfactualComparison.tsx`; `frontend/src/native/offlineRuntime.ts` | Backend probabilities come from synthetic training labels; duration/resumption values use fixed category/focus heuristics. Offline phone mode shows qualitative comparisons with unknown/null personal outcomes. No causal effect is measured. |
| Goal 1: discover the user’s best focus windows | Not implemented | FocusUtils supports user-entered schedules; offline demo data is synthetic | No personal best-window estimator, completed-focus-session dataset, or confidence calculation exists. |
| Goal 2: identify interruption causes | Partial | Store event/app stats; FocusListenerService.onNotificationRemoved; AppNew.tsx Insights | App counts and click outcomes exist, but there is no full app/category/hour causal analysis. |
| Goal 3: measure prolonged sessions by content type | Simulated or demo only | `backend/app/ml/synthetic_generator.py`; `frontend/src/native/offlineRuntime.ts` demo fixtures | Generated browsing durations are examples. There is no UsageStats integration or real session-duration linkage. |
| Goal 4: learn which notifications matter | Partial | Store.feedback/voteImportance; DecisionEngine.roleImportance; AppNew.tsx feedback callbacks | Explicit app roles and feedback votes exist; no validated per-message importance model exists. |
| Goal 5: detect mental fatigue | Not implemented | PlaceholderSection only mentions fatigue as a future idea | No fatigue sensor, self-report flow, or fatigue-risk indicator is implemented. Do not claim fatigue is measured. |
| Goal 6: find apps that fragment concentration | Not implemented | No UsageStats permission or foreground-app session tracking found in the Android manifest/source | The app cannot currently measure app switches or fragmentation. |
| UsageStats session measurement | Not implemented | No PACKAGE_USAGE_STATS permission/service in AndroidManifest.xml or native source | This requires a separate user-granted special access and implementation. |
| Privacy: notification-body storage | Implemented but untested on a phone | FocusListenerService stores metadata; preview assignment is conditional on keepPreviews; defaults in FocusPlugin.settingsObject | Notification title/text are read in memory for urgent/VIP checks. Preview persistence is optional. The broader legacy Lab can make API requests when configured. |
| Offline phone path | Implemented but untested on a phone | `frontend/src/focus.ts`; FocusPlugin; `frontend/src/native/offlineRuntime.ts` | Today/Live/Inbox/Settings use the native local plugin. Legacy Lab can use an optional backend; offline counterfactual output is qualitative only. |
| Deterministic synthetic demo data | Simulated or demo only | FocusPlugin.loadDemoData; offlineRuntime demo data | Clearly labeled and separable from real rows; it is not this user’s behavior. |
| Main consumer UI | Implemented but untested on a phone | `frontend/src/AppNew.tsx`; `frontend/src/AttentionTwin.css` | Today, Live, Inbox, Insights, Settings, setup, controls, and diagnostics are in source. Small-screen usability and accessibility still require device review. |
| Legacy Lab UI | Partial | `frontend/src/LabApp.tsx`; `frontend/src/components/CounterfactualComparison.tsx` | Retained research/demo surface. Some descriptive placeholder copy in PlaceholderSection.tsx describes future work and must not be pitched as built. |
| Backend ML pipeline | Simulated or demo only | `backend/app/ml/action_aware_trainer.py`; `backend/artifacts/action_aware_metadata.json`; `data/synthetic/` | The model is trained on generated data, not the project owner or recruited participants. Artifacts do not validate real-world attention prediction. |
| Android unit tests and APK build | Implemented and verified | `frontend/android/app/src/test/java/com/attentiontwin/app/DecisionEngineTest.java`; owner-supplied output for `gradlew clean testDebugUnitTest assembleDebug` | The supplied run reports BUILD SUCCESSFUL. It is build/unit evidence only, not a physical-phone test. |
| Backend test suite | Partial / not verified | 39 tests were collected by pytest; a prior run stalled at the first CORS test and was stopped | No passing backend-suite result is available for this audit. |

## What is real, estimated, and demo-only

The Android app has real local mechanisms: the notification listener, user-selected package roles, focus timers and schedules, SQLite metadata, safe exceptions, snooze requests, optional DND, and a local settings/history UI. Their operation on the Redmi has not been verified. The native Smart policy uses a simple local estimate from app/category priors and observed opens/importance votes. The separate backend counterfactual model is trained on synthetic labels; its browsing and resumption minutes are heuristics. The six broad learning goals are not yet delivered as personal measurements. Demo rows and their duration/outcome labels are synthetic.

## Claims to remove or qualify before presenting

- Do not say AttentionTwin learns the user’s best focus hours, session duration by content, fatigue, or fragmenting apps. Those features are absent or placeholders.
- Do not say the app predicts personal attention with a validated ML model. The phone’s Smart policy is rule-based; the research model is synthetic-only.
- Do not call counterfactual outputs causal. The action-aware dataset is simulated, and candidate actions in training are policy-generated.
- Do not say the app blocks an alert before it appears. The listener is after-post; optional DND can silence presentation when system access and policy permit.
- Do not promise that “nothing is lost” or that Release can unsnooze early. Android owns snooze timing; AttentionTwin can show a digest and restore opt-in previews.
- In PlaceholderSection.tsx, describe the “fatigue accumulation,” local retraining, individualized focus curves, and cognitive-state profiles as roadmap concepts, not current functionality.
- In the Lab, describe suppression as a model comparison. The on-device offline comparison does not execute suppression and reports unknown personal probabilities.

## Commands and evidence available for this audit

- `npm run build` — passed in this audit: TypeScript and Vite completed; Vite emitted a non-blocking large-chunk warning.
- `npx cap sync android` — passed earlier in this session: Capacitor copied the built web assets and updated Android plugins.
- `gradlew clean testDebugUnitTest assembleDebug` — the project owner supplied terminal output showing BUILD SUCCESSFUL, 106 actionable tasks, including app Java compilation and the Android unit-test task. The resulting APK path is `frontend/android/app/build/outputs/apk/debug/app-debug.apk` (4,376,284 bytes in the checked workspace).
- `.venv/Scripts/python.exe -m pytest backend/tests --collect-only -vv` — collected 39 tests. A prior full-suite run stalled at `test_cors_preflight_android_webview_http_localhost` and was interrupted; it is not a passing result.
- No physical Redmi/HyperOS test, Play Protect installation result, reboot test, or background/force-stop test is available.
