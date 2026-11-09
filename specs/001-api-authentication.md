# API authentication

## Status
Implemented

## Goal
Only clients with the API key can call the email client API, so that stored email is no longer public.

## Background
The Lambda Function URL uses `AuthType: NONE`, so anyone with the URL can call the function, and `GET /inbox` returns every stored email. Each call is also billed to the AWS account.

## Decisions
- **Auth method:** a single API key, checked by the Lambda function.
- **Key storage:** the key is stored as a `SecureString` parameter named `/email-client-restapi/api-key` in AWS Systems Manager Parameter Store.
- **Key creation:** CloudFormation can't create `SecureString` parameters, so the key is generated and stored with a manual command, outside any stack.
- **Function URL:** stays `AuthType: NONE`, because the function does the checking. Rejected requests still invoke the function and are billed.

## Requirements
- Clients send the key in an `x-api-key` request header. A key anywhere else, such as a query parameter, is ignored.
- Every path and method requires a valid key. The only public routes are the `GET` docs routes listed in `003-swagger-ui.md`.
- The header name is matched case-insensitively.
- Authentication runs before any other request handling, including reading email and method checks.
- Rejected requests get the same JSON error whether the key is missing or wrong.
- Keys are compared in constant time.
- If the key can't be read from Parameter Store, requests are rejected. The API never falls back to allowing access.
- The key is randomly generated with at least 256 bits of entropy, and is never stored in the repository or CloudFormation templates, written to logs, or returned in responses.
- The key can be replaced in Parameter Store without changing code or redeploying. The function picks up a new key within 5 minutes.
- The function has read access to this one parameter only.

## Response format
A rejected request gets `401`:

```json
{ "message": "Unauthorized" }
```

If the key can't be read from Parameter Store, the request gets `500`:

```json
{ "message": "Could not verify the request" }
```

## Acceptance criteria
- Given no `x-api-key` header, when a client calls any path, then it returns `401` and the error.
- Given a wrong key, when a client calls any path, then it returns `401` and the same error.
- Given the key only in a query parameter, when a client calls any path, then it returns `401`.
- Given a valid key, when a client calls `GET /inbox`, then it returns `200` and the emails.
- Given a valid key, when a client calls `GET /`, then it returns `200` and the status message.
- Given a valid key, when a client calls `POST /inbox`, then it returns `405`.
- Given no key, when a client calls `POST /inbox`, then it returns `401`.
- Given a rejected request, when it is handled, then no email is read from S3.
- Given Parameter Store can't be read, when a client calls any path, then it returns `500` and no email is read from S3.
- Given a replaced key, when 5 minutes have passed, then the new key is accepted and the old key is rejected.
- Given any request, when it is logged, then the logs do not contain the key.
- Given the deployed function, when `GET /inbox` is called through the Function URL without a key, then it returns `401`, and with the key it returns `200`.

## Out of scope
- Multiple keys or per-client keys
- User sign-up, login and password management
- Per-user or role-based permissions
- Automatic key rotation
- Accepting the old and new keys at the same time during a key change
- Rate limiting and throttling, including limits on repeated wrong keys
- CORS and browser clients

## Implementation notes
- **Permissions:** the `email-client-cicd` stack defines a managed policy that allows `ssm:GetParameter` on this parameter only. This follows the same approach as `InboxReadPolicy`, so a push to `main` can't widen the function's access. The parameter uses the default `aws/ssm` KMS key. Confirm on the first deploy that the function can decrypt it without a separate `kms:Decrypt` permission.
- **Deploy order:** create the parameter first, then redeploy the `email-client-cicd` stack with `DeployRole` allowed to attach the new policy, then push the code.
- **Creating or replacing the key:** this command generates the key without printing it. Add `--overwrite` when replacing an existing key.

  ```bash
  aws ssm put-parameter --name /email-client-restapi/api-key --type SecureString --value "$(openssl rand -hex 32)"
  ```

- **Going live:** the push makes the API reject all requests without the key, so existing clients need the key from that point on.
- **Getting the key:** run `aws ssm get-parameter --name /email-client-restapi/api-key --with-decryption` with AWS credentials. The key is never printed by the build, the pipeline or the function.
- **Cold starts:** reading the parameter adds to cold start time, which is already about 8.6 seconds.
- **Related documents:** in the same commit as the code, add the security scheme and `401` responses to `openapi.yaml`, and update the no-auth decision in `specs/002-inbox.md`.
