package com.mesilat.datashare.events;

import com.mesilat.datashare.service.PageProcessingTask;
import org.codehaus.jackson.node.ObjectNode;

public class DataObjectDeleteEvent extends DataObjectEvent {
    private final ObjectNode oldObject;

    public ObjectNode getOldObject(){
        return oldObject;
    }

    public DataObjectDeleteEvent(Object src, ObjectNode oldObject, PageProcessingTask task){
        super(src, task);
        this.oldObject = oldObject;
    }
}