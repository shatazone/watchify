package com.shatazone.watchify.globs;

import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.List;

@RequiredArgsConstructor
public class CompositePathPattern implements PathPattern {

    private final List<PathPattern> patterns;

    @Override
    public boolean matches(Path path) {
        return patterns.stream().anyMatch(p -> p.matches(path));
    }
}