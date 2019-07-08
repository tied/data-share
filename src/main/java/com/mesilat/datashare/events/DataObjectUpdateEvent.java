package com.mesilat.datashare.events;

import com.mesilat.datashare.service.PageProcessingTask;
import org.codehaus.jackson.node.ObjectNode;

public class DataObjectUpdateEvent extends DataObjectEvent {
    private final ObjectNode rootObject;
    private final ObjectNode oldObject;
    private final boolean pageRenamed;

    public ObjectNode getRootObject(){
        return rootObject;
    }
    public ObjectNode getOldObject(){
        return oldObject;
    }
    public boolean getPageRenamed(){
        return pageRenamed;
    }

    public DataObjectUpdateEvent(Object src, ObjectNode rootObject, ObjectNode oldObject, boolean pageRenamed, PageProcessingTask task){
        super(src, task);
        this.rootObject = rootObject;
        this.oldObject = oldObject;
        this.pageRenamed = pageRenamed;
    }
}