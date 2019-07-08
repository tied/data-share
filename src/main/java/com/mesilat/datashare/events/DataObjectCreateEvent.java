package com.mesilat.datashare.events;

import com.mesilat.datashare.service.PageProcessingTask;
import org.codehaus.jackson.node.ObjectNode;

public class DataObjectCreateEvent extends DataObjectEvent {
    private final ObjectNode rootObject;

    public ObjectNode getRootObject(){
        return rootObject;
    }

    public DataObjectCreateEvent(Object src, ObjectNode rootObject, PageProcessingTask task){
        super(src, task);
        this.rootObject = rootObject;
    }
}