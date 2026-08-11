package com.company.s3explorer;

public interface LogService {
    void info(String message);
    void error(String message, Throwable throwable);
}