package com.company.s3explorer.service;

import com.company.s3explorer.repository.RepositoryDefinition;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.net.URI;
import java.util.List;

public class S3ClientFactory {

    public S3Client create(
            RepositoryDefinition repo) {

        validateRepository(repo);

        S3Configuration s3Config =
                S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .checksumValidationEnabled(false)
                        .build();

        return S3Client.builder()
                .endpointOverride(
                        URI.create(
                                repo.getEndpoint()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        repo.getAccessKey(),
                                        repo.getSecretKey())))
                .serviceConfiguration(
                        s3Config)
                .region(
                        Region.US_EAST_1)
                .build();
    }

    public boolean testConnection(
            RepositoryDefinition repo) {

        S3Client client = null;

        try {

            validateRepository(repo);

            client = create(repo);

            List<String> externalBuckets =
                    repo.getExternalBuckets();

            /*
             * External bucket tanımlanmışsa,
             * gerçek kullanım senaryosunu test ediyoruz.
             *
             * ListBuckets yetkisine ihtiyaç yok.
             */
            if (externalBuckets != null
                    && !externalBuckets.isEmpty()) {

                for (String bucket :
                        externalBuckets) {

                    if (bucket == null
                            || bucket.isBlank()) {

                        continue;
                    }

                    testBucketAccess(
                            client,
                            bucket.trim());
                }

                return true;
            }

            /*
             * External bucket yoksa artık
             * ListBuckets gerçek bir bağlantı
             * ve credential testi olarak kullanılıyor.
             *
             * AccessDenied dahil herhangi bir hata
             * başarısız kabul edilir.
             */
            client.listBuckets();

            return true;

        } catch (Exception ex) {

            return false;

        } finally {

            if (client != null) {
                client.close();
            }
        }
    }

    private void testBucketAccess(
            S3Client client,
            String bucket) {

        client.headBucket(
                HeadBucketRequest.builder()
                        .bucket(bucket)
                        .build());
    }

    private void validateRepository(
            RepositoryDefinition repo) {

        if (repo == null) {

            throw new IllegalArgumentException(
                    "Repository cannot be null");
        }

        if (repo.isEmpty()) {

            throw new IllegalArgumentException(
                    "Repository is empty");
        }

        if (repo.getEndpoint() == null
                || repo.getEndpoint().isBlank()) {

            throw new IllegalArgumentException(
                    "Repository endpoint is required");
        }

        if (repo.getAccessKey() == null
                || repo.getAccessKey().isBlank()) {

            throw new IllegalArgumentException(
                    "Repository access key is required");
        }

        if (repo.getSecretKey() == null
                || repo.getSecretKey().isBlank()) {

            throw new IllegalArgumentException(
                    "Repository secret key is required");
        }
    }
}