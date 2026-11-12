"""
Train a bug-risk model for code changes on the ApacheJIT dataset.

ApacheJIT (Keshavarz & Nagappan, MSR 2022) labels ~106k commits from popular Apache
Java projects as bug-inducing or clean. Each row is one commit with change metrics.

We train on the authors' recommended split: older commits (2003-2016) for training,
the last 3 years for testing. Testing on the future is how the model would really be used.

Usage:
    python train_risk_model.py --data-dir apachejit/dataset
"""

import argparse
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, roc_auc_score
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import FunctionTransformer, StandardScaler

# Features our Java app can compute from a pull request's diff alone.
DEPLOYABLE = ["la", "ld", "nf", "nd", "ns", "ent"]
# Extra features that need repository history (developer experience, file age, ...).
HISTORY = ["ndev", "age", "nuc", "aexp", "arexp", "asexp"]
LABEL = "buggy"


def load(path: Path, needed: list[str]) -> pd.DataFrame:
    df = pd.read_csv(path)
    missing = [c for c in needed + [LABEL] if c not in df.columns]
    if missing:
        raise SystemExit(f"{path.name} is missing columns {missing}. Columns found: {list(df.columns)}")
    df[LABEL] = df[LABEL].astype(str).str.lower().isin(["true", "1", "yes"]).astype(int)
    return df


def heuristic_score(df: pd.DataFrame) -> np.ndarray:
    """The rule the Java app uses today: more added lines = riskier."""
    return np.minimum(0.5, df["la"].to_numpy() / 20.0)


def bugs_caught_in_top(y_true: np.ndarray, scores: np.ndarray, fraction: float) -> float:
    """If reviewers only had time for the riskiest `fraction` of changes, what share of bugs would they see?"""
    k = max(1, int(len(scores) * fraction))
    top = np.argsort(-scores)[:k]
    return y_true[top].sum() / max(1, y_true.sum())


def evaluate(name: str, y: np.ndarray, scores: np.ndarray) -> dict:
    return {
        "model": name,
        "roc_auc": round(roc_auc_score(y, scores), 3),
        "pr_auc": round(average_precision_score(y, scores), 3),
        "bugs_in_top_20pct": round(bugs_caught_in_top(y, scores, 0.20), 3),
    }


def logistic(features: list[str]):
    # log1p tames the long tail (most changes are small, a few are huge).
    return make_pipeline(
        FunctionTransformer(np.log1p, feature_names_out="one-to-one"),
        StandardScaler(),
        LogisticRegression(max_iter=1000),
    )


def boosted():
    return HistGradientBoostingClassifier(max_iter=300, learning_rate=0.05, random_state=42)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", type=Path, required=True, help="ApacheJIT 'dataset' folder")
    parser.add_argument("--out-dir", type=Path, default=Path("model"))
    args = parser.parse_args()

    all_features = DEPLOYABLE + HISTORY
    train = load(args.data_dir / "apachejit_train.csv", all_features)
    test = load(args.data_dir / "apachejit_test_large.csv", all_features)
    y_train, y_test = train[LABEL].to_numpy(), test[LABEL].to_numpy()

    print(f"Train: {len(train):,} commits ({y_train.mean():.0%} bug-inducing)")
    print(f"Test:  {len(test):,} commits ({y_test.mean():.0%} bug-inducing), from later years\n")

    results = [evaluate("baseline: lines-added heuristic", y_test, heuristic_score(test))]
    fitted = {}
    for label, features in [("diff-only", DEPLOYABLE), ("diff + history", all_features)]:
        for kind, model in [("logistic", logistic(features)), ("boosted trees", boosted())]:
            name = f"{kind}, {label} features"
            model.fit(train[features], y_train)
            scores = model.predict_proba(test[features])[:, 1]
            results.append(evaluate(name, y_test, scores))
            fitted[name] = (model, features)

    table = pd.DataFrame(results)
    print(table.to_string(index=False))
    print("\nroc_auc: 0.5 = coin flip, 1.0 = perfect ranking. "
          "bugs_in_top_20pct: share of bug-inducing changes found by reviewing only the riskiest 20%.")

    # Ship the best model the Java app can actually feed: diff-only features.
    deployable = {k: v for k, v in fitted.items() if k.endswith("diff-only features")}
    best_name = max(deployable, key=lambda k: next(r["roc_auc"] for r in results if r["model"] == k))
    best_model, best_features = deployable[best_name]

    args.out_dir.mkdir(parents=True, exist_ok=True)
    joblib.dump(best_model, args.out_dir / "model.joblib")
    metadata = {
        "model": best_name,
        "features": best_features,
        "trained_on": "ApacheJIT apachejit_train.csv (2003-2016)",
        "tested_on": "ApacheJIT apachejit_test_large.csv (last 3 years)",
        "note": "Trained on a balanced set, so treat outputs as risk rankings, not exact probabilities.",
        "results": results,
    }
    (args.out_dir / "metadata.json").write_text(json.dumps(metadata, indent=2))
    print(f"\nSaved '{best_name}' to {args.out_dir / 'model.joblib'} (features: {', '.join(best_features)})")


if __name__ == "__main__":
    main()
