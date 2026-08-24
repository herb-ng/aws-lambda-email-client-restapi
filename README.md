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
curl <FunctionUrl>
```

The Lambda handler is `com.example.email.EmailClientHandler`.
