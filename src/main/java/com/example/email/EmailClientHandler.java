package com.example.email;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmailClientHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final String INBOX_PATH = "/inbox";
    // Below the 6 MB (6,291,456 byte) response limit, leaving room for the rest of the Lambda response.
    private static final int MAX_RESPONSE_BYTES = 6_000_000;
    private static final int MIN_REMAINING_MILLIS = 2_000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Comparator<Email> NEWEST_FIRST =
        Comparator.comparing(Email::sentAt, Comparator.nullsLast(Comparator.reverseOrder()));

    private final int maxResponseBytes;
    private EmailStore store;

    public EmailClientHandler() {
        this(null, MAX_RESPONSE_BYTES);
    }

    EmailClientHandler(EmailStore store, int maxResponseBytes) {
        this.store = store;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {
        if (!INBOX_PATH.equals(request.getRawPath())) {
            return response(200, "{\"message\":\"Email client API is running\"}");
        }
        if (!"GET".equals(method(request))) {
            return response(405, Map.of("Allow", "GET"), "{\"message\":\"Method not allowed\"}");
        }
        try {
            return inboxResponse(context);
        } catch (InboxTooLargeException e) {
            log(e);
            return response(500, "{\"message\":\"The inbox is too large to return\"}");
        } catch (RuntimeException | IOException e) {
            log(e);
            return response(500, "{\"message\":\"Could not read the inbox\"}");
        }
    }

    private APIGatewayV2HTTPResponse inboxResponse(Context context) throws IOException {
        List<Email> emails = readEmails(context);
        emails.sort(NEWEST_FIRST);
        String body = JSON.writeValueAsString(Map.of("emails", emails));
        // Lambda wraps the body in a JSON response, so measure it with its escaping included.
        if (JSON.writeValueAsBytes(body).length > maxResponseBytes) {
            throw new InboxTooLargeException("Response exceeds " + maxResponseBytes + " bytes");
        }
        return response(200, body);
    }

    private List<Email> readEmails(Context context) throws IOException {
        if (store == null) {
            store = S3EmailStore.fromEnvironment();
        }
        List<Email> emails = new ArrayList<>();
        long contentLength = 0;
        for (String key : store.listKeys()) {
            if (context != null && context.getRemainingTimeInMillis() < MIN_REMAINING_MILLIS) {
                throw new InboxTooLargeException("Ran out of time after reading " + emails.size() + " emails");
            }
            try (InputStream rawMessage = store.open(key)) {
                Email email = MimeEmailParser.parse(rawMessage);
                emails.add(email);
                contentLength += email.content() == null ? 0 : email.content().length();
            }
            // Stops reading early, before an oversized inbox can use up the function's memory.
            if (contentLength > maxResponseBytes) {
                throw new InboxTooLargeException("Content exceeds " + maxResponseBytes + " bytes");
            }
        }
        return emails;
    }

    private static String method(APIGatewayV2HTTPEvent request) {
        if (request.getRequestContext() == null || request.getRequestContext().getHttp() == null) {
            return null;
        }
        return request.getRequestContext().getHttp().getMethod();
    }

    private static APIGatewayV2HTTPResponse response(int statusCode, String body) {
        return response(statusCode, Map.of(), body);
    }

    private static APIGatewayV2HTTPResponse response(int statusCode, Map<String, String> extraHeaders, String body) {
        Map<String, String> headers = new HashMap<>(extraHeaders);
        headers.put("Content-Type", "application/json");
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(body)
                .build();
    }

    // Logs only the failure, never message data.
    private static void log(Exception e) {
        System.err.println("Inbox request failed: " + e.getClass().getName() + ": " + e.getMessage());
    }

    private static final class InboxTooLargeException extends RuntimeException {
        InboxTooLargeException(String message) {
            super(message);
        }
    }
}
