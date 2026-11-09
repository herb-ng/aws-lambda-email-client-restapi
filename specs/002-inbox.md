# List inbox emails

## Status
Approved

## Goal
A client can call `GET /inbox` to retrieve all received emails as a JSON list.

## Background
Amazon SES receives mail for `herbertng.com` through the `StoreInboundEmailsToS3` rule and stores each message in the `herbertng-dns-ses-stack-inboundmailbucket-qelyk2vxjy3s` bucket under the `inbox/` prefix.

Each object is one raw email message (MIME). Object names are random IDs and the objects have no useful metadata, so every field in the response must be read from the message itself.

## Decisions
- **No authentication:** this endpoint uses the existing `AuthType: NONE` Function URL for now. Authentication is covered separately by `001-api-authentication.md`.
- **Accepted risk:** anyone who has the URL can read every stored email, including its sender and content.
- **Email body:** `content` is the plain text body. When an email has no plain text body, `content` is the HTML source, unmodified, so a client that displays it must sanitize it.
- **Large inboxes:** a Function URL response is limited to 6 MB and the function has a 15 second timeout. When the inbox can't be returned within those limits, the API returns an error rather than a partial list. Pagination is left for a later spec.

## Requirements
- `GET /inbox` returns every email stored under the `inbox/` prefix, across all pages of S3 list results.
- Objects whose key ends in `/` are folder markers and are ignored.
- Emails are sorted by `sentAt`, newest first. Emails with no valid date come last.
- Each email in the response contains exactly these fields:

  | Field | Type | Source |
  |---|---|---|
  | `from` | string or null | The `From` header, including any display name |
  | `title` | string or null | The `Subject` header |
  | `content` | string or null | The message body |
  | `sentAt` | string or null | The `Date` header, as ISO 8601 in UTC |

- Encoded headers and bodies are decoded to readable text, including base64, quoted-printable and non-UTF-8 character sets.
- Attachments are not returned.
- A field that is missing or can't be parsed is returned as `null`, and the rest of the email is still returned.
- The bucket name comes from configuration, not application code.
- The function has read-only access, limited to the `inbox/` prefix of this bucket.
- Email contents are never written to logs.

## Response format

```json
{
  "emails": [
    {
      "from": "Jane Doe <jane@example.com>",
      "title": "Hello",
      "content": "Hi there.",
      "sentAt": "2026-11-09T01:16:06Z"
    }
  ]
}
```

Errors use this format:

```json
{ "message": "Could not read the inbox" }
```

## Acceptance criteria
- Given stored emails, when a client calls `GET /inbox`, then it returns `200` and every email with `from`, `title`, `content` and `sentAt`.
- Given an empty inbox, when a client calls `GET /inbox`, then it returns `200` and `{"emails": []}`.
- Given more than 1,000 stored emails, when a client calls `GET /inbox`, then the list includes emails from every page of S3 results.
- Given emails with different dates, when a client calls `GET /inbox`, then they are sorted newest first, with undated emails last.
- Given an email with an encoded subject and body, when it is returned, then `title` and `content` are readable text.
- Given a multipart email with attachments, when it is returned, then `content` contains the message body and no attachment data.
- Given an email with a missing `Date` header, when it is returned, then `sentAt` is `null` and its other fields are present.
- Given an email with both plain text and HTML bodies, when it is returned, then `content` is the plain text body.
- Given an email with only an HTML body, when it is returned, then `content` is the HTML source.
- Given S3 can't be read, when a client calls `GET /inbox`, then it returns `500` with the JSON error and no internal details.
- Given an inbox too large to return within the size or time limit, when a client calls `GET /inbox`, then it returns `500` with a JSON error saying the inbox is too large, and no partial list.
- Given another HTTP method, when a client calls `/inbox`, then it returns `405`.
- Given the deployed function, when `GET /inbox` is called through the Function URL, then it returns the emails stored in the bucket.

## Out of scope
- Authentication
- Pagination, search and filtering
- Getting a single email by ID
- Attachments and additional fields such as recipients
- Deleting emails, marking them as read, or moving them between folders
- Changes to how SES stores inbound mail

## Implementation notes
- **Permissions:** the execution role needs `s3:ListBucket` limited to the `inbox/` prefix, and `s3:GetObject` on `inbox/*`. The `email-client-cicd` stack owns this as a managed policy, and `DeployRole` can attach only that policy and `AWSLambdaBasicExecutionRole`. It can't write inline policies, so a change to `template.yaml` alone can't widen the function's access.
- **Deploy order:** redeploy the `email-client-cicd` stack before pushing the `template.yaml` change, so the policy and the pipeline's new parameters exist first.
- **Routing:** the handler has no routing today. Other paths keep their current status response.
- **API contract:** update `openapi.yaml` in the same commit as the code.
