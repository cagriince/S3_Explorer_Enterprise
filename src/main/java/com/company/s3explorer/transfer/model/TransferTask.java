package com.company.s3explorer.transfer.model;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.ui.explorer.RefreshTreeNode;
import com.company.s3explorer.ui.explorer.S3TreeNode;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class TransferTask {
    private UUID id = UUID.randomUUID();
    private TransferGroup group;

    private TransferType type;

    private String repositoryName;
    private String bucket;
    private String objectKey;
    private Path localPath;
    private String targetRepositoryName;
    private String targetBucket;
    private String targetObjectKey;

    private Set<RefreshTreeNode> affectedPrefixes = new HashSet<>();
    private boolean affectsObjectList;
    private boolean affectsFolderTree;

    private long size;

    public TransferTask() {
    }

    public UUID getId() {
        return id;
    }

    public TransferType getType() {
        return type;
    }

    public TransferGroup getGroup() {
        return group;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getBucket() {
        return bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public Path getLocalPath() {
        return localPath;
    }

    public String getTargetBucket() {
        return targetBucket;
    }

    public String getTargetRepositoryName() {
        return targetRepositoryName;
    }

    public void setTargetBucket(String targetBucket) {
        this.targetBucket = targetBucket;
    }

    public String getTargetObjectKey() {
        return targetObjectKey;
    }

    public void setTargetObjectKey(String targetObjectKey) {
        this.targetObjectKey = targetObjectKey;
    }

    public Set<RefreshTreeNode> getAffectedPrefixes() {
        return affectedPrefixes;
    }

    public boolean isAffectsObjectList() {
        return affectsObjectList;
    }

    public boolean isAffectsFolderTree() {
        return affectsFolderTree;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public static Builder upload() {
        return new Builder(TransferType.UPLOAD);
    }

    public static Builder download() {
        return new Builder(TransferType.DOWNLOAD);
    }

    public static Builder delete() {
        return new Builder(TransferType.DELETE);
    }

    public static Builder copy() {
        return new Builder(TransferType.COPY);
    }

    public static Builder move() {
        return new Builder(TransferType.MOVE);
    }

    public static Builder createFolder() {
        return new Builder(TransferType.CREATE_FOLDER);
    }

    public static class Builder {

        private final TransferTask task;

        private Builder(TransferType type) {
            task = new TransferTask();
            task.id = UUID.randomUUID();
            task.type = type;
        }

        public Builder repositoryName(String repositoryName) {
            task.repositoryName = repositoryName;
            return this;
        }

        public Builder bucket(String bucket) {
            task.bucket = bucket;
            return this;
        }

        public Builder objectKey(String key) {
            task.objectKey = key;
            return this;
        }

        public Builder localPath(Path path) {
            task.localPath = path;
            return this;
        }

        public Builder targetRepositoryName(String targetRepositoryName) {
            task.targetRepositoryName = targetRepositoryName;
            return this;
        }

        public Builder targetBucket(String bucket) {
            task.targetBucket = bucket;
            return this;
        }

        public Builder targetObjectKey(String key) {
            task.targetObjectKey = key;
            return this;
        }

        public Builder addRefreshPrefix(RefreshTreeNode prefix) {
            task.affectedPrefixes.add(prefix);
            return this;
        }

        public Builder affectsObjectList(boolean affectsObjectList) {
            task.affectsObjectList = affectsObjectList;
            return this;
        }

        public Builder affectsFolderTree(boolean affectsFolderTree) {
            task.affectsFolderTree = affectsFolderTree;
            return this;
        }

        public Builder size(long size) {
            task.size = size;
            return this;
        }
        
        public Builder group(TransferGroup group) {
            task.group = group;
            return this;
        }

        public TransferTask build() {
            validate();
            return task;
        }

        private void validate() {
            switch (task.type) {
                case UPLOAD -> {
                    Objects.requireNonNull(task.targetRepositoryName);
                    Objects.requireNonNull(task.targetBucket);
                    Objects.requireNonNull(task.targetObjectKey);
                    Objects.requireNonNull(task.localPath);
                }
                case DOWNLOAD -> {
                    Objects.requireNonNull(task.repositoryName);
                    Objects.requireNonNull(task.bucket);
                    Objects.requireNonNull(task.objectKey);
                    Objects.requireNonNull(task.localPath);
                }
                case DELETE -> {
                    Objects.requireNonNull(task.repositoryName);
                    Objects.requireNonNull(task.bucket);
                    Objects.requireNonNull(task.objectKey);
                }
                case COPY,
                     MOVE -> {
                    Objects.requireNonNull(task.repositoryName);
                    Objects.requireNonNull(task.bucket);
                    Objects.requireNonNull(task.objectKey);
                    Objects.requireNonNull(task.targetRepositoryName);
                    Objects.requireNonNull(task.targetBucket);
                    Objects.requireNonNull(task.targetObjectKey);
                }
                case CREATE_FOLDER -> {
                    Objects.requireNonNull(task.repositoryName);
                    Objects.requireNonNull(task.bucket);
                    Objects.requireNonNull(task.objectKey);
                }

            }
        }

    }
}