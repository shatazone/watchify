package com.shatazone.watchify;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
public class InspectionService extends AbstractExecutionThreadService {
    private final Map<Path, FileState> fileStates = new ConcurrentHashMap<>();
    private final Map<Path, Set<Path>> directoryMap = new ConcurrentHashMap<>();

    private final FileStateFactory fileStateFactory = new FileStateFactory();
    private final PathRegistry pathRegistry;
    private final RealtimePathWatcher realtimePathWatcher;
    private final FileEventStabilizer fileEventStabilizer;

    private final BlockingQueue<PendingInspection> inspectionQueue;
    private final Set<Path> dedup = new HashSet<>();

    private final TraversalEngine traversalEngine;

    public InspectionService(PathRegistry pathRegistry, RealtimePathWatcher realtimePathWatcher, FileEventStabilizer fileEventStabilizer, int queueCapacity) {
        this.pathRegistry = pathRegistry;
        this.realtimePathWatcher = realtimePathWatcher;
        this.fileEventStabilizer = fileEventStabilizer;
        this.inspectionQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.traversalEngine = new TraversalEngine(inspectionQueue, 10);
    }

    @Override
    protected void run() throws InterruptedException {
        while (isRunning()) {
            final PendingInspection pendingInspection = inspectionQueue.poll(200, TimeUnit.MILLISECONDS);

            if (pendingInspection == null) {
                continue;
            }

            final Inspection inspection = pendingInspection.inspection();

            try {
                inspect(pendingInspection);
            } finally {
                dedup.remove(inspection.path());
                pendingInspection.scope().arrive();
            }
        }
    }

    public CompletableFuture<Void> enqueue(Inspection inspection) {
        final InspectionScope scope = new InspectionScope();
        enqueue(new PendingInspection(inspection, scope));
        return scope.future();
    }

    private void enqueue(PendingInspection pendingInspection) {
        final Inspection inspection = pendingInspection.inspection();
        final Path path = inspection.path();

        if (!dedup.add(path)) {
            log.debug("Duplicate inspection ignored: {}", inspection);
            return;
        }

        final FileState currentFileState = fileStateFactory.getFileState(path);
        final FileState lastSeenState = fileStates.get(path);

        final boolean isDirectory = isDirectory(currentFileState, lastSeenState);

        final boolean shouldInspect =
                isDirectory
                        ? pathRegistry.shouldWatchDirectory(path)
                        : pathRegistry.shouldWatchFile(path);

        if (!shouldInspect) {
            log.trace(
                    "Inspection ignored because path is not watched: {}",
                    inspection
            );
            dedup.remove(path);
            return;
        }

        pendingInspection.scope().register();
        if (!enqueueWithRetry(pendingInspection)) {
            dedup.remove(path);
            pendingInspection.scope().arrive();
        }
    }


    private boolean enqueueWithRetry(PendingInspection inspection) {

        try {

            for (int attempt = 0; attempt < 3; attempt++) {
                if (inspectionQueue.offer(
                        inspection,
                        100,
                        TimeUnit.MILLISECONDS
                )) {
                    return true;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            log.debug(
                    "Interrupted while enqueueing inspection: {}",
                    inspection
            );

            return false;
        }

        log.warn(
                "Inspection queue saturated, dropping inspection: {}",
                inspection
        );

        return false;
    }


    private boolean inspect(PendingInspection pendingInspection) {
        final Inspection inspection = pendingInspection.inspection();
        final FileState currentFileState = fileStateFactory.getFileState(inspection.path());
        final FileState lastSeenState = fileStates.get(inspection.path());
        final boolean directory = isDirectory(currentFileState, lastSeenState);

        if (directory) {
            return inspectDirectory(pendingInspection, lastSeenState, currentFileState);
        } else {
            return inspectFile(inspection, lastSeenState, currentFileState);
        }
    }

    private boolean inspectDirectory(PendingInspection inspection, FileState lastSeenState, FileState currentFileState) {
        final Path path = inspection.inspection().path();
        if (!pathRegistry.shouldWatchDirectory(path)) {
            // TODO state cleanup ?
            return false;
        }

        final FileEventType fileEventType = analyzeEvent(lastSeenState, currentFileState, inspection.inspection().discoveryMode());

        if (fileEventType == FileEventType.CREATED || fileEventType == FileEventType.DISCOVERED) {
            try {
                realtimePathWatcher.watch(path);
                directoryMap.putIfAbsent(path, new HashSet<>());
            } catch (final IOException e) {
                log.error("Error watching directory: {}", path, e);
            }
        } else if (fileEventType == FileEventType.DELETED) {
            realtimePathWatcher.unwatch(path);
            directoryMap.remove(path);
        }

        traversalEngine.submitDirectory(inspection);
        return true;
    }

    private boolean inspectFile(Inspection inspection, FileState lastSeenState, FileState currentFileState) {
        if (!pathRegistry.shouldWatchFile(inspection.path())) {
            // TODO cleanup ?
            return false;
        }

        final Path path = inspection.path();
        final FileEventType fileEventType = analyzeEvent(lastSeenState, currentFileState, inspection.discoveryMode());

        if (fileEventType == null) {
            return false;
        }

        final Path parentPath = path.getParent();
        final Set<Path> directoryEntries = directoryMap.get(parentPath);

        if (fileEventType == FileEventType.DELETED) {
            fileStates.remove(path);
            directoryEntries.remove(path);

        } else {
            fileStates.put(path, currentFileState);
            directoryEntries.add(path);
        }

        FileEvent fileEvent = new FileEvent(
                fileEventType,
                path,
                currentFileState,
                inspection.requester()
        );

        fileEventStabilizer.stabilize(fileEvent)
                .thenAccept(this::dispatch);

        return true;
    }

    private void dispatch(FileEvent fileEvent) {
        for (FileEventListener fileEventListener : pathRegistry.getListenersOf(fileEvent.path())) {
            try {
                fileEventListener.onFileEvent(fileEvent);
            } catch (Exception e) {
                log.error("Error while dispatching FileEvent: {}", fileEvent, e);
            }
        }
    }

    private static FileEventType analyzeEvent(FileState lastSeenState, FileState currentFileState, boolean discoveryMode) {
        if (lastSeenState == null && currentFileState != null) {
            // created or discovered
            return discoveryMode ? FileEventType.DISCOVERED : FileEventType.CREATED;
        }

        if (lastSeenState != null && currentFileState == null) {
            // deleted
            return FileEventType.DELETED;
        }

        if (!Objects.equals(lastSeenState, currentFileState)) {
            // modified
            return FileEventType.MODIFIED;
        }

        // ignore
        return null;
    }

    @Override
    protected void startUp() {
        realtimePathWatcher.setInspectionSink(this::enqueue);
        realtimePathWatcher.startAsync().awaitRunning();
    }

    @Override
    protected void shutDown() {
        realtimePathWatcher.stopAsync().awaitTerminated();
        traversalEngine.shutdown();
    }

    private static boolean isDirectory(FileState currentFileState, FileState lastSeenState) {
        return (currentFileState != null && currentFileState.isDirectory())
                || (lastSeenState != null && lastSeenState.isDirectory());
    }

    public record PendingInspection(Inspection inspection, InspectionScope scope) {

    }
}