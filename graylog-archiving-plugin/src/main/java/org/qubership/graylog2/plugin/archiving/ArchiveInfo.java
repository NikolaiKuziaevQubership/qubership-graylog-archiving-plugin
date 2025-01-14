package org.qubership.graylog2.plugin.archiving;

import java.util.Date;

public class ArchiveInfo {

    private final String id;

    private final Date startTime;

    private volatile String status;

    private volatile String result;

    public ArchiveInfo(String id, Date startTime) {
        this.id = id;
        this.startTime = startTime;
    }

    public String getId() {
        return id;
    }

    public Date getStartTime() {
        return startTime;
    }

    public String getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
