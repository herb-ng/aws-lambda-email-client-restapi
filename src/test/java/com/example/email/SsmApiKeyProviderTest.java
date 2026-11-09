package com.example.email;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsmApiKeyProviderTest {

    private static final String NAME = "/email-client-restapi/api-key";

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-09T00:00:00Z"));
    private final FakeSsm ssm = new FakeSsm();
    private final SsmApiKeyProvider provider = new SsmApiKeyProvider(ssm, NAME, now::get);

    @Test
    void readsTheDecryptedParameter() {
        ssm.value = "first-key";

        assertEquals("first-key", provider.currentKey());
        assertEquals(NAME, ssm.lastRequest.name());
        assertTrue(ssm.lastRequest.withDecryption());
    }

    @Test
    void cachesTheKeyForUpToFiveMinutes() {
        ssm.value = "first-key";
        provider.currentKey();
        ssm.value = "second-key";
        now.set(now.get().plus(Duration.ofMinutes(5)).minusSeconds(1));

        assertEquals("first-key", provider.currentKey());
        assertEquals(1, ssm.calls);
    }

    @Test
    void picksUpAReplacedKeyAfterFiveMinutes() {
        ssm.value = "first-key";
        provider.currentKey();
        ssm.value = "second-key";
        now.set(now.get().plus(Duration.ofMinutes(5)));

        assertEquals("second-key", provider.currentKey());
    }

    @Test
    void doesNotKeepUsingAnExpiredKeyWhenTheParameterCantBeRead() {
        ssm.value = "first-key";
        provider.currentKey();
        ssm.failure = new IllegalStateException("parameter store is unavailable");
        now.set(now.get().plus(Duration.ofMinutes(5)));

        assertThrows(IllegalStateException.class, provider::currentKey);
    }

    @Test
    void rejectsABlankKey() {
        ssm.value = "  ";

        assertThrows(IllegalStateException.class, provider::currentKey);
    }

    private static class FakeSsm implements SsmClient {
        String value;
        RuntimeException failure;
        GetParameterRequest lastRequest;
        int calls;

        @Override
        public GetParameterResponse getParameter(GetParameterRequest request) {
            if (failure != null) {
                throw failure;
            }
            lastRequest = request;
            calls++;
            return GetParameterResponse.builder()
                .parameter(Parameter.builder().value(value).build())
                .build();
        }

        @Override
        public String serviceName() {
            return "ssm";
        }

        @Override
        public void close() {
        }
    }
}
