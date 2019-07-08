package com.mesilat.datashare;

import net.java.ao.Preload;
import net.java.ao.RawEntity;
import net.java.ao.schema.AutoIncrement;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.PrimaryKey;
import net.java.ao.schema.StringLength;

@Preload
public interface DataAttribute extends RawEntity<Long> {
    @AutoIncrement
    @NotNull
    @PrimaryKey(value = "ID")
    Long getId();
    void setId(Long id);

    @Indexed
    Long getPageId();
    void setPageId(Long pageId);

    @Indexed
    @StringLength(StringLength.MAX_LENGTH)
    String getPath();
    void setPath(String path);

    @Indexed
    @StringLength(StringLength.MAX_LENGTH)
    String getValue();
    void setValue(String value);
}