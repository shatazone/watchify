package com.shatazone.watchify;

import com.shatazone.watchify.globs.GlobPathPattern;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

        final List<CompletableFuture<Void>> startupFutures = new ArrayList<>();

        for (Path monitoredRoot : pathRegistry.getMonitoredRoots()) {
            final CompletableFuture<Void> future =
                    inspectionService.enqueue(
                            new Inspection("Watchify", monitoredRoot, true)
                    );

            startupFutures.add(future);
        }

        CompletableFuture.allOf(
                        startupFutures.toArray(CompletableFuture[]::new)
                )
                .orTimeout(5, TimeUnit.MINUTES)
                .join();
    }

    public Subscription subscribe(String globPattern, FileEventListener fileEventListener) {
        final GlobPathPattern globPathPattern = GlobPathPattern.parse(globPattern);
        pathRegistry.register(globPathPattern, fileEventListener);

        final CompletableFuture<Void> future;
        if(inspectionService.isRunning()) {
            future = inspectionService.enqueue(new Inspection("Watchify", globPathPattern.getDirectory(), true));
        } else {
            future = CompletableFuture.completedFuture(null);
        }

        return new Subscription() {
            @Override
            public void awaitReady() {
                future.join();
            }

            @Override
            public void cancel() {
                pathRegistry.unregister(globPathPattern, fileEventListener);
            }
        };
    }

    public void shutdown() {
        inspectionService.stopAsync().awaitTerminated();
    }
}