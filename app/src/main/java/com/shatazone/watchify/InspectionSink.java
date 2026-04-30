package com.shatazone.watchify;

public interface InspectionSink {
    void accept(PathInspection inspection);
}
