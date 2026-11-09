package com.example.email;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.InputStream;
import java.util.List;

public class S3EmailStore implements EmailStore {

    private static final String INBOX_PREFIX = "inbox/";

    private final S3Client s3;
    private final String bucket;

    S3EmailStore(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    static S3EmailStore fromEnvironment() {
        String bucket = System.getenv("INBOX_BUCKET_NAME");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("INBOX_BUCKET_NAME is not set");
        }
        S3Client s3 = S3Client.builder().httpClient(UrlConnectionHttpClient.create()).build();
        return new S3EmailStore(s3, bucket);
    }

    @Override
    public List<String> listKeys() {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).prefix(INBOX_PREFIX).build();
        return s3.listObjectsV2Paginator(request).contents().stream()
            .map(S3Object::key)
            .filter(key -> !key.endsWith("/"))
            .toList();
    }

    @Override
    public InputStream open(String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
