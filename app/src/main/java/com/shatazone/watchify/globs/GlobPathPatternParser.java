package com.shatazone.watchify.globs;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GlobPathPatternParser {

    public static GlobPathPattern parse(String pattern) {
        final String normalizedPattern = normalizePattern(pattern);

        return buildGlobPathPattern(normalizedPattern);
    }

    static SplitGlob splitGlob(String glob) {
        Objects.requireNonNull(glob, "glob cannot be null");

        if (glob.isEmpty()) {
            return new SplitGlob(null, "");
        }

        final String normalizedGlob = glob.replace('\\', '/');
        int lastSlashIndex = -1;

        for (int i = 0; i < normalizedGlob.length(); i++) {
            char c = normalizedGlob.charAt(i);

            if (c == '/') {
                lastSlashIndex = i;
                continue;
            }

            if (isGlobChar(c)) {
                break;
            }
        }

        final String pathSection = lastSlashIndex == -1 ? null : normalizedGlob.substring(0, lastSlashIndex + 1);
        final String globSection = normalizedGlob.substring(lastSlashIndex + 1);

        return new SplitGlob(pathSection, globSection);
    }

    private static boolean isGlobChar(char c) {
        return switch (c) {
            case '*', '?', '[', ']', '{', '}' -> true;
            default -> false;
        };
    }

    static GlobPathPattern buildGlobPathPattern(String globPattern) {
        if (globPattern == null) {
            return null;
        }

        final String parentDirectoryPattern = getParentDirectoryPattern(globPattern);
        final GlobPathPattern parentPattern = buildGlobPathPattern(parentDirectoryPattern);
        final List<PathMatcher> pathMatchers = createPathMatchers(globPattern);
        final GlobPathPatternParser.SplitGlob splitGlob = GlobPathPatternParser.splitGlob(globPattern);

        return new GlobPathPattern(parentPattern, splitGlob.getPath(), splitGlob.globSection(), pathMatchers);
    }

    static String normalizePattern(String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");


        pattern = pattern.trim().replace('\\', '/');

        if (pattern.isEmpty() || pattern.equals("**")) {
            return "**";
        }

        final int lastIndexOfSlash = pattern.lastIndexOf('/');

        if (lastIndexOfSlash == -1) {
            return "**/" + pattern;
        }

        if (lastIndexOfSlash == pattern.length() - 1) {
            return pattern + "**";
        }

        return pattern;
    }

    private static String getParentDirectoryPattern(String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");

        final String normalized = pattern.endsWith("/")
                ? pattern.substring(0, pattern.length() - 1)
                : pattern;

        final int index = normalized.lastIndexOf("/");

        if (index < 0) {
            return null;
        }

        final String parent = normalized.substring(0, index + 1);

        return parent.matches("(\\w:)?/")
                ? parent
                : parent.substring(0, parent.length() - 1);
    }

    private static List<PathMatcher> createPathMatchers(String normalizedGlob) {
        return generateGlobVariants(normalizedGlob).stream()
                .map(globVariant -> "glob:" + globVariant)
                .map(FileSystems.getDefault()::getPathMatcher)
                .collect(Collectors.toList());
    }

    public static Set<String> generateGlobVariants(String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");

        final Set<String> permutations = new HashSet<>();

        final List<String> parts = new ArrayList<>();

        int start = 0;

        // Windows drive root
        if (pattern.matches("^[A-Za-z]:/.*")) {
            parts.add(pattern.substring(0, 3)); // C:/
            start = 3;
        }
        // Unix root
        else if (pattern.startsWith("/")) {
            parts.add("/");
            start = 1;
        }

        final String remaining = pattern.substring(start);

        if (!remaining.isEmpty()) {
            parts.addAll(Arrays.asList(remaining.split("/")));
        }

        generateGlobVariants(parts, 0, "", permutations);
        return permutations;
    }

    private static void generateGlobVariants(List<String> parts, int index, String prefix, Set<String> permutations) {
        if (index >= parts.size()) {
            permutations.add(prefix);
            return;
        }

        final String segment = parts.get(index);
        final String newPrefix = join(prefix, segment, "/");

        generateGlobVariants(parts, index + 1, newPrefix, permutations);

        if (segment.equals("**")) {
            generateGlobVariants(parts, index + 1, prefix, permutations);
        }
    }

    private static String join(String part1, String part2, String delimiter) {
        if(part1.isEmpty()) {
            return part2;
        }

        if(part2.isEmpty()) {
            return part1;
        }

        if(part1.endsWith(delimiter) || part2.startsWith(delimiter)) {
            return part1 + part2;
        }

        return part1 + delimiter + part2;
    }

    public record SplitGlob(String pathSection, String globSection) {
        public Path getPath() {
            if (pathSection == null) {
                return null;
            }

            return Path.of(pathSection);
        }
    }
}