package com.company.s3explorer.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RepositoryBackupService {

    private static final String ENTRY_NAME = "repositories.json";
    private static final int MAX_JSON_BYTES = 10 * 1024 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a password-protected AES-256 ZIP backup.
     * No plaintext JSON file is created on disk.
     */
    public void exportToZip(
            List<RepositoryDefinition> repositories,
            Path destination,
            char[] password) throws IOException {

        validatePassword(password);

        if (repositories == null || repositories.isEmpty()) {
            throw new IllegalArgumentException(
                    "There are no repositories to export.");
        }

        if (destination == null) {
            throw new IllegalArgumentException(
                    "Destination file must not be null.");
        }

        byte[] jsonBytes = createJson(repositories);

        if (jsonBytes.length > MAX_JSON_BYTES) {
            throw new IOException(
                    "Repository backup exceeds the maximum allowed size.");
        }

        ZipParameters parameters = new ZipParameters();
        parameters.setFileNameInZip(ENTRY_NAME);
        parameters.setCompressionMethod(CompressionMethod.DEFLATE);
        parameters.setEncryptFiles(true);
        parameters.setEncryptionMethod(EncryptionMethod.AES);
        parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

        ZipFile zipFile = new ZipFile(destination.toFile(), password);
        zipFile.addStream(
                new ByteArrayInputStream(jsonBytes),
                parameters);
    }

    /**
     * Reads and validates a backup without extracting files to disk.
     */
    public List<RepositoryDefinition> importFromZip(
            Path source,
            char[] password) throws IOException {

        validatePassword(password);

        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("Backup file does not exist.");
        }

        ZipFile zipFile = new ZipFile(source.toFile(), password);
        List<FileHeader> headers = zipFile.getFileHeaders();

        if (headers == null || headers.isEmpty()) {
            throw new IOException("The ZIP file is empty.");
        }

        FileHeader repositoryHeader = null;

        for (FileHeader header : headers) {
            if (header == null || header.isDirectory()) {
                continue;
            }

            if (ENTRY_NAME.equals(header.getFileName())) {
                if (repositoryHeader != null) {
                    throw new IOException(
                            "The ZIP contains duplicate repository data.");
                }
                repositoryHeader = header;
            } else {
                throw new IOException(
                        "The ZIP contains an unexpected file: "
                                + header.getFileName());
            }
        }

        if (repositoryHeader == null) {
            throw new IOException(
                    "The ZIP does not contain " + ENTRY_NAME + ".");
        }

        if (!repositoryHeader.isEncrypted()) {
            throw new IOException(
                    "The repository data is not encrypted.");
        }

        byte[] jsonBytes;

        try (InputStream input =
                     zipFile.getInputStream(repositoryHeader);
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int total = 0;
            int read;

            while ((read = input.read(buffer)) != -1) {
                total += read;

                if (total > MAX_JSON_BYTES) {
                    throw new IOException(
                            "Repository backup exceeds the maximum allowed size.");
                }

                output.write(buffer, 0, read);
            }

            jsonBytes = output.toByteArray();
        }

        return parseRepositories(jsonBytes);
    }

    private byte[] createJson(
            List<RepositoryDefinition> repositories) throws IOException {

        ArrayNode root = objectMapper.createArrayNode();

        for (RepositoryDefinition repository : repositories) {
            if (repository == null || repository.isEmpty()) {
                throw new IllegalArgumentException(
                        "The repository list contains an invalid entry.");
            }

            ObjectNode node = root.addObject();

            node.put("name", repository.getName());
            node.put(
                    "environment",
                    repository.getEnvironment() == null
                            ? ""
                            : repository.getEnvironment().name());

            putNullable(node, "endpoint", repository.getEndpoint());
            putNullable(node, "accessKey", repository.getAccessKey());
            putNullable(node, "secretKey", repository.getSecretKey());
            putNullable(
                    node,
                    "encryptionTransformation",
                    repository.getEncryptionTransformation());
            putNullable(
                    node,
                    "encryptionIv",
                    repository.getEncryptionIv());
            putNullable(
                    node,
                    "encryptionKey",
                    repository.getEncryptionKey());

            ArrayNode buckets = node.putArray("externalBuckets");
            for (String bucket : repository.getExternalBuckets()) {
                buckets.add(bucket);
            }
        }

        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(root);
    }

    private List<RepositoryDefinition> parseRepositories(
            byte[] jsonBytes) throws IOException {

        JsonNode root = objectMapper.readTree(jsonBytes);

        if (root == null || !root.isArray() || root.isEmpty()) {
            throw new IOException(
                    "The backup does not contain a valid repository list.");
        }

        List<RepositoryDefinition> repositories = new ArrayList<>();
        Set<String> repositoryIds = new HashSet<>();

        for (JsonNode node : root) {
            if (!node.isObject()) {
                throw new IOException(
                        "The backup contains an invalid repository entry.");
            }

            String name = requiredText(node, "name");
            String environmentName = requiredText(node, "environment");

            RepositoryEnvironment environment;

            try {
                environment =
                        RepositoryEnvironment.valueOf(environmentName);
            } catch (IllegalArgumentException ex) {
                throw new IOException(
                        "Unknown repository environment: "
                                + environmentName,
                        ex);
            }

            RepositoryDefinition repository =
                    new RepositoryDefinition(name.trim(), environment);

            repository.setEndpoint(optionalText(node, "endpoint"));
            repository.setAccessKey(optionalText(node, "accessKey"));
            repository.setSecretKey(optionalText(node, "secretKey"));

            repository.setEncryptionTransformation(
                    optionalText(node, "encryptionTransformation"));
            repository.setEncryptionIv(
                    optionalText(node, "encryptionIv"));
            repository.setEncryptionKey(
                    optionalText(node, "encryptionKey"));

            JsonNode bucketsNode = node.get("externalBuckets");
            List<String> buckets = new ArrayList<>();

            if (bucketsNode != null && !bucketsNode.isNull()) {
                if (!bucketsNode.isArray()) {
                    throw new IOException(
                            "External buckets must be a JSON array.");
                }

                for (JsonNode bucketNode : bucketsNode) {
                    if (!bucketNode.isTextual()) {
                        throw new IOException(
                                "An external bucket entry is invalid.");
                    }

                    String bucket = bucketNode.asText().trim();

                    if (!bucket.isEmpty()) {
                        buckets.add(bucket);
                    }
                }
            }

            repository.setExternalBuckets(buckets);

            String repositoryId = repository.getId();

            if (!repositoryIds.add(repositoryId)) {
                throw new IOException(
                        "The backup contains duplicate repository names "
                                + "for the same environment: "
                                + repositoryId);
            }

            repositories.add(repository);
        }

        return repositories;
    }

    private static void putNullable(
            ObjectNode node,
            String property,
            String value) {

        if (value == null) {
            node.putNull(property);
        } else {
            node.put(property, value);
        }
    }

    private static String requiredText(
            JsonNode node,
            String property) throws IOException {

        JsonNode value = node.get(property);

        if (value == null || !value.isTextual()
                || value.asText().isBlank()) {
            throw new IOException(
                    "Required repository property is missing: " + property);
        }

        return value.asText();
    }

    private static String optionalText(
            JsonNode node,
            String property) throws IOException {

        JsonNode value = node.get(property);

        if (value == null || value.isNull()) {
            return null;
        }

        if (!value.isTextual()) {
            throw new IOException(
                    "Repository property must be text: " + property);
        }

        return value.asText();
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException(
                    "Use a backup password containing at least 8 characters.");
        }
    }
}