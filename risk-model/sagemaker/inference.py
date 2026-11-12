"""
Runs inside the SageMaker scikit-learn container. SageMaker calls these four functions:
model_fn loads the model once, then each request goes input_fn -> predict_fn -> output_fn.

Request:  {"instances": [{"la": 120, "ld": 30, "nf": 4, "nd": 2, "ns": 1, "ent": 1.6}]}
Response: {"risk": [0.6123]}
"""

import json
import os

import joblib
import pandas as pd

FEATURES = ["la", "ld", "nf", "nd", "ns", "ent"]


def model_fn(model_dir):
    return joblib.load(os.path.join(model_dir, "model.joblib"))


def input_fn(body, content_type):
    if content_type != "application/json":
        raise ValueError(f"Unsupported content type: {content_type}")
    data = json.loads(body)
    rows = data["instances"] if isinstance(data, dict) and "instances" in data else [data]
    missing = [f for f in FEATURES if any(f not in row for row in rows)]
    if missing:
        raise ValueError(f"Missing features: {sorted(set(missing))}")
    return pd.DataFrame([{f: float(row[f]) for f in FEATURES} for row in rows], columns=FEATURES)


def predict_fn(features, model):
    return model.predict_proba(features)[:, 1]


def output_fn(prediction, accept):
    return json.dumps({"risk": [round(float(p), 4) for p in prediction]}), "application/json"
