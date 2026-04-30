package com.shatazone.watchify;

public record FileState(
        long lastModifiedMillis,
        long size,
        PathType pathType
) {
}
