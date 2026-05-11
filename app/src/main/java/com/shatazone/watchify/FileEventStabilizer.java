package com.shatazone.watchify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
public class FileEventStabilizer implements AutoCloseable {

    private final Map<Path, PendingEvent> pendingEvents = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutorService;
    private final Duration stabilizationDelay;

    public FileEventStabilizer(Duration stabilizationDelay) {
        this.stabilizationDelay = stabilizationDelay;

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "file-event-stabilizer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<FileEvent> stabilize(FileEvent fileEvent) {

        final PendingEvent pendingEvent = pendingEvents.compute(fileEvent.path(), (path, existing) -> {

            if (existing != null) {
                existing.scheduledFuture().cancel(false);

                if (isCreationLike(existing.fileEvent().type())
                        && fileEvent.type() == FileEventType.MODIFIED) {

                    final FileEvent effectiveFileEvent = new FileEvent(
                            existing.fileEvent().type(),
                            existing.fileEvent().path(),
                            fileEvent.fileState(),
                            existing.fileEvent().source()
                    );

                    return schedulePendingEvent(effectiveFileEvent, existing.completableFuture());
                } else {
                    final CompletableFuture<FileEvent> chainedFuture =
                            existing.completableFuture()
                                    .thenCompose(ignored ->
                                            schedulePendingEvent(
                                                    fileEvent,
                                                    new CompletableFuture<>()
                                            ).completableFuture()
                                    );

                    schedulePendingEventImmediately(
                            existing.fileEvent(),
                            existing.completableFuture()
                    );

                    return new PendingEvent(
                            fileEvent,
                            existing.scheduledFuture(),
                            chainedFuture
                    );
                }
            }

            return schedulePendingEvent(fileEvent, new CompletableFuture<>());
        });

        return pendingEvent.completableFuture();
    }

    private @NonNull PendingEvent schedulePendingEventImmediately(FileEvent fileEvent, CompletableFuture<FileEvent> completableFuture) {
        return schedulePendingEvent(fileEvent, completableFuture, 0);
    }

    private @NonNull PendingEvent schedulePendingEvent(FileEvent fileEvent, CompletableFuture<FileEvent> completableFuture) {
        return schedulePendingEvent(fileEvent, completableFuture, computeRemainingStabilizationDelayMillis(fileEvent));
    }

    private @NonNull PendingEvent schedulePendingEvent(FileEvent fileEvent, CompletableFuture<FileEvent> completableFuture, long remainingStabilizationDelayMillis) {
        log.debug("Scheduling stabilization delay millis for {} after {} ms", fileEvent, remainingStabilizationDelayMillis);
        final ScheduledFuture<?> scheduledFuture =
                scheduledExecutorService.schedule(
                        () -> completableFuture.complete(fileEvent),
                        remainingStabilizationDelayMillis,
                        TimeUnit.MILLISECONDS
                );


        final PendingEvent pendingEvent = new PendingEvent(
                fileEvent,
                scheduledFuture,
                completableFuture
        );

        final Path path = fileEvent.path();

        completableFuture.whenComplete((completedEvent, throwable) -> {
            if (pendingEvents.remove(path, pendingEvent)) {
                pendingEvent.scheduledFuture().cancel(false);
            }
        });

        return pendingEvent;
    }

    private long computeRemainingStabilizationDelayMillis(FileEvent fileEvent) {
        if (isCreationLike(fileEvent.type())) {

            final long nowMillis = System.currentTimeMillis();

            final long elapsedSinceLastModificationMillis =
                    nowMillis - fileEvent.fileState().lastModifiedMillis();

            return Math.max(
                    stabilizationDelay.toMillis() - elapsedSinceLastModificationMillis,
                    0
            );

        } else {
            return 0;
        }
    }

    private boolean isCreationLike(FileEventType type) {
        return type == FileEventType.CREATED
                || type == FileEventType.DISCOVERED;
    }

    @Override
    public void close() {
        scheduledExecutorService.shutdownNow();
    }
}