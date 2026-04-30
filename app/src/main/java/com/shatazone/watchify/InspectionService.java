package com.shatazone.watchify;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
public class InspectionService {
    private final Map<Path, FileState> fileStates = new ConcurrentHashMap<>();
    private final FileStateFactory fileStateFactory = new FileStateFactory();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final Map<Path, PendingEvent> stabilizedFutures = new ConcurrentHashMap<>();

    private final Duration stabilizationDelay;

    @Setter
    private Listener listener;

    public void submit(PathInspection inspection) throws IOException {
        final FileState currentFileState = fileStateFactory.getFileState(inspection.path());
        final FileState lastSeenState = currentFileState == null
                ? fileStates.remove(inspection.path())
                : fileStates.put(inspection.path(), currentFileState);

        final FileEventType fileEventType = analyzeEvent(lastSeenState, currentFileState);

        if (fileEventType == null) {
            return;
        }

        final FileEvent newFileEvent = new FileEvent(fileEventType, inspection.path(), currentFileState, inspection.requester());
        final PendingEvent existingPendingEvent = stabilizedFutures.remove(inspection.path());

        if(existingPendingEvent != null) {
            existingPendingEvent.scheduledFuture().cancel(false);

            if((existingPendingEvent.fileEvent().type() == FileEventType.CREATED || existingPendingEvent.fileEvent().type() == FileEventType.DISCOVERED) && newFileEvent.type() == FileEventType.MODIFIED) {
                final FileEvent fileEvent = new FileEvent(
                        existingPendingEvent.fileEvent().type(),
                        existingPendingEvent.fileEvent().path(),
                        newFileEvent.fileState(),
                        existingPendingEvent.fileEvent().source()
                );

                log.info("Restabilizing file event: {}", fileEvent);
                stabilizeFileEvent(fileEvent);
                return;
            } else {
                dispatch(existingPendingEvent.fileEvent());
            }
        }

        if(fileEventType == FileEventType.CREATED || fileEventType == FileEventType.DISCOVERED) {
            log.info("Stabilizing file event: {}", newFileEvent);
            stabilizeFileEvent(newFileEvent);
        } else {
            dispatch(newFileEvent);
        }
    }

    private void stabilizeFileEvent(FileEvent fileEvent) {
        final ScheduledFuture<?> scheduledFuture = scheduledExecutorService.schedule(() -> {
            try {
                dispatch(fileEvent);
            } finally {
                stabilizedFutures.remove(fileEvent.path());
            }
        }, stabilizationDelay.toMillis(), TimeUnit.MILLISECONDS);

        final PendingEvent pendingEvent = new PendingEvent(fileEvent, scheduledFuture);

        stabilizedFutures.put(fileEvent.path(), pendingEvent);
    }

    private void dispatch(FileEvent fileEvent) {
        log.info("Dispatching FileEvent: {}", fileEvent);

        if (listener != null) {
            listener.onFileEvent(fileEvent);
        }
    }

    private static FileEventType analyzeEvent(FileState lastSeenState, FileState currentFileState) {
        if (lastSeenState == null && currentFileState != null) {
            // created or discovered
            return FileEventType.CREATED;
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
}

record PendingEvent (FileEvent fileEvent, ScheduledFuture<?> scheduledFuture) {
}