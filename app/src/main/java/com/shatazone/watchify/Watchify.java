package com.shatazone.watchify;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
public class Watchify {
    private final InspectionService inspectionService;
    private final PathRegistry pathRegistry;

    public Watchify(InspectionService inspectionService, PathRegistry pathRegistry) {
        this.inspectionService = inspectionService;
        this.pathRegistry = pathRegistry;
    }

    public void setListener(Listener listener) {
        inspectionService.setListener(listener);
    }

    public void start() {
        inspectionService.startAsync().awaitRunning();
    }

    public void watch(Path rootPath) {
        if(pathRegistry.addRoot(rootPath)) {
            inspectionService.submit(new PathInspection("Watchify", rootPath, true));
        }
    }

    public void unwatch(Path rootPath) {
        if(pathRegistry.removeRoot(rootPath)) {
            inspectionService.submit(new PathInspection("Watchify", rootPath,  false));
        }
    }

    public void shutdown() {
        inspectionService.stopAsync().awaitTerminated();
    }
}
