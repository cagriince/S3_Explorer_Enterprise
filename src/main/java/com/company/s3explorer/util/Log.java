package com.company.s3explorer.util;

import java.util.logging.Logger;

public final class Log {

    public static Logger of(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());

    }
}
