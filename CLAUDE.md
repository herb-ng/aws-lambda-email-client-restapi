# Email client REST API

Java 25 AWS Lambda behind a Function URL, deployed by CodePipeline. See `README.md` for setup and CI/CD details.

## Spec-driven workflow

Specs come before code:

1. Read the relevant spec in `specs/` before changing behavior. If none exists, write one and get it approved first.
2. Don't implement a spec until its status is `Approved` and it has no open questions. If a requirement is unclear, ask rather than guess.
3. Write tests from the acceptance criteria first, with at least one test per criterion.
4. Change `openapi.yaml` in the same commit as any change to the API's behavior.
5. If the code needs to differ from the spec, update the spec and get that change approved. Don't let them drift apart.
6. Set the spec's status to `Implemented` when the work is done.

Spec files are named `specs/NNN-short-name.md`. Status is one of `Draft`, `Approved` or `Implemented`.

## Commands

- Build and run all tests: `mvn clean verify`

## Deployment

- Every push to `main` deploys to AWS with no approval step. Don't push unfinished work to `main`.
- The pipeline does not update itself. After changing `infrastructure/pipeline.yaml`, redeploy the `email-client-cicd` stack manually.
- `DeployRole` in `infrastructure/pipeline.yaml` only allows this stack's Lambda function and IAM role, and can only attach `AWSLambdaBasicExecutionRole` and the stack's `InboxReadPolicy`. Before adding resources or permissions to `template.yaml`, update that role and redeploy the `email-client-cicd` stack, or the Deploy stage will fail.
