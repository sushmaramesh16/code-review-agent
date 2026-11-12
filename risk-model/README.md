# Risk model

Trains the model behind the agent's `predict_risk` tool, replacing the lines-added heuristic.

Data: [ApacheJIT](https://arxiv.org/abs/2203.00101) (Keshavarz & Nagappan, MSR 2022), about 106k
commits from popular Apache Java projects labeled bug-inducing or clean.

```bash
cd risk-model
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
git clone https://github.com/harsh8398/apachejit.git
python train_risk_model.py --data-dir apachejit/dataset
```

The script compares the current heuristic against logistic regression and gradient-boosted
trees, with and without repository-history features, testing on later years than it trains on.
It saves the best model that uses only features computable from a pull request's diff
(`la`, `ld`, `nf`, `nd`, `ns`, `ent`) to `model/`.
