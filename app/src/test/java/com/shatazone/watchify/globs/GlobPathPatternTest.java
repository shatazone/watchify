package com.shatazone.watchify.globs;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GlobPathPatternTest {

    // =========================
    // Exact matching
    // =========================

    @Test
    void exactMatch_shouldMatchSamePath() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("C:/data/dir/file.txt");

        assertTrue(glob.matches(Path.of("C:/data/dir/file.txt")));
    }

    @Test
    void exactMatch_shouldNotMatchDifferentFile() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("C:/data/dir/file.txt");

        assertFalse(glob.matches(Path.of("C:/data/dir/file2.txt")));
    }

    // =========================
    // Simple wildcard (*)
    // =========================

    @Test
    void wildcard_shouldMatchSingleSegment() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("C:/data/dir/*.txt");

        assertTrue(glob.matches(Path.of("C:/data/dir/file.txt")));
        assertTrue(glob.matches(Path.of("C:/data/dir/abc.txt")));
    }

    @Test
    void wildcard_shouldNotMatchDifferentExtension() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("C:/data/dir/*.txt");

        assertFalse(glob.matches(Path.of("C:/data/dir/file.log")));
    }

    @Test
    void wildcard_shouldNotCrossDirectories() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("C:/data/dir/*.txt");

        assertFalse(glob.matches(Path.of("C:/data/dir/sub/file.txt")));
    }

    // =========================
    // Global wildcard (*.txt)
    // =========================

    @Test
    void globalWildcard_shouldMatchAnywhere() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("*.txt");

        assertTrue(glob.matches(Path.of("file.txt")));
        assertTrue(glob.matches(Path.of("C:/data/file.txt")));
        assertTrue(glob.matches(Path.of("C:/data/dir/file.txt")));
    }

    @Test
    void globalWildcard_shouldNotMatchOtherExtensions() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("*.txt");

        assertFalse(glob.matches(Path.of("file.log")));
    }

    // =========================
    // Prefix matching (core feature)
    // =========================

    @Test
    void prefix_shouldMatchAllAncestors_exactPath() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("C:/data/dir/file.txt");

        assertTrue(glob.matchesPrefix(Path.of("C:/data/dir")));
        assertTrue(glob.matchesPrefix(Path.of("C:/data")));
        assertTrue(glob.matchesPrefix(Path.of("C:/")));
    }

    @Test
    void prefix_shouldNotMatchUnrelatedPath() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("C:/data/dir/file.txt");

        assertFalse(glob.matchesPrefix(Path.of("C:/other")));
    }

    // =========================
    // Prefix with wildcard
    // =========================

    @Test
    void prefix_withWildcard_shouldAllowValidBranches() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("logs/dev/*/dir/*.txt");

        assertTrue(glob.matchesPrefix(Path.of("logs")));
        assertTrue(glob.matchesPrefix(Path.of("logs/dev")));
        assertTrue(glob.matchesPrefix(Path.of("logs/dev/token")));
        assertTrue(glob.matchesPrefix(Path.of("logs/dev/token/dir")));
    }

    @Test
    void prefix_withWildcard_shouldRejectInvalidBranches() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("logs/dev/*/dir/*.txt");

        assertFalse(glob.matchesPrefix(Path.of("logs/prod")));
        assertFalse(glob.matchesPrefix(Path.of("logs/dev/token/invalid")));
    }

    // =========================
    // Full match vs prefix boundary
    // =========================

    @Test
    void shouldMatchExactFileButNotExtraSegments() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("logs/dev/*/dir/*.txt");

        assertTrue(glob.matches(Path.of("logs/dev/token/dir/file.txt")));

        // extra segment beyond match → invalid
        assertFalse(glob.matches(Path.of("logs/dev/token/dir/file.txt/extra")));
    }

    @Test
    void prefix_shouldRejectInvalidContinuation() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("logs/dev/*/dir/*.txt");

        assertFalse(glob.matchesPrefix(Path.of("logs/dev/token/dir/invalid")));
    }

    // =========================
    // Edge cases
    // =========================

    @Test
    void rootOnly_shouldBehaveCorrectly() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("C:/");

        assertTrue(glob.matchesPrefix(Path.of("C:/")));
        assertFalse(glob.matchesPrefix(Path.of("C:/data")));
    }

    @Test
    void emptyOrInvalidPattern_shouldReturnNull() {
        final GlobPathPattern globPathPattern = GlobPathPatternParser.parse("");
        assertEquals("**", globPathPattern.getPattern());
    }

    @Test
    void noSlashPattern_shouldStillWork() {
        final GlobPathPattern glob = GlobPathPatternParser.parse("file.txt");

        assertTrue(glob.matches(Path.of("file.txt")));
        assertTrue(glob.matches(Path.of("C:/data/file.txt")));
    }

    @Test
    void recursiveWildcardShouldMatchBaseDirectory() {
        final GlobPathPattern glob =
                GlobPathPatternParser.parse("c:/dir/**");

        assertTrue(glob.matches(Path.of("c:/dir")));
    }

    @Test
    void recursiveWildcardShouldMatchDescendantPaths() {
        final GlobPathPattern glob =
                GlobPathPatternParser.parse("c:/dir/**");

        assertTrue(glob.matches(Path.of("C:/dir/file.txt")));
        assertTrue(glob.matches(Path.of("C:/dir/dir2/file.txt")));
    }

    @Test
    void recursiveWildcardShouldMatchZeroOrMoreDirectories() {
        final GlobPathPattern glob =
                GlobPathPatternParser.parse("c:/dir/**/dir2/file.txt");

        assertTrue(glob.matches(Path.of("C:/dir/ddd/dir2/file.txt")));
        assertTrue(glob.matches(Path.of("C:/dir/dir2/file.txt")));
        assertFalse(glob.matches(Path.of("C:/dir/file.txt")));
        assertFalse(glob.matches(Path.of("C:/dir/ddd/file.txt")));
        assertFalse(glob.matches(Path.of("C:/dir/ddd/dir3/file.txt")));
    }
}