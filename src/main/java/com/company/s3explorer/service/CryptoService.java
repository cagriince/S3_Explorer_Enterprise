package com.company.s3explorer.service;

public interface CryptoService {
    String encrypt(String value);
    String decrypt(String value);
}