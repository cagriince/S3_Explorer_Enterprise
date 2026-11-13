package com.company.s3explorer.ui.explorer;

import javax.swing.tree.DefaultMutableTreeNode;

public class S3TreeNode extends DefaultMutableTreeNode {
    public static final String ROOT_PREFIX = "";
    public static final String LOADING = "Loading...";
    public static final String ERROR = "Load failed - expand to retry";

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
        Object userObject = super.getUserObject();

        return LOADING.equals(userObject)
                || ERROR.equals(userObject);
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