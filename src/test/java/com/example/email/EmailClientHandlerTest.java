package com.example.email;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailClientHandlerTest {

    @Test
    void returnsHealthyApiResponse() {
        APIGatewayV2HTTPResponse response = new EmailClientHandler()
            .handleRequest(new APIGatewayV2HTTPEvent(), null);

        assertEquals(200, response.getStatusCode());
        assertEquals("{\"message\":\"Email client API is running\"}", response.getBody());
    }
}