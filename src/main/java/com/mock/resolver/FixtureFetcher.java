package com.mock.resolver;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import java.nio.charset.StandardCharsets;

@Component
public class FixtureFetcher {

    private final S3Client s3;

    public FixtureFetcher(S3Client s3) {
        this.s3 = s3;
    }

    public String fetch(String bucket, String key) {
        byte[] bytes = s3.getObjectAsBytes(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()
        ).asByteArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
