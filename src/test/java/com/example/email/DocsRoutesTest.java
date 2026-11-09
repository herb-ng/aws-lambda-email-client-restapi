package com.example.email;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocsRoutesTest {

    private static final String API_KEY = "test-api-key";
    private static final String UNAUTHORIZED_BODY = "{\"message\":\"Unauthorized\"}";

    private final CountingStore store = new CountingStore();

    @Test
    void pageIsServedWithoutAKey() {
        APIGatewayV2HTTPResponse response = get("/docs");

        assertEquals(200, response.getStatusCode());
        assertEquals("text/html; charset=utf-8", response.getHeaders().get("Content-Type"));
        assertTrue(response.getBody().contains("id=\"swagger-ui\""));
    }

    @Test
    void docsRoutesDoNotNeedTheKeyToBeReadable() {
        ApiKeyProvider failing = () -> {
            throw new IllegalStateException("parameter store is unavailable");
        };

        APIGatewayV2HTTPResponse response = new EmailClientHandler(store, 6_000_000, failing)
            .handleRequest(request("GET", "/docs"), null);

        assertEquals(200, response.getStatusCode());
    }

    @Test
    void openApiFileMatchesTheRepository() throws Exception {
        APIGatewayV2HTTPResponse response = get("/openapi.yaml");

        assertEquals(200, response.getStatusCode());
        assertEquals("application/yaml; charset=utf-8", response.getHeaders().get("Content-Type"));
        assertEquals(Files.readString(Path.of("openapi.yaml")), response.getBody());
    }

    @Test
    void listedFilesHaveTheCorrectContentType() {
        Map<String, String> files = Map.of(
            "/docs/swagger-ui-bundle.js", "text/javascript; charset=utf-8",
            "/docs/swagger-initializer.js", "text/javascript; charset=utf-8",
            "/docs/swagger-ui.css", "text/css; charset=utf-8");

        files.forEach((path, contentType) -> {
            APIGatewayV2HTTPResponse response = get(path);

            assertEquals(200, response.getStatusCode(), path);
            assertEquals(contentType, response.getHeaders().get("Content-Type"), path);
            assertFalse(response.getBody().isEmpty(), path);
        });
    }

    @Test
    void everyDocsResponseSendsTheSecurityHeaders() {
        for (String path : List.of("/docs", "/docs/swagger-ui-bundle.js", "/docs/swagger-initializer.js", "/docs/swagger-ui.css", "/openapi.yaml")) {
            Map<String, String> headers = get(path).getHeaders();

            String policy = headers.get("Content-Security-Policy");
            assertTrue(policy.contains("default-src 'none'"), path);
            assertTrue(policy.contains("script-src 'self'"), path);
            assertTrue(policy.contains("connect-src 'self'"), path);
            assertEquals("nosniff", headers.get("X-Content-Type-Options"), path);
        }
    }

    @Test
    void unlistedPathsNeedTheKey() {
        List<String> paths = List.of(
            "/docs/", "/docs/other.js", "/docs/index.html", "/docs/../openapi.yaml", "/docs/..%2fopenapi.yaml",
            "/docs/META-INF/MANIFEST.MF", "/docs/com/example/email/EmailClientHandler.class", "/DOCS");

        for (String path : paths) {
            APIGatewayV2HTTPResponse response = get(path);

            assertEquals(401, response.getStatusCode(), path);
            assertEquals(UNAUTHORIZED_BODY, response.getBody(), path);
        }
    }

    @Test
    void otherMethodsOnDocsRoutesNeedTheKey() {
        for (String method : List.of("POST", "PUT", "DELETE", "HEAD", "OPTIONS")) {
            for (String path : List.of("/docs", "/openapi.yaml", "/docs/swagger-ui.css")) {
                APIGatewayV2HTTPResponse response = handler().handleRequest(request(method, path), null);

                assertEquals(401, response.getStatusCode(), method + " " + path);
            }
        }
    }

    @Test
    void inboxStillNeedsTheKeyAndDocsNeverReadEmail() {
        get("/docs");
        get("/openapi.yaml");
        assertEquals(0, store.reads);

        APIGatewayV2HTTPResponse response = get("/inbox");

        assertEquals(401, response.getStatusCode());
        assertEquals(0, store.reads);
    }

    @Test
    void pageFilesReferenceNoOtherOrigins() {
        for (String path : List.of("/docs", "/docs/swagger-initializer.js")) {
            String body = get(path).getBody();

            assertFalse(body.contains("http://"), path);
            assertFalse(body.contains("https://"), path);
            assertFalse(body.contains("<script>"), path);
        }
    }

    @Test
    void initializerDisablesTheValidatorAndDoesNotSaveTheKey() {
        String body = get("/docs/swagger-initializer.js").getBody();

        assertTrue(body.contains("validatorUrl: null"));
        assertTrue(body.contains("persistAuthorization: false"));
        assertTrue(body.contains("url: '/openapi.yaml'"));
    }

    @Test
    void everyFileFitsTheResponseLimitOnceEscaped() throws Exception {
        ObjectMapper json = new ObjectMapper();

        for (String path : List.of("/docs", "/docs/swagger-ui-bundle.js", "/docs/swagger-initializer.js", "/docs/swagger-ui.css", "/openapi.yaml")) {
            assertTrue(json.writeValueAsBytes(get(path).getBody()).length < 6_000_000, path);
        }
    }

    private APIGatewayV2HTTPResponse get(String path) {
        return handler().handleRequest(request("GET", path), null);
    }

    private EmailClientHandler handler() {
        return new EmailClientHandler(store, 6_000_000, () -> API_KEY);
    }

    private static APIGatewayV2HTTPEvent request(String method, String path) {
        return APIGatewayV2HTTPEvent.builder()
            .withRawPath(path)
            .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder().withMethod(method).build())
                .build())
            .build();
    }

    private static class CountingStore implements EmailStore {
        int reads;

        @Override
        public List<String> listKeys() {
            reads++;
            return List.of();
        }

        @Override
        public InputStream open(String key) {
            reads++;
            return InputStream.nullInputStream();
        }
    }
}
