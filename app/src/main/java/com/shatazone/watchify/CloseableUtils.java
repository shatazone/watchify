package com.shatazone.watchify;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;

@Slf4j
@UtilityClass
public class CloseableUtils {
    static public void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (Exception e) {
            log.trace("Exception while closing Closeable", e);
        }
    }
}