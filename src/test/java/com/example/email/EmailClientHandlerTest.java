package com.example.email;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailClientHandlerTest {

    private static final String STATUS_BODY = "{\"message\":\"Email client API is running\"}";
    private static final String READ_ERROR_BODY = "{\"message\":\"Could not read the inbox\"}";
    private static final String TOO_LARGE_BODY = "{\"message\":\"The inbox is too large to return\"}";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void returnsHealthyApiResponse() {
        APIGatewayV2HTTPResponse response = new EmailClientHandler()
            .handleRequest(new APIGatewayV2HTTPEvent(), null);

        assertEquals(200, response.getStatusCode());
        assertEquals(STATUS_BODY, response.getBody());
    }

    @Test
    void otherPathsKeepTheStatusResponse() {
        APIGatewayV2HTTPResponse response = handler(new FakeStore()).handleRequest(request("GET", "/other"), null);

        assertEquals(200, response.getStatusCode());
        assertEquals(STATUS_BODY, response.getBody());
    }

    @Test
    void returnsEveryEmailWithExactlyTheSpecifiedFields() throws Exception {
        FakeStore store = new FakeStore()
            .with("inbox/a", email("Jane Doe <jane@example.com>", "Hello", "Mon, 9 Nov 2026 12:16:06 +1100", "Hi there."))
            .with("inbox/b", email("sam@example.com", "Second", "Sun, 8 Nov 2026 12:00:00 +0000", "More."));

        APIGatewayV2HTTPResponse response = handler(store).handleRequest(request("GET", "/inbox"), null);

        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        JsonNode emails = JSON.readTree(response.getBody()).get("emails");
        assertEquals(2, emails.size());
        JsonNode first = emails.get(0);
        assertEquals(Set.of("from", "title", "content", "sentAt"), fieldNames(first));
        assertEquals("Jane Doe <jane@example.com>", first.get("from").asText());
        assertEquals("Hello", first.get("title").asText());
        assertEquals("Hi there.", first.get("content").asText().strip());
        assertEquals("2026-11-09T01:16:06Z", first.get("sentAt").asText());
    }

    @Test
    void emptyInboxReturnsAnEmptyList() {
        APIGatewayV2HTTPResponse response = handler(new FakeStore()).handleRequest(request("GET", "/inbox"), null);

        assertEquals(200, response.getStatusCode());
        assertEquals("{\"emails\":[]}", response.getBody());
    }

    @Test
    void sortsNewestFirstWithUndatedEmailsLast() throws Exception {
        FakeStore store = new FakeStore()
            .with("inbox/undated", "From: a@example.com\r\nSubject: undated\r\n\r\nBody\r\n")
            .with("inbox/old", email("a@example.com", "old", "Sat, 1 Aug 2026 00:00:00 +0000", "Body"))
            .with("inbox/new", email("a@example.com", "new", "Mon, 9 Nov 2026 00:00:00 +0000", "Body"));

        APIGatewayV2HTTPResponse response = handler(store).handleRequest(request("GET", "/inbox"), null);

        List<String> titles = new ArrayList<>();
        JSON.readTree(response.getBody()).get("emails").forEach(email -> titles.add(email.get("title").asText()));
        assertEquals(List.of("new", "old", "undated"), titles);
    }

    @Test
    void missingDateIsNullAndOtherFieldsArePresent() throws Exception {
        FakeStore store = new FakeStore().with("inbox/a", "From: a@example.com\r\nSubject: No date\r\n\r\nBody\r\n");

        APIGatewayV2HTTPResponse response = handler(store).handleRequest(request("GET", "/inbox"), null);

        JsonNode email = JSON.readTree(response.getBody()).get("emails").get(0);
        assertTrue(email.has("sentAt"));
        assertTrue(email.get("sentAt").isNull());
        assertEquals("a@example.com", email.get("from").asText());
        assertEquals("No date", email.get("title").asText());
    }

    @Test
    void storeFailureReturnsErrorWithoutInternalDetails() {
        EmailStore failing = new FakeStore() {
            @Override
            public List<String> listKeys() {
                throw new IllegalStateException("internal bucket detail");
            }
        };

        APIGatewayV2HTTPResponse response = handler(failing).handleRequest(request("GET", "/inbox"), null);

        assertEquals(500, response.getStatusCode());
        assertEquals(READ_ERROR_BODY, response.getBody());
    }

    @Test
    void inboxOverTheSizeLimitReturnsErrorAndNoPartialList() {
        FakeStore store = new FakeStore()
            .with("inbox/a", email("a@example.com", "A", "Mon, 9 Nov 2026 00:00:00 +0000", "x".repeat(200)));

        APIGatewayV2HTTPResponse response = new EmailClientHandler(store, 100).handleRequest(request("GET", "/inbox"), null);

        assertEquals(500, response.getStatusCode());
        assertEquals(TOO_LARGE_BODY, response.getBody());
    }

    @Test
    void runningOutOfTimeReturnsErrorAndNoPartialList() {
        FakeStore store = new FakeStore()
            .with("inbox/a", email("a@example.com", "A", "Mon, 9 Nov 2026 00:00:00 +0000", "Body"));

        APIGatewayV2HTTPResponse response = handler(store).handleRequest(request("GET", "/inbox"), contextWithRemainingMillis(100));

        assertEquals(500, response.getStatusCode());
        assertEquals(TOO_LARGE_BODY, response.getBody());
    }

    @Test
    void otherMethodsOnInboxReturn405() {
        APIGatewayV2HTTPResponse response = handler(new FakeStore()).handleRequest(request("POST", "/inbox"), null);

        assertEquals(405, response.getStatusCode());
        assertEquals("GET", response.getHeaders().get("Allow"));
        assertFalse(response.getBody().contains("emails"));
    }

    private static EmailClientHandler handler(EmailStore store) {
        return new EmailClientHandler(store, 6_000_000);
    }

    private static APIGatewayV2HTTPEvent request(String method, String path) {
        return APIGatewayV2HTTPEvent.builder()
            .withRawPath(path)
            .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder().withMethod(method).build())
                .build())
            .build();
    }

    private static String email(String from, String subject, String date, String body) {
        return "From: " + from + "\r\nSubject: " + subject + "\r\nDate: " + date + "\r\n\r\n" + body + "\r\n";
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Context contextWithRemainingMillis(int millis) {
        return (Context) Proxy.newProxyInstance(
            Context.class.getClassLoader(),
            new Class<?>[] {Context.class},
            (proxy, method, args) -> method.getName().equals("getRemainingTimeInMillis") ? Integer.valueOf(millis) : null);
    }

    private static class FakeStore implements EmailStore {
        private final Map<String, String> messages = new LinkedHashMap<>();

        FakeStore with(String key, String message) {
            messages.put(key, message);
            return this;
        }

        @Override
        public List<String> listKeys() {
            return List.copyOf(messages.keySet());
        }

        @Override
        public InputStream open(String key) {
            return new ByteArrayInputStream(messages.get(key).getBytes(StandardCharsets.UTF_8));
        }
    }
}
