# API authentication

## Status
Draft

## Goal
Only authorized clients can call the email client API, so that email features can be added without exposing them publicly.

## Background
The Lambda Function URL uses `AuthType: NONE`, and the handler returns `200` for every path and method. Anyone with the URL can call it, and each call is billed to the AWS account.

## Requirements
- Every API request must present valid credentials, except any public routes listed in this spec.
- Requests with missing or invalid credentials are rejected before any email logic runs.
- Rejected requests get a JSON error response that does not reveal why the credentials failed.
- Credentials are never stored in the repository, written to logs, or returned in responses.
- Credentials can be replaced without changing application code.

## Acceptance criteria
- Given no credentials, when a client calls the API, then it is rejected with `401`.
- Given invalid credentials, when a client calls the API, then it is rejected with `401`.
- Given valid credentials, when a client calls the API, then it returns `200` and the existing status message.
- Given a rejected request, when the response is returned, then it does not reveal whether credentials were missing or invalid.
- Given any request, when it is logged, then the logs do not contain the credentials.

## Out of scope
- User sign-up, login and password management
- Per-user or role-based permissions
- Rate limiting and throttling
- Email features

## Open questions
1. **Which auth method should the API use?** The answer depends on who the clients are.
   - **API key in a request header, checked by the Lambda:** simplest for one trusted client or a personal project, with the key kept in Secrets Manager or Parameter Store. Recommended unless a question below rules it out.
   - **Function URL `AWS_IAM` auth:** no auth code to write, but clients must sign requests with AWS credentials, which suits backend or AWS-based clients. Lambda rejects bad requests itself, with `403` rather than `401`, so the acceptance criteria would need to change.
   - **Amazon Cognito with API Gateway:** best when real users sign in from a web or mobile app, but it adds the most infrastructure.
2. **Who will call this API?** For example: only you, a backend service, or a web or mobile app with its own users.
3. **Should a `/health` route stay public?** If so, this spec needs to list it as a public route.

## Implementation notes
Any new AWS resources or permissions must also be allowed by `DeployRole` in `infrastructure/pipeline.yaml`, or the pipeline's Deploy stage will fail.
