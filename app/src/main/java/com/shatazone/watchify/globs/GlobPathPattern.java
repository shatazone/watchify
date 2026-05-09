package com.shatazone.watchify.globs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

@RequiredArgsConstructor
public class GlobPathPattern implements PathPattern {

    private final GlobPathPattern parentGlobPattern;

    @Getter
    private final Path directory;

    @Getter
    private final String pattern;

    private final List<PathMatcher> matchers;

    @Override
    public boolean matches(Path path) {
        return matchers.stream().anyMatch(pathMatcher -> pathMatcher.matches(path));
    }

    public boolean matchesPrefix(Path path) {
        if (parentGlobPattern == null) {
            return false;   // TODO return true if pattern is **
        }

        if (parentGlobPattern.matches(path)) {
            return true;
        }

        return parentGlobPattern.matchesPrefix(path);
    }

    @Override
    public String toString() {
        return "GlobPathPattern[" + pattern + "]";
    }

    public static GlobPathPattern parse(String pattern) {
        return GlobPathPatternParser.parse(pattern);
    }
}