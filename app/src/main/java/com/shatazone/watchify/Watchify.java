package com.shatazone.watchify;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
public class Watchify {
    private final RealtimePathWatcher realtimePathWatcher;
    private final InspectionService inspectionService;

    public Watchify(RealtimePathWatcher realtimePathWatcher, InspectionService inspectionService) {
        this.realtimePathWatcher = realtimePathWatcher;
        this.inspectionService = inspectionService;

        realtimePathWatcher.setInspectionSink(this::onInspectionRequest);
    }

    public void setListener(Listener listener) {
        inspectionService.setListener(listener);
    }

    private void onInspectionRequest(PathInspection pathInspection) {
        try {
            inspectionService.submit(pathInspection);
        } catch (Exception e) {
            log.error("Inspection error", e);
        }
    }

    public void start() {
        realtimePathWatcher.startAsync().awaitRunning();
    }

    public void watch(Path rootPath) throws IOException {
        realtimePathWatcher.watch(rootPath);
    }

    public void unwatch(Path rootPath) {
        realtimePathWatcher.unwatch(rootPath);
    }

    public void shutdown() {
        realtimePathWatcher.stopAsync().awaitTerminated();
    }
}
