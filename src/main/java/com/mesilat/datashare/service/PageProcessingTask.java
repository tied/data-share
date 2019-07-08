package com.mesilat.datashare.service;

public interface PageProcessingTask {
    static enum Status {
        INCOMPLETE, OK, WARNING, ERROR
    };

    void addNotification(Status status, String notification);
}