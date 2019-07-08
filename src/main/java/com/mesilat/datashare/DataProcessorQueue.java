package com.mesilat.datashare;

import java.util.Date;
import net.java.ao.Preload;
import net.java.ao.RawEntity;
import net.java.ao.schema.AutoIncrement;
import net.java.ao.schema.Default;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.PrimaryKey;
import net.java.ao.schema.StringLength;

@Preload
public interface DataProcessorQueue extends RawEntity<Long> {
    @AutoIncrement
    @NotNull
    @PrimaryKey(value = "ID")
    Long getId();
    void setId(Long id);

    @Indexed
    Long getPageId();
    void setPageId(Long pageId);

    @Indexed
    Integer getVersion();
    void setVersion(Long version);

    @Indexed
    @StringLength(40)
    String getUserKey();
    void setUserKey(String userKey);

    @StringLength(4)
    String getAction();
    void setAction(String action);

    @Indexed
    @StringLength(4)
    @Default("PEND")
    String getStatus();
    void setStatus(String status);

    @Indexed
    @StringLength(4)
    @Default("NACK")
    String getAcknowledged();
    void setAcknowledged(String acknowledged);

    boolean isPageRenamed();
    void setPageRenamed(boolean renamed);

    @StringLength(StringLength.UNLIMITED)
    String getLog();
    void setLog(String log);

    @Indexed
    Date getEnqueueDate();
    void setEnqueueDate(Date date);
}