package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.util.S3Util;

import software.amazon.awssdk.services.s3.model.S3Object;

public abstract class AbstractCopyMoveProducer
        extends AbstractFolderTransferProducer {

    protected final String targetRepository;
    protected final String targetBucket;
    protected final String targetPrefix;

    protected final String parentPrefix;

    /**
     * Backward-compatible constructor.
     *
     * Producer kendi TransferGroup'unu oluşturur.
     */
    protected AbstractCopyMoveProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix,
            String targetRepository,
            String targetBucket,
            String targetPrefix,
            boolean sourceRefreshRequired) {

        this(
                context,
                queue,
                repository,
                bucket,
                prefix,
                targetRepository,
                targetBucket,
                targetPrefix,
                null,
                sourceRefreshRequired);
    }

    /**
     * TransferManager tarafından oluşturulan logical
     * TransferGroup'un producer tarafından kullanılması için.
     */
    protected AbstractCopyMoveProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix,
            String targetRepository,
            String targetBucket,
            String targetPrefix,
            TransferGroup externalGroup,
            boolean sourceRefreshRequired) {

        super(
                context,
                queue,
                repository,
                bucket,
                prefix,
                externalGroup);

        this.targetRepository = targetRepository;
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