package com.shatazone.watchify.globs;

import lombok.RequiredArgsConstructor;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
public class GlobPathPattern implements PathPattern {

    private final GlobPathPattern parentGlobPattern;
    private final String pattern;
    private final List<PathMatcher> matchers;

    @Override
    public boolean matches(Path path) {
        return matchers.stream().anyMatch(pathMatcher -> pathMatcher.matches(path));
    }

    private static String normalizeGlob(Path parentDir, String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");

        final boolean startsWithSlash = pattern.startsWith("/");
        final String fullPath = parentDir == null ? pattern : parentDir + (startsWithSlash ? "" : "/") + pattern;

        if (fullPath.isEmpty()) {
            return "**";
        }

        if (fullPath.equals("**")) {
            return "**";
        }

        String normalizedPattern = fullPath.replace("\\", "/");

        if (!normalizedPattern.contains("/")) {
            normalizedPattern = "**/" + normalizedPattern;
        }

        if (normalizedPattern.endsWith("/")) {
            normalizedPattern = normalizedPattern + "**";
        }

        return normalizedPattern;
    }

    public boolean matchesPrefix(Path path) {
        if (parentGlobPattern == null) {
            return false;
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
        return parse((Path) null, pattern);
    }

    public static GlobPathPattern parse(Path directory, String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");

        final String trimmedPattern = pattern.trim();

        if (trimmedPattern.isEmpty()) {
            return null;
        }

        final Set<String> roots = StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false)
                .map(Path::toString)
                .map(path -> path.replace("\\", "/"))
                .collect(Collectors.toSet());

        final String normalizedGlob = normalizeGlob(directory, trimmedPattern);

        return parse(roots, normalizedGlob);
    }

    private static List<PathMatcher> createPathMatchers(String normalizedGlob) {
        final List<PathMatcher> pathMatchers = new ArrayList<>();

        pathMatchers.add(FileSystems.getDefault().getPathMatcher("glob:" + normalizedGlob));

        if(normalizedGlob.endsWith("/")) {
            pathMatchers.add(FileSystems.getDefault().getPathMatcher("glob:" + normalizedGlob.substring(0, normalizedGlob.length() - 1)));
        }

        if (normalizedGlob.startsWith("**/")) {
            pathMatchers.add(FileSystems.getDefault().getPathMatcher("glob:" + normalizedGlob.substring("**/".length())));
        }

        if (normalizedGlob.endsWith("/**")) {
            pathMatchers.add(FileSystems.getDefault().getPathMatcher("glob:" + normalizedGlob.substring(0, normalizedGlob.length() - "/**".length())));
        }

        return pathMatchers;
    }

    // /data/apps/file.txt
    // /data/apps/file.txt
    private static GlobPathPattern parse(Set<String> roots, String pattern) {
        final String parentPattern = findParentPattern(roots, pattern);
        final GlobPathPattern parent = parentPattern == null ? null : parse(roots, parentPattern);
        final List<PathMatcher> pathMatchers = createPathMatchers(pattern);

        return new GlobPathPattern(parent, pattern, pathMatchers);
    }

    private static String findParentPattern(Set<String> roots, String pattern) {
        if(roots.contains(pattern)) {
            return null;
        }

        String parentPattern;

        if(pattern.endsWith("/")) {
            parentPattern = pattern.substring(0, pattern.length()-1);
        } else {
            parentPattern = pattern;
        }

        final int index = parentPattern.lastIndexOf("/");

        if (index == -1) {
            return null;
        }

        return parentPattern.substring(0, index+1);
    }
}