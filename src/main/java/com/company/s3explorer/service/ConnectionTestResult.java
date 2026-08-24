package com.company.s3explorer.service;

public class ConnectionTestResult {

    public enum Status {

        SUCCESS,

        CONNECTED_LIMITED,

        INVALID_CREDENTIALS,

        BUCKET_ACCESS_DENIED,

        BUCKET_NOT_FOUND,

        ENDPOINT_UNREACHABLE,

        CONNECTION_FAILED
    }

    private final Status status;

    private final String message;

    private final String detail;

    private ConnectionTestResult(
            Status status,
            String message,
            String detail) {

        this.status = status;
        this.message = message;
        this.detail = detail;
    }

    public static ConnectionTestResult success() {

        return new ConnectionTestResult(
                Status.SUCCESS,
                "Connection successful",
                null);
    }

    public static ConnectionTestResult connectedLimited(
            String detail) {

        return new ConnectionTestResult(
                Status.CONNECTED_LIMITED,
                "Connection successful, but ListBuckets permission is denied",
                detail);
    }

    public static ConnectionTestResult invalidCredentials(
            String detail) {

        return new ConnectionTestResult(
                Status.INVALID_CREDENTIALS,
                "Invalid credentials",
                detail);
    }

    public static ConnectionTestResult bucketAccessDenied(
            String bucket,
            String detail) {

        return new ConnectionTestResult(
                Status.BUCKET_ACCESS_DENIED,
                "Bucket access denied: " + bucket,
                detail);
    }

    public static ConnectionTestResult bucketNotFound(
            String bucket,
            String detail) {

        return new ConnectionTestResult(
                Status.BUCKET_NOT_FOUND,
                "Bucket not found: " + bucket,
                detail);
    }

    public static ConnectionTestResult endpointUnreachable(
            String detail) {

        return new ConnectionTestResult(
                Status.ENDPOINT_UNREACHABLE,
                "Endpoint could not be reached",
                detail);
    }

    public static ConnectionTestResult connectionFailed(
            String detail) {

        return new ConnectionTestResult(
                Status.CONNECTION_FAILED,
                "Connection failed",
                detail);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isSuccess() {

        return status == Status.SUCCESS
                || status == Status.CONNECTED_LIMITED;
    }

    @Override
    public String toString() {

        if (detail == null
                || detail.isBlank()) {

            return message;
        }

        return message
                + System.lineSeparator()
                + System.lineSeparator()
                + detail;
    }
}