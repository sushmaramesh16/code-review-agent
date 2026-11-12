# Risk model

Trains and serves the model behind the agent's `predict_risk` tool.

Data: [ApacheJIT](https://arxiv.org/abs/2203.00101) (Keshavarz & Nagappan, MSR 2022), about 106k
commits from popular Apache Java projects labeled bug-inducing or clean. Training uses 2003-2016,
testing uses the last 3 years, so the model is always judged on "future" commits.

## Environment

`requirements.txt` pins scikit-learn 1.4.2 and its dependencies to match SageMaker's
scikit-learn 1.4-2 container, so the saved model loads identically in the cloud.

```bash
cd risk-model
uv venv .venv-sm --python 3.11
source .venv-sm/bin/activate
uv pip install -r requirements.txt
git clone https://github.com/harsh8398/apachejit.git
```

## Train

```bash
python train_risk_model.py --data-dir apachejit/dataset
```

Compares the lines-added heuristic with logistic regression and gradient-boosted trees, with and
without repository-history features, and saves the best diff-only model to `model/`.

## Serve on SageMaker (serverless)

```bash
aws login
python deploy_to_sagemaker.py local    # run the inference code locally first
python deploy_to_sagemaker.py deploy   # private S3 bucket, least-privilege role, serverless endpoint
python deploy_to_sagemaker.py test     # call the live endpoint
python deploy_to_sagemaker.py delete   # tear everything down
```
