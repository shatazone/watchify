package com.shatazone.watchify;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.time.Duration;

@Slf4j
public class Main {
    public static void main(String[] args) throws IOException {
        final RealtimePathWatcher realtimePathWatcher = new RealtimePathWatcher(FileSystems.getDefault().newWatchService());
        final PathRegistry pathRegistry = new PathRegistry();
        final InspectionService inspectionService = new InspectionService(pathRegistry, realtimePathWatcher, Duration.ofSeconds(10));

        final Watchify watchify = new Watchify(inspectionService, pathRegistry);
        final PathRegistry.Subscription subscription = watchify.subscribe("E:/ws-test/**/*.trigger", fileEvent -> {
            log.info(">> Captured file event: {}", fileEvent);
        });

        watchify.start();
        log.info("Started watching file event");
    }
}
