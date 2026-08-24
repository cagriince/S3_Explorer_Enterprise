package com.company.s3explorer.service;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

public final class S3ErrorResolver {

    private S3ErrorResolver() {
    }

    public static String getUserMessage(
            Throwable throwable) {

        Throwable cause =
                unwrap(throwable);

        if (cause instanceof S3Exception s3Exception) {

            return resolveS3Exception(
                    s3Exception);
        }

        if (cause instanceof SdkClientException
                || containsCause(
                cause,
                ConnectException.class)
                || containsCause(
                cause,
                SocketTimeoutException.class)) {

            return "The S3 service could not be reached.";
        }

        if (cause.getMessage() != null
                && !cause.getMessage().isBlank()) {

            return cause.getMessage();
        }

        return "The S3 operation failed.";
    }

    public static String getDetailedMessage(
            Throwable throwable) {

        Throwable cause =
                unwrap(throwable);

        if (cause instanceof S3Exception s3Exception) {

            String errorCode =
                    getErrorCode(s3Exception);

            String message =
                    s3Exception.getMessage();

            if (errorCode != null
                    && message != null
                    && !message.isBlank()) {

                return errorCode
                        + ": "
                        + message;
            }

            if (errorCode != null) {
                return errorCode;
            }

            if (message != null
                    && !message.isBlank()) {

                return message;
            }
        }

        return getUserMessage(cause);
    }

    public static boolean isAccessDenied(
            Throwable throwable) {

        Throwable cause =
                unwrap(throwable);

        if (!(cause instanceof S3Exception s3Exception)) {
            return false;
        }

        String errorCode =
                getErrorCode(s3Exception);

        return s3Exception.statusCode() == 403
                || "AccessDenied".equalsIgnoreCase(
                errorCode);
    }
    
    private static String resolveS3Exception(
            S3Exception exception) {

        String errorCode =
                getErrorCode(exception);

        int statusCode =
                exception.statusCode();

        if ("InvalidAccessKeyId"
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
                .equalsIgnoreCase(errorCode)) {

            return "Invalid S3 credentials.";
        }

        if (statusCode == 403
                || "AccessDenied"
                .equalsIgnoreCase(errorCode)) {

            return "Access denied. You do not have permission to perform this operation.";
        }

        if (statusCode == 404
                || "NoSuchBucket"
                .equalsIgnoreCase(errorCode)) {

            return "The requested bucket was not found.";
        }

        if ("NoSuchKey"
                .equalsIgnoreCase(errorCode)) {

            return "The requested object was not found.";
        }

        if ("NoSuchUpload"
                .equalsIgnoreCase(errorCode)) {

            return "The requested multipart upload was not found.";
        }

        if ("SlowDown"
                .equalsIgnoreCase(errorCode)
                || statusCode == 429) {

            return "The S3 service is throttling requests. Please try again.";
        }

        if (statusCode >= 500
                && statusCode < 600) {

            return "The S3 service is temporarily unavailable. Please try again.";
        }

        if (errorCode != null
                && !errorCode.isBlank()) {

            return "S3 operation failed: "
                    + errorCode;
        }

        return "S3 operation failed.";
    }

    private static String getErrorCode(
            S3Exception exception) {

        if (exception.awsErrorDetails() == null) {
            return null;
        }

        return exception.awsErrorDetails()
                .errorCode();
    }

    private static Throwable unwrap(
            Throwable throwable) {

        if (throwable == null) {
            return new RuntimeException(
                    "Unknown S3 error");
        }

        Throwable current =
                throwable;

        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {

            current =
                    current.getCause();
        }

        return current;
    }

    private static boolean containsCause(
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
}