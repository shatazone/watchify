package com.shatazone.watchify;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class PathRegistry {
    private final Set<Path> roots = new HashSet<Path>();

    public boolean addRoot(Path path) {
        return roots.add(path);
    }

    public boolean removeRoot(Path path) {
        return roots.add(path);
    }

    public boolean shouldWatch(Path path) {
        for(Path root : roots) {
            if(path.startsWith(root)) {
                return true;
            }
        }

        return false;
    }
}
