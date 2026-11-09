# AWS Lambda Email Client

A Java 25 AWS Lambda project with a public Lambda Function URL.

## Requirements

- JDK 25
- Maven 3.9+
- AWS SAM CLI (for local invocation and deployment)

## Build

```bash
mvn clean verify
```

The shaded deployment artifact is written to `target/email-client-restapi-1.0.0.jar`.

## Run locally

```bash
sam build
sam local invoke EmailClientFunction
```

To deploy and create the Function URL:

```bash
curl http://127.0.0.1:3000/health
```

## Deploy

```bash
sam deploy --guided
```

After deployment, use the `FunctionUrl` CloudFormation output to call the function:

```bash
curl -H "x-api-key: <api-key>" <FunctionUrl>
```

Every request needs the API key, which is stored as the `/email-client-restapi/api-key` SSM `SecureString` parameter. Read it with:

```bash
aws ssm get-parameter --name /email-client-restapi/api-key --with-decryption --query Parameter.Value --output text
```

The Lambda handler is `com.example.email.EmailClientHandler`.

## CI/CD

`infrastructure/pipeline.yaml` defines a CodePipeline that runs on every push to `main`:

1. **Source:** fetches the commit through a CodeConnections GitHub connection.
2. **Build:** runs `mvn verify` in CodeBuild and uploads the JAR to `email-client-restapi/<commit>.jar` in the artifact bucket.
3. **Deploy:** creates or updates the `email-client-restapi` stack from `template.yaml`, using that JAR.

Create the pipeline once, after pushing this file to `main`, because creating it starts a run straight away:

```bash
aws cloudformation deploy \
  --stack-name email-client-cicd \
  --template-file infrastructure/pipeline.yaml \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    ConnectionArn=<connection-arn> \
    ArtifactBucketName=<artifact-bucket>
```

Don't start the pipeline stack's name with `email-client-restapi-`. The deploy role can manage IAM roles with that prefix, which would include the pipeline's own roles.

The deploy role only allows Lambda functions and roles for this stack, and the only managed policies it can attach are `AWSLambdaBasicExecutionRole` and the pipeline stack's inbox and API key read policies. Update it in `infrastructure/pipeline.yaml` when `template.yaml` needs other resources or permissions.
