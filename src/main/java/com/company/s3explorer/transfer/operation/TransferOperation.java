package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.context.TransferContext;

public interface TransferOperation {
    void execute(TransferRuntime runtime, TransferContext transferContext) throws Exception;
}