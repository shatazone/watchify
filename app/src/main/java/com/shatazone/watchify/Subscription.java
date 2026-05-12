package com.shatazone.watchify;

public interface Subscription {
    void awaitReady();
    void cancel();
}