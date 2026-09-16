package com.company.s3explorer.ui.explorer;

import javax.swing.tree.DefaultMutableTreeNode;

public class S3TreeNode extends DefaultMutableTreeNode {
    public static String ROOT_PREFIX = "";
    public static String LOADING = "Loading...";

    private String displayName;
    private String bucket;
    private String fullPrefix;

    public S3TreeNode(String displayName, String bucket, String fullPrefix) {
        super(displayName);
        this.displayName = displayName;
        this.bucket = bucket;
        this.fullPrefix = fullPrefix;
    }

    public String getBucket() {
        return bucket;
    }

    public String getFullPrefix() {
        return fullPrefix;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public boolean isLoading() {
        return LOADING.equals(super.getUserObject());
    }

    public void rename(
            String newDisplayName,
            String newBucket,
            String newFullPrefix) {

        this.displayName = newDisplayName;
        this.bucket = newBucket;
        this.fullPrefix = newFullPrefix;

        setUserObject(newDisplayName);
    }

    public String getDisplayName() {
        return displayName;
    }
}