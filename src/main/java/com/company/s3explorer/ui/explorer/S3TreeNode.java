package com.company.s3explorer.ui.explorer;

import javax.swing.tree.DefaultMutableTreeNode;

public class S3TreeNode extends DefaultMutableTreeNode {
    public static String ROOT_PREFIX = "";
    public static String LOADING = "Loading...";

    private final String displayName;
    private final String bucket;
    private final String fullPrefix;

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
}