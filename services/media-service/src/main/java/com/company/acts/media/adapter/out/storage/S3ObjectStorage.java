package com.company.acts.media.adapter.out.storage;

import com.company.acts.media.application.port.out.ObjectStorage;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3-совместимое хранилище (RustFS, ADR 0006). Бакет создаётся при первом обращении, если его нет.
 * Адресация path-style: http://rustfs:9000/acts-media/ключ.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
class S3ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Client s3;
    private final String bucket;
    private volatile boolean bucketReady;

    S3ObjectStorage(@Value("${app.storage.endpoint}") String endpoint,
                    @Value("${app.storage.access-key}") String accessKey,
                    @Value("${app.storage.secret-key}") String secretKey,
                    @Value("${app.storage.bucket}") String bucket) {
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        ensureBucket();
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType).contentLength((long) content.length),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String key) {
        return s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                s3.headBucket(b -> b.bucket(bucket));
            } catch (NoSuchBucketException e) {
                createBucket();
            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    createBucket();
                } else {
                    throw e;
                }
            }
            bucketReady = true;
        }
    }

    private void createBucket() {
        log.info("Создаю бакет {}", bucket);
        s3.createBucket(b -> b.bucket(bucket));
    }
}
