package com.company.s3explorer.transfer.factory;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.operation.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class TransferOperationFactory {

    private final Map<TransferType, TransferOperation> operations = new EnumMap<>(TransferType.class);

    public TransferOperationFactory() {
        register(TransferType.UPLOAD, new UploadOperation());
        register(TransferType.DOWNLOAD, new DownloadOperation());
        register(TransferType.DELETE, new DeleteOperation());
        register(TransferType.COPY, new CopyOperation());
        register(TransferType.MOVE, new MoveOperation());
        register(TransferType.CREATE_FOLDER, new CreateFolderOperation());
    }

    private void register(TransferType type, TransferOperation operation) {
        operations.put(type, operation);
    }

    public TransferOperation get(TransferType type) {
        return Objects.requireNonNull(operations.get(type), "No TransferOperation registered for " + type);
    }
}
