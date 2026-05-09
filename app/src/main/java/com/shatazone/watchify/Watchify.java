package com.shatazone.watchify;

import com.shatazone.watchify.globs.GlobPathPattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Watchify {
    private final InspectionService inspectionService;
    private final PathRegistry pathRegistry;

    public Watchify(InspectionService inspectionService, PathRegistry pathRegistry) {
        this.inspectionService = inspectionService;
        this.pathRegistry = pathRegistry;
    }

    public void start() {
        inspectionService.startAsync().awaitRunning();
    }

    public PathRegistry.Subscription subscribe(String globPattern, FileEventListener fileEventListener) {
        final GlobPathPattern globPathPattern = GlobPathPattern.parse(globPattern);
        PathRegistry.Subscription subscribe = pathRegistry.subscribe(globPathPattern, fileEventListener);
        inspectionService.submit(new PathInspection("Watchify", globPathPattern.getDirectory(), true));
        return subscribe;
    }

    public void shutdown() {
        inspectionService.stopAsync().awaitTerminated();
    }
}