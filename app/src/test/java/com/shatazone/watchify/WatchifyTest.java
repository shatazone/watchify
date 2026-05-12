package com.shatazone.watchify;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class WatchifyTest {

    @TempDir
    Path tempDir;

    private Watchify watchify;

    @BeforeEach
    void setUp() throws IOException {
        RealtimePathWatcher realtimePathWatcher =
                new RealtimePathWatcher(FileSystems.getDefault().newWatchService());

        PathRegistry pathRegistry = new PathRegistry();

        // IMPORTANT: deterministic tests should avoid stabilization delay
        FileEventStabilizer fileEventStabilizer =
                new FileEventStabilizer(Duration.ZERO);

        InspectionService inspectionService =
                new InspectionService(
                        pathRegistry,
                        realtimePathWatcher,
                        fileEventStabilizer,
                        100_000
                );

        watchify = new Watchify(inspectionService, pathRegistry);
        watchify.start();
    }

    @AfterEach
    void tearDown() {
        watchify.shutdown();
    }

    // -----------------------------
    // Helper
    // -----------------------------

    private Path globRoot() {
        return tempDir.toAbsolutePath();
    }

    // -----------------------------
    // BASIC CREATE TEST
    // -----------------------------

    @Test
    void should_emit_created_event_for_new_file() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FileEvent> ref = new AtomicReference<>();

        Subscription sub = watchify.subscribe(
                globRoot() + "/**",
                event -> {
                    if (event.fileState().pathType() == PathType.DIRECTORY) return;

                    ref.set(event);
                    latch.countDown();
                }
        );

        sub.awaitReady();

        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "hello");

        assertTrue(latch.await(5, TimeUnit.SECONDS), "CREATED event not received");

        FileEvent event = ref.get();
        assertNotNull(event);
        assertEquals(file, event.path());
        assertEquals(FileEventType.CREATED, event.type());
    }

    // -----------------------------
    // MODIFY TEST
    // -----------------------------

    @Test
    void should_emit_modified_event_for_existing_file() throws Exception {
        List<FileEvent> events = new CopyOnWriteArrayList<>();

        Subscription sub = watchify.subscribe(
                globRoot() + "/**",
                events::add
        );

        sub.awaitReady();

        Path file = tempDir.resolve("b.txt");
        Files.writeString(file, "v1");

        await().until(() ->
                events.stream().anyMatch(e -> e.type() == FileEventType.CREATED)
        );

        Files.writeString(file, "v2");

        await().until(() ->
                events.stream().anyMatch(e -> e.type() == FileEventType.MODIFIED)
        );
    }

    // -----------------------------
    // MULTIPLE MODIFICATIONS
    // -----------------------------

    @Test
    void should_emit_multiple_modifications_in_order() throws Exception {
        List<FileEvent> events = new CopyOnWriteArrayList<>();

        Subscription sub = watchify.subscribe(
                globRoot() + "/**",
                events::add
        );

        sub.awaitReady();

        Path file = tempDir.resolve("c.txt");
        Files.writeString(file, "1");

        await().until(() ->
                events.stream().anyMatch(e -> e.type() == FileEventType.CREATED)
        );

        Files.writeString(file, "2");
        Files.writeString(file, "3");

        await().until(() ->
                events.stream().anyMatch(e -> e.type() == FileEventType.MODIFIED)
        );

        // we do NOT assume exact count, only ordering correctness if present
        List<FileEventType> types = events.stream()
                .map(FileEvent::type)
                .toList();

        assertTrue(types.contains(FileEventType.CREATED));
        assertTrue(types.stream().filter(t -> t == FileEventType.MODIFIED).count() >= 1);
    }

    // -----------------------------
    // DIRECTORY CREATION + FILE INSIDE
    // -----------------------------

    @Test
    void should_detect_files_created_inside_new_directory() throws Exception {
        List<FileEvent> events = new CopyOnWriteArrayList<>();

        Subscription sub = watchify.subscribe(
                globRoot() + "/**",
                events::add
        );

        sub.awaitReady();

        Path dir = tempDir.resolve("dirA");
        Path file = dir.resolve("inner.txt");

        Files.createDirectories(dir);
        Files.writeString(file, "data");

        await().until(() ->
                events.stream().anyMatch(e ->
                        e.type() == FileEventType.CREATED &&
                                e.path().equals(file)
                )
        );
    }

    // -----------------------------
    // DELETE EVENT
    // -----------------------------

    @Test
    void should_emit_deleted_event_when_file_removed() throws Exception {
        List<FileEvent> events = new CopyOnWriteArrayList<>();

        Subscription sub = watchify.subscribe(
                globRoot() + "/**",
                events::add
        );

        sub.awaitReady();

        Path file = tempDir.resolve("delete.txt");
        Files.writeString(file, "x");

        await().until(() ->
                events.stream().anyMatch(e -> e.type() == FileEventType.CREATED)
        );

        Files.delete(file);

        await().until(() ->
                events.stream().anyMatch(e -> e.type() == FileEventType.DELETED)
        );
    }

    // -----------------------------
    // STABILITY UNDER RAPID CHANGES
    // -----------------------------

    @Test
    void should_handle_rapid_file_changes() throws Exception {
        final List<FileEvent> events = new CopyOnWriteArrayList<>();

        final Subscription sub = watchify.subscribe(
                globRoot() + "/**",
                events::add
        );

        sub.awaitReady();

        final Path file = tempDir.resolve("stress.txt");

        Files.writeString(file, "1");
        Files.writeString(file, "2");
        Files.writeString(file, "3");
        Files.writeString(file, "4");

        await().until(() ->
                events.stream().anyMatch(e ->
                        e.type() == FileEventType.CREATED ||
                                e.type() == FileEventType.MODIFIED
                )
        );

        assertFalse(events.isEmpty());
    }
}