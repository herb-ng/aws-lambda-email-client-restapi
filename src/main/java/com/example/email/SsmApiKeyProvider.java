package com.example.email;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class SsmApiKeyProvider implements ApiKeyProvider {

    private static final Duration CACHE_DURATION = Duration.ofMinutes(5);

    private final SsmClient ssm;
    private final String parameterName;
    private final Supplier<Instant> clock;
    private String cachedKey;
    private Instant expiresAt = Instant.MIN;

    SsmApiKeyProvider(SsmClient ssm, String parameterName, Supplier<Instant> clock) {
        this.ssm = ssm;
        this.parameterName = parameterName;
        this.clock = clock;
    }

    static SsmApiKeyProvider fromEnvironment() {
        String parameterName = System.getenv("API_KEY_PARAMETER_NAME");
        if (parameterName == null || parameterName.isBlank()) {
            throw new IllegalStateException("API_KEY_PARAMETER_NAME is not set");
        }
        SsmClient ssm = SsmClient.builder().httpClient(UrlConnectionHttpClient.create()).build();
        return new SsmApiKeyProvider(ssm, parameterName, Instant::now);
    }

    @Override
    public String currentKey() {
        Instant now = clock.get();
        if (now.isBefore(expiresAt)) {
            return cachedKey;
        }
        cachedKey = null;
        GetParameterRequest request = GetParameterRequest.builder().name(parameterName).withDecryption(true).build();
        String key = ssm.getParameter(request).parameter().value();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("The API key parameter is blank");
        }
        cachedKey = key;
        expiresAt = now.plus(CACHE_DURATION);
        return key;
    }
}
