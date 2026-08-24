package com.company.s3explorer.service;

import com.company.s3explorer.repository.RepositoryDefinition;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
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

    public ConnectionTestResult testConnection(
            RepositoryDefinition repo) {

        S3Client client = null;

        try {

            validateRepository(repo);

            client = create(repo);

            List<String> externalBuckets =
                    repo.getExternalBuckets();

            /*
             * External bucket tanımlanmışsa
             * ListBuckets yetkisine ihtiyaç duymuyoruz.
             *
             * Uygulamanın gerçekten kullanacağı
             * bucket'lara erişimi test ediyoruz.
             */
            if (externalBuckets != null
                    && !externalBuckets.isEmpty()) {

                for (String bucket :
                        externalBuckets) {

                    if (bucket == null
                            || bucket.isBlank()) {
                        continue;
                    }

                    ConnectionTestResult result =
                            testBucketAccess(
                                    client,
                                    bucket.trim());

                    if (!result.isSuccess()) {
                        return result;
                    }
                }

                return ConnectionTestResult.success();
            }

            /*
             * External bucket yoksa
             * ListBuckets ile repository seviyesinde
             * bağlantı ve credential testi yapıyoruz.
             */
            try {

                client.listBuckets();

                return ConnectionTestResult.success();

            } catch (S3Exception ex) {

                return classifyS3Exception(ex);
            }

        } catch (SdkClientException ex) {

            return classifySdkClientException(ex);

        } catch (Exception ex) {

            return ConnectionTestResult.connectionFailed(
                    buildDetail(ex));

        } finally {

            if (client != null) {
                client.close();
            }
        }
    }

    private ConnectionTestResult testBucketAccess(
            S3Client client,
            String bucket) {

        try {

            client.headBucket(
                    HeadBucketRequest.builder()
                            .bucket(bucket)
                            .build());

            return ConnectionTestResult.success();

        } catch (S3Exception ex) {

            int statusCode =
                    ex.statusCode();

            String errorCode =
                    getAwsErrorCode(ex);

            String detail =
                    buildS3Detail(ex);

            /*
             * Authentication problemleri.
             */
            if (isInvalidCredentials(
                    errorCode,
                    statusCode)) {

                return ConnectionTestResult
                        .invalidCredentials(detail);
            }

            /*
             * Bucket mevcut olabilir ancak
             * kullanıcının erişim yetkisi yoktur.
             */
            if (statusCode == 403
                    || "AccessDenied".equalsIgnoreCase(
                    errorCode)) {

                return ConnectionTestResult
                        .bucketAccessDenied(
                                bucket,
                                detail);
            }

            /*
             * S3 HeadBucket için 404,
             * bucket'ın bulunamadığını gösterir.
             */
            if (statusCode == 404
                    || "NoSuchBucket".equalsIgnoreCase(
                    errorCode)) {

                return ConnectionTestResult
                        .bucketNotFound(
                                bucket,
                                detail);
            }

            return ConnectionTestResult
                    .connectionFailed(detail);

        } catch (SdkClientException ex) {

            return ConnectionTestResult
                    .endpointUnreachable(
                            buildDetail(ex));
        }
    }

    private ConnectionTestResult classifyS3Exception(
            S3Exception ex) {

        int statusCode =
                ex.statusCode();

        String errorCode =
                getAwsErrorCode(ex);

        String detail =
                buildS3Detail(ex);

        /*
         * Örneğin:
         *
         * InvalidAccessKeyId
         * SignatureDoesNotMatch
         * InvalidToken
         */
        if (isInvalidCredentials(
                errorCode,
                statusCode)) {

            return ConnectionTestResult
                    .invalidCredentials(detail);
        }

        /*
         * Endpoint'e ulaşıldı fakat
         * ListBuckets yetkisi yok.
         *
         * Bu bağlantının tamamen başarısız
         * olduğu anlamına gelmez.
         */
        if (statusCode == 403
                || "AccessDenied".equalsIgnoreCase(
                errorCode)) {

            return ConnectionTestResult
                    .connectedLimited(detail);
        }

        return ConnectionTestResult
                .connectionFailed(detail);
    }

    private boolean isInvalidCredentials(
            String errorCode,
            int statusCode) {

        if (errorCode == null) {
            return false;
        }

        return "InvalidAccessKeyId"
                .equalsIgnoreCase(errorCode)

                || "InvalidAccessKey"
                .equalsIgnoreCase(errorCode)

                || "SignatureDoesNotMatch"
                .equalsIgnoreCase(errorCode)

                || "InvalidToken"
                .equalsIgnoreCase(errorCode)

                || "ExpiredToken"
                .equalsIgnoreCase(errorCode)

                || "InvalidClientTokenId"
                .equalsIgnoreCase(errorCode);
    }

    private String getAwsErrorCode(
            S3Exception ex) {

        if (ex.awsErrorDetails() == null) {
            return null;
        }

        return ex.awsErrorDetails()
                .errorCode();
    }

    private String buildS3Detail(
            S3Exception ex) {

        String errorCode =
                getAwsErrorCode(ex);

        String message =
                ex.getMessage();

        if (errorCode == null
                || errorCode.isBlank()) {

            return message;
        }

        if (message == null
                || message.isBlank()) {

            return "S3 error: "
                    + errorCode;
        }

        return errorCode
                + ": "
                + message;
    }

    private ConnectionTestResult classifySdkClientException(
            SdkClientException ex) {

        Throwable cause =
                ex.getCause();

        if (containsCause(
                ex,
                SocketTimeoutException.class)
                || containsCause(
                ex,
                ConnectException.class)
                || containsCause(
                ex,
                IOException.class)) {

            return ConnectionTestResult
                    .endpointUnreachable(
                            buildDetail(ex));
        }

        return ConnectionTestResult
                .connectionFailed(
                        buildDetail(ex));
    }

    private boolean containsCause(
            Throwable throwable,
            Class<? extends Throwable> type) {

        Throwable current =
                throwable;

        while (current != null) {

            if (type.isInstance(current)) {
                return true;
            }

            current =
                    current.getCause();
        }

        return false;
    }

    private String buildDetail(
            Exception ex) {

        String message =
                ex.getMessage();

        if (message == null
                || message.isBlank()) {

            return ex.getClass()
                    .getSimpleName();
        }

        return message;
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