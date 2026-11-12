"""
Deploy the trained risk model to a SageMaker serverless endpoint, test it, or tear it down.

    python deploy_to_sagemaker.py local    # test the inference code on your laptop first
    python deploy_to_sagemaker.py deploy   # package, upload, create role + serverless endpoint
    python deploy_to_sagemaker.py test     # call the live endpoint
    python deploy_to_sagemaker.py delete   # remove the endpoint, its configs and models

Serverless endpoints have no idle cost: you pay only while a request is being processed.
Uses your `aws login` session; nothing secret is stored in this file.
"""

import importlib.util
import json
import sys
import tarfile
import time
from pathlib import Path

import boto3
from botocore.exceptions import ClientError

HERE = Path(__file__).parent
MODEL_FILE = HERE / "model" / "model.joblib"
INFERENCE_FILE = HERE / "sagemaker" / "inference.py"
BUILD_DIR = HERE / "build"

ENDPOINT_NAME = "code-review-risk"
ROLE_NAME = "CodeReviewAgentSageMakerRole"
# SageMaker's official scikit-learn 1.4-2 image (CPU). Must match the version we trained with.
SKLEARN_IMAGES = {
    "us-east-1": "683313688378.dkr.ecr.us-east-1.amazonaws.com/sagemaker-scikit-learn:1.4-2-cpu-py3",
    "us-east-2": "257758044811.dkr.ecr.us-east-2.amazonaws.com/sagemaker-scikit-learn:1.4-2-cpu-py3",
}

SAMPLES = [
    {"la": 4, "ld": 1, "nf": 1, "nd": 1, "ns": 1, "ent": 0.0},        # tiny one-file fix
    {"la": 1047, "ld": 126, "nf": 32, "nd": 13, "ns": 4, "ent": 4.14},  # big change spread across files
]


def session():
    s = boto3.session.Session()
    if s.region_name not in SKLEARN_IMAGES:
        sys.exit(f"Region is {s.region_name!r}. Run: aws configure set region <your project's region>")
    return s


def local_test():
    """Run inference.py exactly as SageMaker would, but on this laptop."""
    spec = importlib.util.spec_from_file_location("inference", INFERENCE_FILE)
    inference = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(inference)
    model = inference.model_fn(str(MODEL_FILE.parent))
    features = inference.input_fn(json.dumps({"instances": SAMPLES}), "application/json")
    body, _ = inference.output_fn(inference.predict_fn(features, model), "application/json")
    print("Local prediction:", body)


def package() -> Path:
    """model.tar.gz = model.joblib at the root + code/inference.py (SageMaker's expected layout)."""
    if not MODEL_FILE.exists():
        sys.exit("No model/model.joblib yet. Run train_risk_model.py first.")
    BUILD_DIR.mkdir(exist_ok=True)
    archive = BUILD_DIR / "model.tar.gz"
    with tarfile.open(archive, "w:gz") as tar:
        tar.add(MODEL_FILE, arcname="model.joblib")
        tar.add(INFERENCE_FILE, arcname="code/inference.py")
    return archive


def ensure_bucket(s3, account: str, region: str) -> str:
    bucket = f"code-review-agent-{account}-{region}"
    try:
        s3.head_bucket(Bucket=bucket)
    except ClientError:
        if region == "us-east-1":
            s3.create_bucket(Bucket=bucket)
        else:
            s3.create_bucket(Bucket=bucket, CreateBucketConfiguration={"LocationConstraint": region})
        s3.put_public_access_block(Bucket=bucket, PublicAccessBlockConfiguration={
            "BlockPublicAcls": True, "IgnorePublicAcls": True,
            "BlockPublicPolicy": True, "RestrictPublicBuckets": True})
        print(f"Created private bucket {bucket}")
    return bucket


