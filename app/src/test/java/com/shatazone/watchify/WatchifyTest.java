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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class WatchifyTest {

    @TempDir
    private Path tempDir;

    private Watchify watchify;

    @BeforeEach
    void setUp() throws IOException {
        final RealtimePathWatcher realtimePathWatcher = new RealtimePathWatcher(FileSystems.getDefault().newWatchService());
        final InspectionService inspectionService = new InspectionService(Duration.ofSeconds(5));

        watchify = new Watchify(realtimePathWatcher, inspectionService);
        watchify.start();
    }

    @AfterEach
    void tearDown() {
        watchify.shutdown();
        watchify.setListener(null);
    }

    @Test
    void test1() throws IOException, InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicReference<FileEvent> fileEventRef = new AtomicReference<>();

        watchify.setListener(fileEvent -> {
            fileEventRef.set(fileEvent);
            countDownLatch.countDown();
        });

        watchify.watch(tempDir);

        final Path newFile = tempDir.resolve("myfile.txt");
        Files.writeString(newFile, "hello test");

        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS), "Did not receive the file event");
        FileEvent fileEvent = fileEventRef.get();
        assertNotNull(fileEvent);
        assertEquals(newFile, fileEvent.path());
        assertEquals(FileEventType.CREATED, fileEvent.type());
    }

    @Test
    void test2() throws IOException {
        watchify.watch(tempDir);

        final List<FileEvent> fileEventList = new ArrayList<>();

        watchify.setListener(fileEventList::add);


        final Path newFile = tempDir.resolve("myfile.txt");
        Files.writeString(newFile, "hello test");


        await().until(() -> fileEventList.size() == 1);
        assertEquals(FileEventType.CREATED, fileEventList.get(0).type());

        Files.writeString(newFile, "new text");

        await().until(() -> fileEventList.size() == 2);
        assertEquals(FileEventType.MODIFIED, fileEventList.get(1).type());
    }
}