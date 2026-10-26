# Data and ML status

## Sources currently used

The only currently trained data source is `data/synthetic/feature_dataset.csv`: 8,000 generated events across 10 simulated identities and a 14-day window. It contains `opened_within_5min` and `outcome_label`. It is development data only and must not be treated as evidence of performance on people. The current trainer and artifacts are in `backend/app/ml/action_aware_trainer.py`, `backend/app/ml/trainer.py`, and `backend/artifacts/`.

`data/synthetic/raw_synthetic_events.json` includes generated response latency and browsing/resumption duration values. Those values originate from the synthetic generator's rules and random draws. The model-ready CSV does not contain them; the current counterfactual engine substitutes hand-authored category/focus lookups. Neither is an observed-duration model.

## Candidate public data

| Dataset | Provenance and fields | Appropriate use and limits |
|---|---|---|
| [AmI-2018 notifications](https://github.com/komis1/ami2018-notifications) | README reports 176,195 notifications, 14 users, May–June 2018. Package, posted/removed times, priority, alert mode, screen/device state. GPL-3.0 repository. | May support response/removal timing proxy analyses after inspecting the archive and label semantics. No browsing duration, task-resumption label, intervention assignment, or notification content target. Keep license obligations. |
| [AmI-2019 notifications](https://github.com/komis1/ami2019-notifications) | README reports 229,792 notifications, 44 users, Dec 2018–May 2019, including package, posted/removed times, device state, venue metadata. README says free use and requests academic citation. | Useful notification/context signals, but not measured task resumption or app-session duration. Assess sensitive location fields and exact repository license before redistribution. IDs are not joinable to AmI-2018 users. |
| [UbiqLog source](https://github.com/Rezar/Ubiqlog) / [UCI dataset record](https://archive.ics.uci.edu/dataset/369/ubiqlog%2Bsmartphone%2Blifelogging) | General app/use lifelog. Source repository declares BSD-2-Clause; UCI record has privacy-use restrictions. | Could help app-session patterns if the exact obtainable dataset schema and permitted use are verified. It is not itself granular notification-response telemetry. Do not infer joins to notification datasets. |

No external dataset has been downloaded or used in training in this pass. The mission's other candidate sources still need direct inspection of their current schema, access terms, samples, and license.

## Target availability

| Target | Current support | Honest status |
|---|---|---|
| Quick open | Synthetic `opened_within_5min`; AmI data has post/remove timestamps that may proxy interaction but must be validated | Existing classifier is synthetic-only. Response timing is not the same as notification importance. |
| Browsing duration | Generated duration in raw synthetic records only | No current feature table target, observed-user target, trained duration model, or verified app-use linkage. |
| Task-resumption delay | Generated duration in raw synthetic records only | No measured return-to-task signal or trained estimator. Requires explicit user/session measurement or careful research proxy. |
| Focus/productivity periods | Synthetic focus context only | No personal task completion or self-reported focus measure is collected. |
| Fragmentation/fatigue | No suitable personal targets | Must be derived from opt-in app-switch/break observations and clearly named indicators, not inferred as a mental state from notification metadata alone. |
| Notification importance | No current labels | Add explicit priority/urgent-sender settings and optional feedback. Do not substitute open probability. |

## Evaluation requirements before claiming a model

Use chronological splits for temporal claims and user-held-out splits for new-user claims. Report target counts, class balance, baseline, calibration/metrics, missingness, and provenance. Synthetic evaluation only validates implementation against synthetic behavior. If action assignment is policy-generated, prediction conditioned on action is confounded by that policy; it is not a causal estimate of unchosen alternatives. Keep observed, user-reported, proxy, synthetic, and predicted records distinct in both schema and UI.

Reproducible commands already documented by the repository are the backend trainer invocations in `README.md`; confirm those paths against the installed environment before treating them as a release procedure. No new model was trained in this audit.

## Additional candidate named in the project brief

The brief also lists a **Mobile Phone Use dataset** as a possible source for app/session-duration priors. It was not downloaded, reviewed, or used in this pass. Confirm the exact dataset/version, schema, license, participant consent, and whether app foreground intervals are available before using it. Do not join it to AmI-2018, AmI-2019, or UbiqLog identities. Any app-session duration derived from those records would be a proxy for this product, not observed notification-caused browsing or task resumption.
