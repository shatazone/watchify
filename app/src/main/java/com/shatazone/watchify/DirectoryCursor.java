package com.shatazone.watchify;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

@RequiredArgsConstructor
public class DirectoryCursor implements AutoCloseable {

    private final Path directory;
    private final DirectoryStream<Path> stream;
    private final Iterator<Path> iterator;

    private boolean closed;

    public static DirectoryCursor open(Path directory) throws IOException {
        final DirectoryStream<Path> stream = Files.newDirectoryStream(directory);
        final Iterator<Path> iterator = stream.iterator();

        return new DirectoryCursor(directory, stream, iterator);
    }

    public Path directory() {
        return directory;
    }

    public boolean hasNext() {
        return iterator.hasNext();
    }

    public Path next() {
        return iterator.next();
    }

    public void closeQuietly() {
        if (!closed) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
            closed = true;
        }
    }

    @Override
    public void close() {
        closeQuietly();
    }
}