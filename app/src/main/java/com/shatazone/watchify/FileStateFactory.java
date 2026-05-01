package com.shatazone.watchify;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

@Slf4j
public class FileStateFactory {

    public FileState getFileState(Path file) {
        final BasicFileAttributes basicFileAttributes;

        try {
            basicFileAttributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            log.trace("No such file or directory: {}", file);
            return null;
        } catch (IOException e) {
            log.error("Error reading file: {}", file, e);
            return null;
        }

        return new FileState(
                basicFileAttributes.lastModifiedTime().toMillis(),
                basicFileAttributes.size(),
                basicFileAttributes.isDirectory() ? PathType.DIRECTORY : PathType.FILE
        );
    }
}
