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

                /*
                 * JSON üzerinde çalışırken orijinal node'u
                 * değiştirmiyoruz.
                 *
                 * Önce kopyasını oluşturuyoruz.
                 */
                ObjectNode node =
                        originalNode.deepCopy();

                /*
                 * secretKeyEncrypted,
                 * RepositoryDefinition'ın propertysi değil.
                 *
                 * treeToValue() çağrısından önce kaldırıyoruz.
                 */
                JsonNode encryptedSecretNode =
                        node.remove("secretKeyEncrypted");

                RepositoryDefinition repository =
                        mapper.treeToValue(
                                node,
                                RepositoryDefinition.class);

                /*
                 * Yeni format:
                 *
                 * secretKeyEncrypted
                 */
                if (encryptedSecretNode != null
                        && !encryptedSecretNode.isNull()
                        && !encryptedSecretNode.asText().isBlank()) {

                    repository.setSecretKey(
                            cryptoService.decrypt(
                                    encryptedSecretNode.asText()));
                }

                /*
                 * Eski format:
                 *
                 * secretKey
                 *
                 * Eğer eski JSON kullanılıyorsa,
                 * treeToValue() secretKey'i zaten
                 * RepositoryDefinition içine yüklemiş olur.
                 *
                 * Böylece geriye dönük uyumluluk korunur.
                 */

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
                 * Secret'ın düz metin olarak
                 * JSON'a yazılmasını engelliyoruz.
                 */
                node.remove("secretKey");

                String secretKey =
                        repository.getSecretKey();

                if (secretKey != null
                        && !secretKey.isBlank()) {

                    String encryptedSecret =
                            cryptoService.encrypt(
                                    secretKey);

                    node.put(
                            "secretKeyEncrypted",
                            encryptedSecret);
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