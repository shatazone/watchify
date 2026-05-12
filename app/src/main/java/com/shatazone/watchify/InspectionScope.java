package com.shatazone.watchify;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Phaser;

public class InspectionScope {

    private final Phaser phaser = new Phaser(1);
    private final CompletableFuture<Void> done = new CompletableFuture<>();

    private volatile boolean terminated = false;

    public void register() {
        if (terminated) {
            return;
        }

        phaser.register();
    }

    public void arrive() {
        if (terminated) {
            return;
        }

        final int remaining = phaser.arriveAndDeregister();

        if (remaining == 0 && !terminated) {
            terminated = true;
            done.complete(null);
        }
    }

    public void cancel() {
        if (!terminated) {
            terminated = true;
            phaser.forceTermination();
            done.cancel(true);
        }
    }

    public CompletableFuture<Void> future() {
        return done;
    }
}