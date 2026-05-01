package com.shatazone.watchify;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.time.Duration;

@Slf4j
public class Main {
    public static void main(String[] args) throws IOException {
        final RealtimePathWatcher realtimePathWatcher = new RealtimePathWatcher(FileSystems.getDefault().newWatchService());
        final PathRegistry pathRegistry = new PathRegistry();
        final InspectionService inspectionService = new InspectionService(pathRegistry, realtimePathWatcher, Duration.ofSeconds(10));

        final Watchify watchify = new Watchify(inspectionService, pathRegistry);
        watchify.watch(Paths.get("E:\\ws-test"));
        watchify.start();

        watchify.setListener(new Listener() {

            @Override
            public void onFileEvent(FileEvent fileEvent) {
                log.info("Hello file event: {}", fileEvent);
            }
        });
    }
}
