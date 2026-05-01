package com.shatazone.watchify;

import java.nio.file.Path;

public record FileEvent(
        FileEventType type,
        Path path,
        FileState fileState,
        String source) {
}
