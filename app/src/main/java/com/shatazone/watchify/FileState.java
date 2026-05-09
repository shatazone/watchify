package com.shatazone.watchify;

public record FileState(
        long lastModifiedMillis,
        long size,
        PathType pathType) {

    public boolean isDirectory() {
        return pathType == PathType.DIRECTORY;
    }

    public boolean isFile() {
        return pathType == PathType.FILE;
    }
}
