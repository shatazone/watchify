package com.shatazone.watchify;

import com.google.common.util.concurrent.AbstractIdleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

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

            try (Stream<Path> pathStream = inspectDirectory(inspection, lastSeenState, currentFileState)) {
                pathStream.distinct().forEach(subPath -> {
                    submit(
                            new PathInspection(inspection.requester(), subPath, inspection.discoveryMode())
                    );
                });
            }

        } else {
            if (!pathRegistry.shouldWatchFile(inspection.path())) {
                // TODO cleanup ?
                return false;
            }

            final FileEvent fileEvent = inspectFile(inspection, lastSeenState, currentFileState);

            if (fileEvent == null) {
                return false;
            }

            fileEventStabilizer.stabilize(fileEvent)
                    .thenAccept(this::dispatch);
        }

        return true;
    }

    private Stream<Path> inspectDirectory(PathInspection inspection, FileState lastSeenState, FileState currentFileState) {
        final FileEventType fileEventType = analyzeEvent(lastSeenState, currentFileState, inspection.discoveryMode());

        if (fileEventType == FileEventType.CREATED || fileEventType == FileEventType.DISCOVERED) {
            try {
                realtimePathWatcher.watch(inspection.path());
                directoryMap.put(inspection.path(), new HashSet<>());
            } catch (final IOException e) {
                log.error("Error watching directory: {}", inspection.path(), e);
            }
        } else if (fileEventType == FileEventType.DELETED) {
            realtimePathWatcher.unwatch(inspection.path());
            directoryMap.remove(inspection.path());
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

        return Stream.concat(lastSeenSubPaths, currentlySeenSubPaths)
                .distinct();
    }

    private @Nullable FileEvent inspectFile(PathInspection inspection, FileState lastSeenState, FileState currentFileState) {
        final Path path = inspection.path();
        final FileEventType fileEventType = analyzeEvent(lastSeenState, currentFileState, inspection.discoveryMode());

        if (fileEventType == null) {
            return null;
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

        return new FileEvent(
                fileEventType,
                path,
                currentFileState,
                inspection.requester()
        );
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