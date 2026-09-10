package com.company.s3explorer.repository;

import com.company.s3explorer.security.AesCryptoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RepositoryConfigStore {

    private final File file;

    private final ObjectMapper mapper =
            new ObjectMapper();

    private final AesCryptoService cryptoService;

    public RepositoryConfigStore() {

        this.file =
                new File(
                        System.getProperty("user.home"),
                        ".s3explorer/repositories.json");

        this.cryptoService =
                new AesCryptoService();
    }

    public List<RepositoryDefinition> load() {

        if (!file.exists()) {
            return new ArrayList<>();
        }

        try {

            JsonNode root =
                    mapper.readTree(file);

            if (!root.isArray()) {

                throw new IllegalStateException(
                        "Repository configuration must be a JSON array");
            }

            List<RepositoryDefinition> repositories =
                    new ArrayList<>();

            for (JsonNode originalNode : root) {

                ObjectNode node =
                        originalNode.deepCopy();

                JsonNode encryptedSecretNode =
                        node.remove("secretKeyEncrypted");

                JsonNode encryptedTransformationNode =
                        node.remove("encryptionTransformationEncrypted");

                JsonNode encryptedIvNode =
                        node.remove("encryptionIvEncrypted");

                JsonNode encryptedKeyNode =
                        node.remove("encryptionKeyEncrypted");

                RepositoryDefinition repository =
                        mapper.treeToValue(
                                node,
                                RepositoryDefinition.class);

                if (encryptedSecretNode != null
                        && !encryptedSecretNode.isNull()
                        && !encryptedSecretNode.asText().isBlank()) {

                    repository.setSecretKey(
                            cryptoService.decrypt(
                                    encryptedSecretNode.asText()));
                }

                if (encryptedTransformationNode != null
                        && !encryptedTransformationNode.isNull()
                        && !encryptedTransformationNode.asText().isBlank()) {

                    repository.setEncryptionTransformation(
                            cryptoService.decrypt(
                                    encryptedTransformationNode.asText()));
                }

                if (encryptedIvNode != null
                        && !encryptedIvNode.isNull()
                        && !encryptedIvNode.asText().isBlank()) {

                    repository.setEncryptionIv(
                            cryptoService.decrypt(
                                    encryptedIvNode.asText()));
                }

                if (encryptedKeyNode != null
                        && !encryptedKeyNode.isNull()
                        && !encryptedKeyNode.asText().isBlank()) {

                    repository.setEncryptionKey(
                            cryptoService.decrypt(
                                    encryptedKeyNode.asText()));
                }

                repositories.add(repository);
            }

            return repositories;

        } catch (Exception ex) {

            throw new RuntimeException(
                    "Cannot load repositories",
                    ex);
        }
    }

    public void save(
            List<RepositoryDefinition> repositories) {

        try {

            file.getParentFile().mkdirs();

            ArrayNode root =
                    mapper.createArrayNode();

            for (RepositoryDefinition repository :
                    repositories) {

                ObjectNode node =
                        mapper.valueToTree(repository);

                /*
                 * Hassas alanların düz metin olarak
                 * JSON'a yazılmasını engelliyoruz.
                 */
                node.remove("secretKey");
                node.remove("encryptionTransformation");
                node.remove("encryptionIv");
                node.remove("encryptionKey");

                String secretKey =
                        repository.getSecretKey();

                if (secretKey != null
                        && !secretKey.isBlank()) {

                    node.put(
                            "secretKeyEncrypted",
                            cryptoService.encrypt(
                                    secretKey));
                }

                String encryptionTransformation =
                        repository.getEncryptionTransformation();

                if (encryptionTransformation != null
                        && !encryptionTransformation.isBlank()) {

                    node.put(
                            "encryptionTransformationEncrypted",
                            cryptoService.encrypt(
                                    encryptionTransformation));
                }

                String encryptionIv =
                        repository.getEncryptionIv();

                if (encryptionIv != null
                        && !encryptionIv.isBlank()) {

                    node.put(
                            "encryptionIvEncrypted",
                            cryptoService.encrypt(
                                    encryptionIv));
                }

                String encryptionKey =
                        repository.getEncryptionKey();

                if (encryptionKey != null
                        && !encryptionKey.isBlank()) {

                    node.put(
                            "encryptionKeyEncrypted",
                            cryptoService.encrypt(
                                    encryptionKey));
                }

                root.add(node);
            }

            mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(
                            file,
                            root);

        } catch (Exception ex) {

            throw new RuntimeException(
                    "Cannot save repositories",
                    ex);
        }
    }
}