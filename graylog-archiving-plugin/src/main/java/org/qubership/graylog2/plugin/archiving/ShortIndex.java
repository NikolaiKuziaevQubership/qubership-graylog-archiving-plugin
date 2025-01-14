package org.qubership.graylog2.plugin.archiving;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ShortIndex {
    @JsonProperty("index")
    private String name;
    @JsonProperty("creation.date")
    private long creationDate;

    public String getName() {
        return name;
    }

    public long getCreationDate() {
        return creationDate;
    }

    public ShortIndex() {
        this.name = "";
        this.creationDate = 0L;
    }
}
