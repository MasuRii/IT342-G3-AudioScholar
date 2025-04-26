package edu.cit.audioscholar.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UptimeRobotResponse {

    @JsonProperty("stat")
    private String stat;

    @JsonProperty("pagination")
    private Pagination pagination;

    @JsonProperty("monitors")
    private List<Monitor> monitors;

    public String getStat() {
        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }

    public List<Monitor> getMonitors() {
        return monitors;
    }

    public void setMonitors(List<Monitor> monitors) {
        this.monitors = monitors;
    }
}
