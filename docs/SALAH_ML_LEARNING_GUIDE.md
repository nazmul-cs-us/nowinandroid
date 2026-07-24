# Learn Machine Learning with the Salah Detector — Step by Step

This guide teaches the machine learning behind the salah posture detector by
walking through every stage of the real pipeline — what happens, **why** it
happens, how to see it with your own eyes, and how to tell when something is
wrong. Work through it top to bottom once; afterwards you'll be able to
diagnose bad training data and judge test results on your own.

- For the exact operational commands, use `docs/SALAH_TRAINING_PLAYBOOK.md`.
- For the architecture reference, use `docs/SALAH_ML_TRAINING_GUIDE.md`.
- This guide is the *classroom*.

---

## The big picture (read this first)

We are solving a **supervised classification** problem:

> Given the last 2 seconds of pocket sensor data, which of 7 postures is the
> person in?

"Supervised" means we teach by example: we hand the model thousands of
*(sensor data, correct label)* pairs, and an optimization algorithm adjusts
the model's internal numbers (weights) until its guesses match the labels.
That adjustment process is **training**. Everything in this pipeline exists
to make those examples *plentiful, correct, and honest* — and to *measure*
how well the learned model generalizes to data it has never seen.

The three mantras you'll see enforced everywhere below:

1. **Garbage in, garbage out** — a wrongly labeled recording teaches the
   model the wrong thing with perfect efficiency.
2. **Never test on what you trained on** — otherwise you're grading the
   model on questions it memorized.
3. **One number is never the whole story** — 95 % accuracy can hide a class
   that fails completely.

---

## Step 0 — Know your raw data (the JSONL files)

Every recording is a `.jsonl` file: one JSON object per line, one line per
**100 ms window** of sensor data.

```json
{"timestamp": 1741823000123, "session_id": "56b7cf1a", "posture": "RUKU",
 "accel_x": [0.1, 0.2, ...5 values...], "accel_y": [...], "accel_z": [...],
 "gyro_x": [...], "gyro_y": [...], "gyro_z": [...],
 "pitch": 87.3, "roll": -2.1, "accel_magnitude": 9.81, "gyro_magnitude": 0.05}
```

Why 5 values per axis? Sensors run at 50 Hz; 100 ms × 50 Hz = 5 samples.
The `posture` field is the **label** — whatever the app believed you were
doing when that window was written.

**Do it now:**

```bash
cd training/salah_model
python3 inspect_data.py
```

You get a per-file table, per-class totals against the 500-window target,
usable-segment counts for train/validation/test splitting, a class-balance
ratio, and ⚠️ warnings for broken or pending-review files. This is your
data-quality dashboard — run it after every `adb pull`.

**Exercise 0.1 — look at one window by hand:**

```bash
head -1 "$(ls data/salah_training_data/*.jsonl | head -1)" | python3 -m json.tool
```

**Exercise 0.2 — see a recording as a picture:**

```bash
python3 inspect_data.py --file data/salah_training_data/<any-file>.jsonl --plot
```

Open the PNG it writes. (The in-app **3D Visualization** card offers a
phone-side alternative: scatter/humanoid views with playback and an
"analyze predictions" overlay.) Motion spikes (accel/gyro magnitude) should sit at
segment *boundaries*; the shaded label regions should be flat inside. A
spike in the middle of a "SUJUD" segment means the label boundary is wrong —
this is what bad training data literally looks like.

---

## Step 1 — Features: turning raw signals into evidence

Models don't eat raw sensor arrays well. We summarize each 100 ms window
into **30 numbers** ("features") that carry the evidence a human would use:

- *Where is gravity pointing?* → accel means, pitch, roll
  (standing vs bowing vs prostrating have wildly different gravity vectors
  for a phone in a pocket)
