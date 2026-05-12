package com.shatazone.watchify;

import java.nio.file.Path;

public record Inspection(String requester, Path path, boolean discoveryMode) {
}