def ensure_role(iam, bucket: str) -> str:
    """The identity SageMaker itself uses. Least privilege: read this bucket, pull the image, write logs."""
    trust = {"Version": "2012-10-17", "Statement": [{
        "Effect": "Allow", "Principal": {"Service": "sagemaker.amazonaws.com"}, "Action": "sts:AssumeRole"}]}
    permissions = {"Version": "2012-10-17", "Statement": [
        {"Effect": "Allow", "Action": "s3:GetObject", "Resource": f"arn:aws:s3:::{bucket}/*"},
        {"Effect": "Allow", "Action": ["ecr:GetAuthorizationToken", "ecr:BatchGetImage",
                                       "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"],
         "Resource": "*"},
        {"Effect": "Allow", "Action": ["logs:CreateLogGroup", "logs:CreateLogStream",
                                       "logs:PutLogEvents", "logs:DescribeLogStreams"],
         "Resource": "arn:aws:logs:*:*:log-group:/aws/sagemaker/*"},
        {"Effect": "Allow", "Action": "cloudwatch:PutMetricData", "Resource": "*"},
    ]}
    try:
        arn = iam.get_role(RoleName=ROLE_NAME)["Role"]["Arn"]
    except iam.exceptions.NoSuchEntityException:
        arn = iam.create_role(RoleName=ROLE_NAME, AssumeRolePolicyDocument=json.dumps(trust),
                              Description="Lets SageMaker serve the code-review risk model")["Role"]["Arn"]
        print(f"Created role {ROLE_NAME}; waiting for it to propagate...")
        time.sleep(15)
    iam.put_role_policy(RoleName=ROLE_NAME, PolicyName="serve-risk-model",
                        PolicyDocument=json.dumps(permissions))
    return arn


def deploy():
    s = session()
    region = s.region_name
    account = s.client("sts").get_caller_identity()["Account"]
    s3, iam, sm = s.client("s3"), s.client("iam"), s.client("sagemaker")

    archive = package()
    bucket = ensure_bucket(s3, account, region)
    version = time.strftime("%Y%m%d-%H%M%S")
    key = f"risk-model/{version}/model.tar.gz"
    s3.upload_file(str(archive), bucket, key)
    print(f"Uploaded s3://{bucket}/{key}")

    role_arn = ensure_role(iam, bucket)
    name = f"{ENDPOINT_NAME}-{version}"
    sm.create_model(ModelName=name, ExecutionRoleArn=role_arn, PrimaryContainer={
        "Image": SKLEARN_IMAGES[region],
        "ModelDataUrl": f"s3://{bucket}/{key}",
        "Environment": {"SAGEMAKER_PROGRAM": "inference.py",
                        "SAGEMAKER_SUBMIT_DIRECTORY": "/opt/ml/model/code",
                        # Serverless runs as a non-root user, so the container's pip install of our
                        # script lands in a user folder Python doesn't search. Import it directly instead.
                        "PYTHONPATH": "/opt/ml/model/code"},
    })
    sm.create_endpoint_config(EndpointConfigName=name, ProductionVariants=[{
        "VariantName": "AllTraffic", "ModelName": name,
        "ServerlessConfig": {"MemorySizeInMB": 2048, "MaxConcurrency": 1},
    }])

    try:
        sm.describe_endpoint(EndpointName=ENDPOINT_NAME)
        sm.update_endpoint(EndpointName=ENDPOINT_NAME, EndpointConfigName=name)
        print("Updating existing endpoint...")
    except ClientError:
        sm.create_endpoint(EndpointName=ENDPOINT_NAME, EndpointConfigName=name)
        print("Creating endpoint (usually 3-8 minutes)...")

    try:
        sm.get_waiter("endpoint_in_service").wait(EndpointName=ENDPOINT_NAME,
                                                  WaiterConfig={"Delay": 30, "MaxAttempts": 40})
    except Exception:
        reason = sm.describe_endpoint(EndpointName=ENDPOINT_NAME).get("FailureReason", "unknown")
        sys.exit(f"Endpoint did not become ready. Reason: {reason}")
    print(f"Endpoint '{ENDPOINT_NAME}' is InService.")
    test()


def test():
    runtime = session().client("sagemaker-runtime")
    response = runtime.invoke_endpoint(EndpointName=ENDPOINT_NAME, ContentType="application/json",
                                       Body=json.dumps({"instances": SAMPLES}))
    print("Live prediction:", response["Body"].read().decode())


def delete():
    sm = session().client("sagemaker")
    try:
        sm.delete_endpoint(EndpointName=ENDPOINT_NAME)
        print(f"Deleted endpoint {ENDPOINT_NAME}")
    except ClientError as e:
        print(f"No endpoint to delete ({e.response['Error']['Code']})")
    for config in sm.list_endpoint_configs(NameContains=ENDPOINT_NAME)["EndpointConfigs"]:
        sm.delete_endpoint_config(EndpointConfigName=config["EndpointConfigName"])
    for model in sm.list_models(NameContains=ENDPOINT_NAME)["Models"]:
        sm.delete_model(ModelName=model["ModelName"])
    print("Deleted endpoint configs and models.")


if __name__ == "__main__":
    commands = {"local": local_test, "deploy": deploy, "test": test, "delete": delete}
    if len(sys.argv) != 2 or sys.argv[1] not in commands:
        sys.exit(f"Usage: python {Path(__file__).name} [{'|'.join(commands)}]")
    commands[sys.argv[1]]()
