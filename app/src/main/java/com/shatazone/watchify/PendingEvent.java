package com.shatazone.watchify;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

record PendingEvent (FileEvent fileEvent, ScheduledFuture<?> scheduledFuture, CompletableFuture<FileEvent> completableFuture) {

}