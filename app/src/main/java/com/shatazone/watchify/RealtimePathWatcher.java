package com.shatazone.watchify;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class RealtimePathWatcher extends AbstractExecutionThreadService {

    private final Map<Path, WatchKey> watchKeys = new ConcurrentHashMap<>();
    private final WatchService watchService;

    @Setter
    private InspectionSink inspectionSink;

    public void watch(Path directory) throws IOException {
        if (watchKeys.containsKey(directory)) {
            return;
        }

        final WatchKey newWatchKey = directory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
        );

        final WatchKey existing = watchKeys.putIfAbsent(directory, newWatchKey);

        if (existing != null) {
            newWatchKey.cancel();
        }
    }

    public boolean isWatched(Path directory) {
        return watchKeys.containsKey(directory);
    }

    public void unwatch(Path directory) {
        final WatchKey removedKey = watchKeys.remove(directory);

        if (removedKey != null) {
            removedKey.cancel();
        }
    }

    @Override
    protected void run() {
        while (isRunning()) {
            final WatchKey watchKey;

            try {
                watchKey = watchService.take();
            } catch (ClosedWatchServiceException e) {
                if(isRunning()) {
                    log.error("WatchService has closed unexpectedly");
                } else {
                    log.error("WatchService closed");
                }
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Service has been interrupted");
                break;
            }

            final Path baseDir = (Path) watchKey.watchable();

            for (WatchEvent<?> watchEvent : watchKey.pollEvents()) {
                if (watchEvent.kind() == StandardWatchEventKinds.OVERFLOW) {
                    requestInspection(baseDir);
                    log.warn("Overflow event was received for path: {}", baseDir);
                    continue;
                }

                final Path fullPath = baseDir.resolve((Path) watchEvent.context());
                requestInspection(fullPath);
            }

            if (!watchKey.reset()) {
                watchKeys.remove(baseDir);
                requestInspection(baseDir);
            }
        }
    }

    private void requestInspection(Path path) {
        if (inspectionSink != null) {
            inspectionSink.accept(new PathInspection(serviceName(), path, false));
        }
    }

    @Override
    protected void triggerShutdown() {
        CloseableUtils.closeQuietly(watchService);
    }
}