- *How much movement?* → std-devs, magnitudes, energy
  (transitions move; static postures don't)
- *How is it changing within the window?* → ranges, variances

The full 30-feature layout is in the playbook's reference section. Two
things matter for learning:

1. **The same 30 features are computed twice** — in Python
   (`feature_engineering.py`) for training and in Kotlin
   (`SalahFeatureExtractor.kt`) on the phone. They must match bit-for-bit in
   order and formula, or the deployed model receives inputs unlike anything
   it trained on. This is a classic production-ML failure mode called
   **training/serving skew**.
2. Features are **hand-designed** here (classical approach). The CNN then
   learns *patterns across time* on top of them.

**Exercise 1.1 — compute a feature yourself** and confirm the intuition:

```python
import json
lines = [json.loads(l) for l in open("data/salah_training_data/<file>.jsonl")]
w = lines[0]
accel_mean_z = sum(w["accel_z"]) / len(w["accel_z"])   # feature index 2
print(w["posture"], "accel_mean_z =", accel_mean_z)
```

Compare `accel_mean_z` between a QIYAM file and a SUJUD file. Gravity moved
from one axis to another — that single number already separates the two
classes most of the way.

**Exercise 1.2 — see class separability:**

```bash
python inspect_data.py --plot
```

The boxplot PNG shows pitch per class. Classes whose boxes overlap heavily
(traditionally JALSA vs TASHAHHUD — both are sitting!) are exactly the ones
the model will confuse. This is worth internalizing: **model confusion is
usually visible in the data before you ever train**.

---

## Step 2 — Sequences: giving the model a sense of time

One window (100 ms) can't distinguish "standing still" from "a brief pause
mid-transition". So we feed the model a **sequence of 20 consecutive
windows = 2 seconds** — shape `(20, 30)`.

Sequences are cut from recordings with a sliding window: start at window 0,
take 20; slide forward by `stride` windows; repeat. With `--stride 10`,
consecutive sequences share 50 % of their windows — more training examples
from the same data, at the cost of them being *correlated* (see Step 3 for
why that's dangerous).

Rule of thumb: `--stride 10` normally; `--stride 3` when a class is scarce
and you accept more correlation to get more examples.

---

## Step 3 — The split: the most important honesty rule in ML

We split sequences into three sets:

| Set | Share | Used for |
|---|---|---|
| **Train** | 70 % | Adjusting the model's weights |
| **Validation** | 15 % | Deciding when to stop training & comparing experiments |
| **Test** | 15 % | Touched ONCE at the end — the honest generalization number |

**The trap:** overlapping sequences. Two sequences that share 10 windows are
near-duplicates. Split randomly, and one lands in train and its twin in
test — the model "generalizes" to data it effectively saw. Your test
accuracy becomes a lie (impressively high, meaningless).

**The fix in this pipeline:** `split_by_session()` in
`train_salah_detector.py` assigns every *whole recording session* to exactly
one set. This is stricter than segment grouping: windows and conditions from
the same continuous recording cannot appear in both training and test. It does
not provide participant isolation unless participant IDs are collected.
That's also why collecting **many separate sessions and people** matters more
than one long session: with few sessions, validation and test remain noisy.

**Red flag you can now diagnose:** val/test accuracy ≥ 99 % but the deployed
model performs poorly in your pocket → your dataset probably has too few
distinct sessions per class (check `inspect_data.py`), or too little diversity
between people/devices for the held-out session to represent real use.

---

## Step 4 — Balancing and augmentation: more (and fairer) examples

**Balancing:** if QIYAM has 4× the windows of TASHAHHUD, the model can reach
high accuracy by rarely predicting TASHAHHUD. The pipeline oversamples
minority classes so each class carries similar weight in training. But
oversampling *repeats* data — it doesn't add information. Collecting real
TASHAHHUD data beats duplicating what you have. (Run `inspect_data.py`; the
balance ratio warning tells you when this matters.)

**Augmentation** (`data_augmentation.py`) creates synthetic variants of
training sequences:

| Transform | Simulates |
|---|---|
| Rotation (≤15°) | Phone sitting differently in the pocket |
| Time-warp | Praying slightly faster/slower |
| Noise | Sensor jitter |
| Magnitude scaling | Different phone masses/mounts, movement intensity |

Each is *label-preserving*: a rotated RUKU is still RUKU. Augmentation is
applied **only to the training set** — augmenting val/test would corrupt
your measuring stick.

**Exercise 4.1 — measure augmentation's value:** train twice, compare
`test_accuracy` in `output/dataset_report.json`:

```bash
python train_salah_detector.py --data_dir data/salah_training_data --output_dir output
python train_salah_detector.py --data_dir data/salah_training_data --output_dir output_noaug --no-augment
```

With a small dataset, expect the no-augment run to score *worse on test*
(and possibly better on train — that's overfitting, Step 6's topic).

---

## Step 5 — Normalization: putting features on the same scale

`accel_mean_z` lives near ±9.8; `gyro_var` might live near 0.001. Neural
networks train badly when inputs span such different scales, so every
feature is **Z-scored**: `(value − mean) / std`, using the mean/std of each
feature **computed on the training set only**.

Why train-set only? Using all data would leak information about val/test
into training (their statistics shift the mean). It's a small leak, but the
discipline is the point: **anything fitted must be fitted on train only.**

These 30 means and stds are saved to `norm_params.json` and shipped inside
the app (`salah_norm_params.json`) — the phone must preprocess exactly like
training did. If you ever change features, both sides change together.

---

## Step 6 — Training: what actually happens epoch by epoch

The model (a small 1D CNN — three convolution blocks that learn temporal
patterns across the 20 windows, then a classifier head) starts with random
weights. Then, repeatedly:

1. Take a **batch** of 32 sequences.
2. **Forward pass**: model outputs 7 probabilities per sequence.
3. **Loss**: cross-entropy measures how wrong the probabilities are vs the
   true labels (confidently-wrong costs the most).
4. **Backpropagation**: compute how each weight contributed to the loss.
5. **Optimizer step** (Adam, lr = 0.001): nudge every weight slightly in
   the direction that reduces loss.

One pass over all training data = one **epoch**. After each epoch the model
is scored on the **validation set** (no weight updates), producing the
`val_accuracy` curve. Three safety mechanisms react to that curve:

- **EarlyStopping** (patience 15): if val accuracy hasn't improved for 15
  epochs, stop — training longer would only memorize.
- **ReduceLROnPlateau**: halve the learning rate when val loss stalls
  (finer steps near the summit).
- **Checkpoint**: keep the weights from the *best* val epoch, not the last.

**Exercise 6.1 — read the training curves.** After any run:

```python
import json, matplotlib.pyplot as plt
h = json.load(open("output/training_history.json"))
plt.plot(h["accuracy"], label="train")
plt.plot(h["val_accuracy"], label="val")
plt.xlabel("epoch"); plt.legend(); plt.savefig("output/curves.png", dpi=130)
```

How to read what you see:

| Pattern | Diagnosis | Fix |
|---|---|---|
| Train ↑ steadily, val ↑ then **flattens/falls** while train keeps rising | **Overfitting** — memorizing train data | More data, more augmentation, stop earlier (all already automated here; the gap size still tells you data is scarce) |
| Both curves plateau **low** | **Underfitting** — model can't express the pattern, or features/labels are broken | Check labels first (Step 8) — with this model size, bad data is far more likely than insufficient capacity |
| Val **above** train | Normal here early on (dropout is active during train scoring, off during val) — only worry if the gap is huge for many epochs | — |
| Val jumps around wildly | Validation set too small/too few groups | Collect more distinct sessions |

---

## Step 7 — Validating results: how to know if the model is actually good

After training, `output/dataset_report.json` holds the verdict. Read it in
this order:

**1. `metrics.test_accuracy` — the headline.** Val guided training; test was
never touched, so test is the honest number. Expect 0.90–0.97 with a decent
dataset. Distrust anything ≥ 0.99 (Step 3 red flag).

**2. `metrics.per_class_f1_test` — the per-class truth.** F1 blends two
questions about each class:
- *Precision*: when the model says TASHAHHUD, how often is it right?
- *Recall*: of all real TASHAHHUD windows, how many did it find?

F1 is their harmonic mean — it only scores high when **both** are high. A
class with F1 0.60 while overall accuracy is 0.95 means that class is
carried by the others. The app's "Deployed model" card surfaces the two
weakest F1 classes for exactly this reason: **they are your next collection
targets.**

**3. `metrics.confusion_matrix_test` — where errors go.** Rows = true class,
columns = predicted class, in the label order `QIYAM, RUKU, GOING_TO_SUJUD,
SUJUD, JALSA, TASHAHHUD, QIYAM_RISING`. The diagonal is correct; every
off-diagonal cell is a specific mistake. Worked example:

```
true JALSA row:      [0, 0, 1, 2, 61, 14, 0]
                                  ↑    ↑
                             correct   14 JALSA windows predicted TASHAHHUD
```

That's not random noise — the model confuses the two *sitting* postures.
The fix is not "train more"; it's *collect data that separates them* (e.g.
longer, cleaner examples of each, since their difference is partly duration
and hand position — subtle in pocket sensors).

**4. Cross-check on the phone.** Lab metrics ≠ pocket reality. After
deploying: run the review screen's **Analyze data quality** on a good
recording (agreement should be ≥ 85 % green), then do one live rak'ah and
watch the detection panel. The batch-inference agreement is the same model
in the same preprocessing pipeline — it's the most honest field test
available without writing code.

---

## Step 8 — Finding what's wrong in the training data

Work top-down through this table when anything looks off:

| Symptom | Likely cause | How to confirm | Fix |
|---|---|---|---|
| One class F1 near 0 | Almost no data for it | `inspect_data.py` class totals | Collect that class |
| Two specific classes confused | Genuinely similar signals or cross-labeled files | Confusion matrix + `inspect_data.py --plot` boxplots overlapping | Collect cleaner/longer examples of both; check labels |
| Test accuracy high, real-world bad | Too few sessions (leakage-ish), or phone position differs from training | Count files per class; try recording with the phone in the *other* pocket | More sessions, more variety (pockets, clothing) |
| Test accuracy suddenly dropped after adding data | The new files are mislabeled | `inspect_data.py` warnings; in-app file agreement (a bad file scores red) | Review/relabel or delete the new files |
| Val accuracy wildly unstable between runs | Tiny validation set | Few groups per class in split printout | More distinct recordings |
| Training crashes / class missing | Unreviewed live files (all-QIYAM) or unknown labels | `inspect_data.py` flags both | Review & label in app, or delete |
| Great curves, terrible on-device | Feature skew (Python ≠ Kotlin) — rare, but catastrophic | Did you modify feature code on one side only? | Re-align `feature_engineering.py` ↔ `SalahFeatureExtractor.kt`, bump model version |

The four lenses, in escalating effort:
0. **Posture-tile progress bars** (in-app Select Posture grid) — uneven bars =
   dataset imbalance; visible before you even pull the data.
1. **`inspect_data.py`** — structural problems (counts, balance, broken files).
2. **`inspect_data.py --file X --plot`** — label/motion misalignment inside one file.
3. **In-app review + Analyze** — the deployed model itself points at wrong labels
   (red-flagged segments are usually YOUR error, not the model's).

---

## Step 9 — The experiments ladder (your hands-on curriculum)

Each experiment teaches one core ML concept with this real project. Do them
in order; write down the test accuracy before and after each.

1. **Baseline** — train on current data, record `test_accuracy` and the two
   weakest F1 classes. *(Concept: establishing a baseline.)*
2. **Feed the weakest class** — collect 3 guided sessions emphasizing it,
   retrain, compare per-class F1. *(Targeted data collection beats blind
   collection.)*
3. **Augmentation ablation** — `--no-augment` vs default. *(Regularization
   via data.)*
4. **Stride sensitivity** — `--stride 3` vs `--stride 10` vs `--stride 20`.
   Watch train/val gap. *(Correlated samples inflate optimism.)*
5. **Sabotage test** — deliberately mislabel ONE file (rename a copy, edit
   its `posture` fields), retrain, watch the confusion matrix light up, then
   find the bad file using only the in-app agreement analysis. Delete it.
   *(Learn to detect poisoned data — this skill is the whole game.)*
6. **Leave-one-session-out** — move your newest recording out of the data
   dir, train without it, then check the model's agreement on that file in
   the app. That's *true* out-of-sample testing. *(Generalization.)*
7. **Capacity probe** — halve the Conv filters (32/64/128 → 16/32/64) in
   `build_model()`, retrain. Almost the same accuracy? The bottleneck is
   data, not model size — as it usually is. *(Model capacity vs data.)*

After the ladder you will have personally seen: baselines, targeted
collection, regularization, leakage, data poisoning, out-of-sample testing,
and capacity trade-offs. That's a genuine applied-ML foundation earned on
your own project.

---

## Glossary (the terms used above)

| Term | Meaning here |
|---|---|
| **Label** | The `posture` string attached to a window — the "right answer" |
| **Feature** | One of the 30 numbers summarizing a window |
| **Sequence** | 20 consecutive windows (2 s) — one training example |
| **Epoch** | One full pass over the training data |
| **Batch** | The 32 sequences processed between weight updates |
| **Loss** | The number training minimizes (cross-entropy here) |
| **Overfitting** | Memorizing train data instead of learning the pattern |
| **Leakage** | Test data influencing training — inflates metrics |
| **Precision / Recall / F1** | "When it says X, is it right?" / "Does it find all X?" / both combined |
| **Confusion matrix** | Table of true class vs predicted class counts |
| **Z-score normalization** | (value − mean) / std, statistics from train set only |
| **Augmentation** | Label-preserving synthetic variants of training data |
| **Quantization** | Shrinking model weights to int8 for speed/size |
| **Training/serving skew** | Phone preprocessing ≠ training preprocessing |
