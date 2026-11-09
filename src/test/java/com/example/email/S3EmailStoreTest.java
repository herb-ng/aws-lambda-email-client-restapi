package com.example.email;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3EmailStoreTest {

    @Test
    void listsKeysFromEveryPageUnderTheInboxPrefix() {
        FakeS3 s3 = new FakeS3();

        List<String> keys = new S3EmailStore(s3, "mail-bucket").listKeys();

        assertEquals(1001, keys.size());
        assertTrue(keys.contains("inbox/page-two"));
        assertEquals(List.of("inbox/"), s3.prefixes.stream().distinct().toList());
        assertEquals(List.of("mail-bucket"), s3.buckets.stream().distinct().toList());
    }

    @Test
    void ignoresFolderMarkers() {
        List<String> keys = new S3EmailStore(new FakeS3(), "mail-bucket").listKeys();

        assertFalse(keys.contains("inbox/"));
    }

    @Test
    void opensTheRequestedObject() throws IOException {
        FakeS3 s3 = new FakeS3();

        try (InputStream in = new S3EmailStore(s3, "mail-bucket").open("inbox/page-two")) {
            assertEquals("raw message", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals("inbox/page-two", s3.openedKey);
    }

    private static class FakeS3 implements S3Client {
        final List<String> prefixes = new ArrayList<>();
        final List<String> buckets = new ArrayList<>();
        String openedKey;

        @Override
        public ListObjectsV2Response listObjectsV2(ListObjectsV2Request request) {
            prefixes.add(request.prefix());
            buckets.add(request.bucket());
            if (request.continuationToken() == null) {
                List<S3Object> firstPage = new ArrayList<>();
                firstPage.add(S3Object.builder().key("inbox/").build());
                IntStream.range(0, 1000)
                    .forEach(i -> firstPage.add(S3Object.builder().key("inbox/message-" + i).build()));
                return ListObjectsV2Response.builder()
                    .contents(firstPage)
                    .isTruncated(true)
                    .nextContinuationToken("next")
                    .build();
            }
            return ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("inbox/page-two").build())
                .isTruncated(false)
                .build();
        }

        @Override
        public <T> T getObject(GetObjectRequest request, ResponseTransformer<GetObjectResponse, T> transformer) {
            openedKey = request.key();
            byte[] bytes = "raw message".getBytes(StandardCharsets.UTF_8);
            try {
                return transformer.transform(
                    GetObjectResponse.builder().build(),
                    AbortableInputStream.create(new ByteArrayInputStream(bytes)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String serviceName() {
            return "s3";
        }

        @Override
        public void close() {
        }
    }
}
