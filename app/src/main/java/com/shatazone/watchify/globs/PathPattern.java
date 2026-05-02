package com.shatazone.watchify.globs;

import java.nio.file.Path;

public interface PathPattern {
    boolean matches(Path path);
}