package com.shatazone.watchify.globs;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GlobPathPatternParserTest {

    @Test
    void shouldSplitSimpleGlob() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("/var/log/*.log");

        assertEquals("/var/log/", result.pathSection());
        assertEquals("*.log", result.globSection());
    }

    @Test
    void shouldSplitRecursiveGlob() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("/home/**/test?.txt");

        assertEquals("/home/", result.pathSection());
        assertEquals("**/test?.txt", result.globSection());
    }

    @Test
    void shouldHandlePathWithoutGlob() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("src/main/java");

        assertEquals("src/main/", result.pathSection());
        assertEquals("java", result.globSection());
    }

    @Test
    void shouldHandleGlobOnly() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("**/*.java");

        assertNull(result.pathSection());
        assertEquals("**/*.java", result.globSection());
    }

    @Test
    void shouldHandleSingleFileGlob() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("*.txt");

        assertNull(result.pathSection());
        assertEquals("*.txt", result.globSection());
    }

    @Test
    void shouldHandleQuestionMarkGlob() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("logs/file?.txt");

        assertEquals("logs/", result.pathSection());
        assertEquals("file?.txt", result.globSection());
    }

    @Test
    void shouldHandleCharacterClassGlob() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("src/[a-z]*.java");

        assertEquals("src/", result.pathSection());
        assertEquals("[a-z]*.java", result.globSection());
    }

    @Test
    void shouldHandleBraceExpansionGlob() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("src/*.{java,kt}");

        assertEquals("src/", result.pathSection());
        assertEquals("*.{java,kt}", result.globSection());
    }

    @Test
    void shouldNormalizeWindowsSeparators() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("C:\\temp\\**\\*.log");

        assertEquals("C:/temp/", result.pathSection());
        assertEquals("**/*.log", result.globSection());
    }

    @Test
    void shouldHandleEmptyInput() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("");

        assertNull(result.pathSection());
        assertEquals("", result.globSection());
    }

    @Test
    void shouldHandleNullInput() {
        assertThrows(NullPointerException.class, () -> GlobPathPatternParser.splitGlob(null));
    }

    @Test
    void shouldHandleAbsoluteGlobAtRoot() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("/**/*.java");

        assertEquals("/", result.pathSection());
        assertEquals("**/*.java", result.globSection());
    }

    @Test
    void shouldHandleGlobInMiddleOfPath() {

        GlobPathPatternParser.SplitGlob result =
                GlobPathPatternParser.splitGlob("src/*/test/*.java");

        assertEquals("src/", result.pathSection());
        assertEquals("*/test/*.java", result.globSection());
    }

    @Test
    void shouldReturnOriginalPatternWhenNoDoubleStarExists() {
        final Set<String> globVariants = GlobPathPatternParser.generateGlobVariants("c:/dir1/dir2/file.txt");

        assertEquals(
                Set.of("c:/dir1/dir2/file.txt"),
                globVariants
        );
    }

    @Test
    void shouldGenerateVariantWithoutIntermediateDoubleStar() {
        final Set<String> globVariants = GlobPathPatternParser.generateGlobVariants("c:/dir1/**/file.txt");
        assertEquals(
                Set.of(
                        "c:/dir1/file.txt",
                        "c:/dir1/**/file.txt"
                ),
                globVariants
        );
    }

    @Test
    void shouldGenerateVariantWithoutTrailingDoubleStar() {
        final Set<String> globVariants = GlobPathPatternParser.generateGlobVariants("c:/dir1/**");
        assertEquals(
                Set.of(
                        "c:/dir1/**",
                        "c:/dir1"
                ),
                globVariants
        );
    }

    @Test
    void shouldGenerateVariantWithoutLeadingDoubleStar() {
        final Set<String> globVariants = GlobPathPatternParser.generateGlobVariants("**/dir1/dir2/file.txt");
        assertEquals(
                Set.of(
                        "**/dir1/dir2/file.txt",
                        "dir1/dir2/file.txt"
                ),
                globVariants);
    }

    @Test
    void shouldHandleUnixRootWithDoubleStar() {
        final Set<String> globVariants =
                GlobPathPatternParser.generateGlobVariants("/**");

        assertEquals(
                Set.of(
                        "/**",
                        "/"
                ),
                globVariants
        );
    }

    @Test
    void shouldHandleWindowsDriveRootWithDoubleStar() {
        final Set<String> globVariants =
                GlobPathPatternParser.generateGlobVariants("c:/**");

        assertEquals(
                Set.of(
                        "c:/**",
                        "c:/"
                ),
                globVariants
        );
    }

    @Test
    void shouldHandleEmptyPattern() {
        final Set<String> globVariants =
                GlobPathPatternParser.generateGlobVariants("");

        assertEquals(
                Set.of(""),
                globVariants
        );
    }

    @Test
    void shouldThrowWhenPatternIsNull() {
        assertThrows(
                NullPointerException.class,
                () -> GlobPathPatternParser.generateGlobVariants(null)
        );
    }
}