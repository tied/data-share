package com.mesilat.datashare;

import net.java.ao.Preload;
import net.java.ao.RawEntity;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.PrimaryKey;
import net.java.ao.schema.StringLength;

@Preload
public interface DataObject extends RawEntity<Long> {
    @NotNull
    @PrimaryKey("id")
    Long getId();
    void setId(Long id);

    @StringLength(StringLength.UNLIMITED)
    String getRootObject();
    void setRootObject(String rootObject);
}