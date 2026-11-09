# Swagger UI page

## Status
Approved

## Goal
A developer can open a web page to read the API documentation and try the API with the API key.

## Background
`openapi.yaml` describes the API, but nothing serves it, so viewing it means pasting it into an external tool.

Spec `001-api-authentication.md` requires a key on every path, sent in the `x-api-key` header. A browser can't add a header when it opens a page, so a page protected this way can't be opened directly.

## Decisions
- **Hosting:** the Lambda function serves the page from the existing Function URL. The page and the API share an origin, so no CORS configuration is needed.
- **Swagger UI files:** a pinned version of Swagger UI is bundled in the JAR. The page loads no third-party scripts, because users type the API key into it.
- **Public docs:** the docs routes don't need the API key, which changes the "no public routes" decision in `001-api-authentication.md`. Anyone with the URL can see the API's routes and response formats, but no emails or keys, and they still need the key to call the API.
- **Memory:** the function stays at 512 MB.
- **Try it out:** enabled. It displays real emails in the browser for anyone holding the key, which is the same access the key already gives.

## Requirements
- `GET /docs` returns the Swagger UI page.
- `GET /openapi.yaml` returns the repository's `openapi.yaml`, so the docs always match the deployed code.
- The Swagger UI script and stylesheet are served from fixed paths under `/docs/`. Only a fixed list of files can be served, never a file chosen by the request path.
- These `GET` routes contain no email data or secrets, and they are the only routes that don't need the API key. Any other method on them requires the key.
- All other paths still require the key, including `/inbox`.
- The page's **Authorize** button accepts the API key, and **Try it out** sends it in the `x-api-key` header.
- The page does not save the key in the browser, so it's gone when the page is closed or reloaded.
- The page never includes the key in a URL, and the function doesn't log it.
- The page responses send a `Content-Security-Policy` header that only allows scripts and styles from the same origin, and API requests to the same origin.
- `openapi.yaml` sets a relative server URL, so **Try it out** works without naming the function's URL.
- `openapi.yaml` documents these routes as not needing a key.

## Acceptance criteria
- Given no key, when a browser opens `/docs`, then it returns `200` and the page loads with no failed requests.
- Given no key, when a client calls `GET /openapi.yaml`, then it returns `200` and the same content as the repository's file.
- Given no key, when a client calls `GET /docs/<a listed file>`, then it returns `200` with the correct content type.
- Given no key, when a client calls `GET /docs/<anything else>`, including `../` paths, then it returns `401`.
- Given no key, when a client calls `POST /docs`, then it returns `401`.
- Given no key, when a client calls `GET /inbox`, then it still returns `401`.
- Given the page with no key entered, when **Try it out** calls `GET /inbox`, then it shows `401`.
- Given the page with a valid key entered, when **Try it out** calls `GET /inbox`, then it shows `200` and the emails.
- Given a key entered on the page, when the page is reloaded, then the key is no longer present.
- Given the page, when it loads, then the browser makes no requests to other origins.

## Out of scope
- Logging in to view the page
- A custom domain
- Editing the API from the page
- Documentation versions
- Caching or compression of the page files

## Implementation notes
- **Related documents:** in the same commit as the code, update the "no public routes" requirement in `001-api-authentication.md`, and update `openapi.yaml`.
- **Packaging:** `openapi.yaml` lives at the repository root, so the build must copy it into the JAR.
- **Size:** the bundled Swagger UI adds to the JAR, which is already about 19 MB. A cold start on `GET /inbox` took 9.9 seconds before this work, against the 15 second timeout, so measure it again after deploying.
- **Response limits:** the Swagger UI script must fit within the 6 MB response limit.
- **Permissions:** no new AWS resources or permissions are needed, so the `email-client-cicd` stack doesn't need a redeploy.
- **Spec order:** finish verifying `001-api-authentication.md` with a valid key before this work goes live.
