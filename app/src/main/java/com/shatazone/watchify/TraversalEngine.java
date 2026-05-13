package com.shatazone.watchify;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class TraversalEngine {

    private final BlockingQueue<InspectionService.PendingInspection> inspectionQueue;
    private final int batchSize;

    private final Map<Path, DirectoryCursor> cursors = new ConcurrentHashMap<>();

    public void submitDirectory(InspectionService.PendingInspection inspection) {

        final DirectoryCursor cursor = cursors.computeIfAbsent(inspection.inspection().path(), p -> {
            try {
                return DirectoryCursor.open(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        processBatch(inspection, cursor);
    }

    private void processBatch(
            InspectionService.PendingInspection inspection,
            DirectoryCursor cursor
    ) {
        final Path directory = inspection.inspection().path();
        int processed = 0;

        while (cursor.hasNext() && processed < batchSize) {

            Path child = cursor.next();

            inspectionQueue.offer(
                    new InspectionService.PendingInspection(
                            new Inspection(
                                    inspection.inspection().requester(),
                                    child,
                                    inspection.inspection().discoveryMode()
                            ), inspection.scope()
                    )
            );

            processed++;
        }

        if (!cursor.hasNext()) {
            finish(directory);
            return;
        }

        // re-schedule continuation
        inspectionQueue.offer(
                new InspectionService.PendingInspection(
                        new Inspection(
                                inspection.inspection().requester(),
                                directory,
                                inspection.inspection().discoveryMode()
                        ),
                        inspection.scope())
        );
    }

    private void finish(Path directory) {
        final DirectoryCursor cursor = cursors.remove(directory);

        if (cursor != null) {
            cursor.closeQuietly();
        }
    }

    public void shutdown() {
        for (DirectoryCursor cursor : cursors.values()) {
            cursor.closeQuietly();
        }
        cursors.clear();
    }
}