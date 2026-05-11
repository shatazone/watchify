package com.shatazone.watchify;

import com.google.common.util.concurrent.AbstractIdleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class InspectionService extends AbstractIdleService {
    private final Map<Path, FileState> fileStates = new ConcurrentHashMap<>();
    private final Map<Path, Set<Path>> directoryMap = new ConcurrentHashMap<>();

    private final FileStateFactory fileStateFactory = new FileStateFactory();
    private final PathRegistry pathRegistry;
    private final RealtimePathWatcher realtimePathWatcher;
    private final FileEventStabilizer fileEventStabilizer;

    public boolean submit(PathInspection inspection) {
        final FileState currentFileState = fileStateFactory.getFileState(inspection.path());
        final FileState lastSeenState = fileStates.get(inspection.path());
        final boolean directory = (currentFileState != null && currentFileState.isDirectory())
                || (lastSeenState != null && lastSeenState.isDirectory());

        if (directory) {
            if (!pathRegistry.shouldWatchDirectory(inspection.path())) {
                // TODO state cleanup ?
                return false;
            }

            final FileEventType fileEventType = analyzeEvent(lastSeenState, currentFileState, inspection.discoveryMode());

            if (fileEventType == FileEventType.DELETED) {
                realtimePathWatcher.unwatch(inspection.path());
                directoryMap.remove(inspection.path());
            } else if (fileEventType == FileEventType.CREATED || fileEventType == FileEventType.DISCOVERED) {
                try {
                    realtimePathWatcher.watch(inspection.path());
                    directoryMap.put(inspection.path(), new HashSet<>());
                } catch (final IOException e) {
                    log.error("Error watching directory: {}", inspection.path(), e);
                }
            }

            Stream<Path> lastSeenSubPaths = directoryMap.getOrDefault(inspection.path(), Collections.emptySet()).stream();
            Stream<Path> currentlySeenSubPaths;

            try {
                currentlySeenSubPaths = Files.list(inspection.path());
            } catch (NoSuchFileException e) {
                currentlySeenSubPaths = Stream.empty();
            } catch (IOException e) {
                log.warn("Error listing files in path {}", inspection.path(), e);
                currentlySeenSubPaths = Stream.empty();
            }

            try (Stream<Path> subPaths = Stream.concat(lastSeenSubPaths, currentlySeenSubPaths)) {
                subPaths.distinct().forEach(subPath -> {
                    submit(new PathInspection(inspection.requester(), subPath, inspection.discoveryMode()));
                });
            }

        } else {
            if (!pathRegistry.shouldWatchFile(inspection.path())) {
                // TODO cleanup ?
                return false;
            }

            final FileEventType fileEventType = analyzeEvent(lastSeenState, currentFileState, inspection.discoveryMode());

            if (fileEventType == FileEventType.DELETED) {
                fileStates.remove(inspection.path());
                directoryMap.get(inspection.path().getParent()).remove(inspection.path());

                if (directoryMap.get(inspection.path().getParent()).isEmpty()) {
                    realtimePathWatcher.unwatch(inspection.path());
                }
            } else {
                fileStates.put(inspection.path(), currentFileState);
                directoryMap.get(inspection.path().getParent()).add(inspection.path());
            }

            if (fileEventType == null) {
                return false;
            }

            final FileEvent newFileEvent = new FileEvent(fileEventType, inspection.path(), currentFileState, inspection.requester());

            fileEventStabilizer.stabilize(newFileEvent)
                    .thenAccept(fileEvent -> {
                        dispatch(newFileEvent);
                    });


        }
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
        realtimePathWatcher.setInspectionSink(this::submit);
        realtimePathWatcher.startAsync().awaitRunning();
    }

    @Override
    protected void shutDown() {
        realtimePathWatcher.stopAsync().awaitTerminated();
    }
}