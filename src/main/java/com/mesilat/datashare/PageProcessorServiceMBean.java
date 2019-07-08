package com.mesilat.datashare;

import com.mesilat.jmx.Description;

@Description("DataShare page processor")
public interface PageProcessorServiceMBean {
    @Description("Get count of pages queued for processing")
    int getPendingCount();
    @Description("Get count of pages processed with errors in last 24 hours")
    int getErrorCount();
    @Description("Get count of pages processed with warnings in last 24 hours")
    int getWarningCount();
    @Description("Is Confluence running in cluster mode?")
    boolean isClustered();
}