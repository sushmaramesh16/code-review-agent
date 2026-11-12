# Code Review Agent

An AI agent that reviews Java pull requests. It decides which files look risky,
investigates them with tools, and reports findings tied to exact lines, along with
a step-by-step trace of everything it did.

**Status: feature-complete.** Spring Boot API, agent loop (rule-based or a local LLM), real GitHub
pull requests, and a trained bug-risk model served from a SageMaker serverless endpoint.

## Run it

You need **Java 17+** (`java -version`) and **Maven** (`mvn -v`).
Or open the folder in IntelliJ IDEA, which handles Maven for you.

```bash
mvn test              # run the tests
mvn spring-boot:run   # start the API on http://localhost:8080
```

## Try it

```bash
# See the demo pull request
curl http://localhost:8080/pull-requests

# Ask the agent to review it
curl -i -X POST http://localhost:8080/reviews \
  -H "Content-Type: application/json" \
  -d '{"prNumber": 1}'

# Review a real public GitHub pull request (only .java files are reviewed)
curl -s -X POST http://localhost:8080/reviews \
  -H "Content-Type: application/json" \
  -d '{"owner": "spring-projects", "repo": "spring-petclinic", "prNumber": 123}'

# Fetch a saved review (use the id from the response)
curl http://localhost:8080/reviews/<id>
```

| Request | Status |
|---|---|
| Valid PR number | 201 Created, with a `Location` header |
| Missing/invalid `prNumber`, or owner without repo | 400 Bad Request |
| PR or review that doesn't exist | 404 Not Found |
| GitHub error or rate limit | 502 Bad Gateway |
| LLM unreachable (Ollama mode) | 503 Service Unavailable |

Without a token, GitHub allows 60 requests an hour. For more, create a token on GitHub
(Settings → Developer settings → Personal access tokens) and run `export GITHUB_TOKEN=...`
before starting the app.

## Two brains

- `agent.planner=rule` (default): a fixed strategy. No install needed. Used by the tests.
- `agent.planner=ollama`: a real LLM decides every step, free and local.

```bash
# Install Ollama from ollama.com, then:
ollama pull qwen2.5:7b
mvn spring-boot:run -Dspring-boot.run.arguments=--agent.planner=ollama
```

## Trained risk model on SageMaker

The `assess_change_risk` tool asks a gradient-boosted model, trained on ~75,000 real Apache Java
commits (ApacheJIT), how likely the whole pull request is to introduce a bug. On commits from later
years than it trained on, reviewing the riskiest 20% of changes catches about half of all
bug-inducing commits, versus about 22% for a lines-added heuristic. High-risk changes (0.7 or more)
get every file checked. See `risk-model/` for training and deployment.

```bash
aws login
mvn spring-boot:run -Dspring-boot.run.arguments=--risk.model=sagemaker
```

If SageMaker can't be reached (expired session, endpoint deleted), the app falls back to the
heuristic and labels the result `heuristic fallback (SageMaker unavailable)` instead of failing.

## How it's organized

```
api/      HTTP layer: controllers, request validation, error handling
agent/    AgentService (the loop), Planner (the brain), ReviewContext (shared state)
tools/    What the agent may do: list files, assess change risk, read diffs/files, rank files, check pitfalls
risk/     Change-risk models: heuristic, or the trained model on SageMaker (with fallback)
source/   Where pull requests come from: built-in demo data, or the GitHub REST API
model/    Plain data records
```

The pitfall checker reads each diff's hunk headers (`@@ -8,3 +8,17 @@`) to work out exactly
which lines the pull request added, and only reports issues on those lines. Rules: String `==`,
null-unsafe `equals`, empty catch, overly broad catch, unclosed streams, `printStackTrace()`,
and `System.out` logging.

Every review has two summaries: `summary` is computed by Java from the recorded findings,
so its counts are always correct, and `agentNotes` holds the planner's own words. In testing,
the LLM planner once miscounted findings in its notes, which is exactly why facts never come from it.

The planner only **proposes** actions. `AgentService` validates the tool name,
runs it, records the result, and stops after `agent.max-steps`.

## Roadmap

1. ✅ Spring Boot API + agent loop on demo data
2. ✅ Read real pull requests from GitHub
3. ✅ Flag only lines the PR added, 7 Java pitfall rules, budget-based file selection
4. Replace the risk heuristic with a model on a SageMaker serverless endpoint
5. Lambda webhook, S3 for diffs and reports, DynamoDB for reviews
6. Evaluate on real historical bugs (Defects4J), demo video
