package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.util.S3Util;

import software.amazon.awssdk.services.s3.model.S3Object;

public abstract class AbstractCopyMoveProducer
        extends AbstractFolderTransferProducer {

    protected final String targetRepositoryId;
    protected final String targetBucket;
    protected final String targetPrefix;

    protected final String parentPrefix;

    /**
     * TransferManager tarafından oluşturulan logical
     * TransferGroup'un producer tarafından kullanılması için.
     */
    protected AbstractCopyMoveProducer(
            TransferContext context,
            TransferQueue queue,
            String repositoryId,
            String bucket,
            String prefix,
            String targetRepositoryId,
            String targetBucket,
            String targetPrefix,
            TransferGroup externalGroup) {

        super(
                context,
                queue,
                repositoryId,
                bucket,
                prefix,
                externalGroup);

        this.targetRepositoryId = targetRepositoryId;
        this.targetBucket = targetBucket;
        this.targetPrefix = targetPrefix;

        this.parentPrefix =
                S3Util.extractParentPrefix(prefix);
    }

    protected String buildTargetKey(
            S3Object object) {

        String relative =
                object.key()
                        .substring(parentPrefix.length());

        return S3Util.combineKey(
                targetPrefix,
                relative);
    }

    protected String getTargetChildPrefix() {

        return S3Util.combineKey(
                targetPrefix,
                S3Util.extractFolderName(prefix));
    }
}