package com.mesilat.datashare;

import net.java.ao.Preload;
import net.java.ao.RawEntity;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.PrimaryKey;

@Preload
public interface DataInlineObject extends RawEntity<String> {
    @NotNull
    @PrimaryKey("id")
    String getId();
    void setId(String id);

    @Indexed
    public Long getPageId();
    public void setPageId(Long pageId);
}