# Now Nudge ranker training

Now Nudge uses a candidate ranker, not a generative language model. Every feature supplies safe,
executable candidates and normalized signals. The trainer learns the small set of weights used by
`DeenlyRankingWeights`; Android and iOS share the same inference code.

## Training input

Export only consented, privacy-minimal interaction data. The CSV schema is:

```text
base_priority,context_match,unfinished_progress,user_affinity,freshness,expected_completion,impressions_today,was_recent_action,reward
```

The first six values and `was_recent_action` are in `[0, 1]`. `impressions_today` is capped at five.
The reward is `1.0` for completed, `0.7` for opened, `0.15` for revealed, and `0.0` for ignored or
dismissed. Do not export content text, voice recordings, search terms, precise location, or direct
user identifiers.

## Train and export

```bash
python3 training/nudge_model/train_ranker.py \
  --input training/nudge_model/data/train.csv \
  --output training/nudge_model/output/nudge-ltr-2026-10-05.json \
  --model-version nudge-ltr-2026-10-05
```

Keep the starter weights until the dataset contains enough representative outcomes. Before a new
model ships, compare completion, dismissal, feature diversity, and per-action performance against
the current model on a time-based holdout. Release it behind a versioned configuration and retain
the previous weights for rollback.

Retraining should be scheduled (for example monthly) only after a minimum data threshold is met.
It does not happen on the user's phone and should never block the app. The app continues to rank
with its last bundled, validated weights while a newer release is evaluated.
