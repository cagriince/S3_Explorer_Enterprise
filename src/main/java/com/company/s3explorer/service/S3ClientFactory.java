package com.company.s3explorer.service;

import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.security.AesCryptoService;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;

import java.net.URI;

public class S3ClientFactory {

    private final AesCryptoService cryptoService;

    public S3ClientFactory() {
        this.cryptoService = new AesCryptoService();
    }

    public S3Client create(RepositoryDefinition repo) {
      S3Configuration s3Config =
                S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .checksumValidationEnabled(false)
                        .build();

        return S3Client.builder()
                .endpointOverride(URI.create(repo.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(repo.getAccessKey(), repo.getSecretKey())))
                .serviceConfiguration(s3Config)
                .region(Region.US_EAST_1)
                .build();
    }

    public boolean testConnection(RepositoryDefinition repo) {
        S3Client client = null;
        try {
            client = create(repo);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        finally {
            if (client != null) {
                client.close();
            }
        }
    }
}