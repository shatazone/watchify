package com.shatazone.watchify;

import com.shatazone.watchify.globs.GlobPathPattern;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PathRegistry {
    private final Map<Path, MonitoredRoot> monitoredRoots = new ConcurrentHashMap<>();

    public List<Path> getMonitoredRoots() {
        return new ArrayList<>(monitoredRoots.keySet());
    }

    public Subscription subscribe(final GlobPathPattern globPathPattern, FileEventListener fileEventListener) {
        final String subscriptionKey = globPathPattern.getPattern();

        monitoredRoots.compute(globPathPattern.getDirectory(), (path, existing) -> {
            final MonitoredRoot monitoredRoot = existing != null
                    ? existing
                    : new MonitoredRoot(path);

            final FileEventListenerGroup fileEventListenerGroup = monitoredRoot.listenerGroup.computeIfAbsent(subscriptionKey, k -> new FileEventListenerGroup(globPathPattern));
            fileEventListenerGroup.listeners().add(fileEventListener);

            return monitoredRoot;
        });

        return new Subscription() {
            @Override
            public void cancel() {
                final MonitoredRoot monitoredRoot = monitoredRoots.get(globPathPattern.getDirectory());
                final FileEventListenerGroup fileEventListenerGroup = monitoredRoot.listenerGroup().get(subscriptionKey);
                fileEventListenerGroup.listeners().remove(fileEventListener);

                if(fileEventListenerGroup.listeners().isEmpty()) {
                    monitoredRoot.listenerGroup().remove(subscriptionKey, fileEventListenerGroup);
                }

                if(monitoredRoot.listenerGroup().isEmpty()) {
                    monitoredRoots.remove(globPathPattern.getDirectory(),  monitoredRoot);
                }
            }
        };
    }

    public boolean shouldWatchDirectory(Path path) {
        for (Map.Entry<Path, MonitoredRoot> entry : monitoredRoots.entrySet()) {
            final Path rootPath = entry.getKey();

            if (path.startsWith(rootPath)) {
                final MonitoredRoot monitoredRoot = entry.getValue();

                for(FileEventListenerGroup fileEventListenerGroup : monitoredRoot.listenerGroup.values()) {
                    if(fileEventListenerGroup.pathPattern.matchesPrefix(path) || fileEventListenerGroup.pathPattern.matches(path)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean shouldWatchFile(Path path) {
        for (Map.Entry<Path, MonitoredRoot> entry : monitoredRoots.entrySet()) {
            final Path rootPath = entry.getKey();

            if (path.startsWith(rootPath)) {
                final MonitoredRoot monitoredRoot = entry.getValue();

                for(FileEventListenerGroup fileEventListenerGroup : monitoredRoot.listenerGroup.values()) {
                    if(fileEventListenerGroup.pathPattern.matches(path)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public List<FileEventListener> getListenersOf(Path path) {
        FileEventListenerGroup listenerGroup = getListenerGroup(path);

        if(listenerGroup == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(listenerGroup.listeners());
    }

    private FileEventListenerGroup getListenerGroup(Path path) {
        for (Map.Entry<Path, MonitoredRoot> entry : monitoredRoots.entrySet()) {
            final Path rootPath = entry.getKey();

            if (path.startsWith(rootPath)) {
                final MonitoredRoot monitoredRoot = entry.getValue();

                for(FileEventListenerGroup fileEventListenerGroup : monitoredRoot.listenerGroup.values()) {
                    if(fileEventListenerGroup.pathPattern.matches(path)) {
                        return fileEventListenerGroup;
                    }
                }
            }
        }

        return null;
    }

    private record FileEventListenerGroup(GlobPathPattern pathPattern, List<FileEventListener> listeners) {
        public FileEventListenerGroup(GlobPathPattern pathPattern) {
            this(pathPattern, new ArrayList<>());
        }
    }

    private record MonitoredRoot(Path rootPath, ConcurrentHashMap<String, FileEventListenerGroup> listenerGroup) {
        private MonitoredRoot(Path rootPath) {
            this(rootPath, new ConcurrentHashMap<>());
        }
    }
}