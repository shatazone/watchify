package com.shatazone.watchify.globs;

import lombok.RequiredArgsConstructor;

import java.nio.file.Path;

@RequiredArgsConstructor
public class FilteringPathPattern implements PathPattern {

    private final PathPattern include;
    private final PathPattern exclude;

    @Override
    public boolean matches(Path path) {
        return include.matches(path) && !exclude.matches(path);
    }
}