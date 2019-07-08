package com.mesilat.datashare.events;

import com.atlassian.confluence.event.events.ConfluenceEvent;
import com.mesilat.datashare.service.PageProcessingTask;

public abstract class DataObjectEvent extends ConfluenceEvent {
    private final PageProcessingTask task;

    public PageProcessingTask getTask(){
        return task;
    }

    public DataObjectEvent(Object src, PageProcessingTask task){
        super(src);
        this.task = task;
    }
}