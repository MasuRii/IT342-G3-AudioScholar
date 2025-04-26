package edu.cit.audioscholar.dto;

import java.text.DecimalFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Monitor {

    @JsonProperty("id")
    private long id;

    @JsonProperty("friendly_name")
    private String friendlyName;

    @JsonProperty("url")
    private String url;

    @JsonProperty("status")
    private int status;

    @JsonProperty("custom_uptime_ratio")
    private String customUptimeRatio;

    private static final DecimalFormat UPTIME_FORMAT = new DecimalFormat("0.000'%'");


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCustomUptimeRatio() {
        return customUptimeRatio;
    }

    public void setCustomUptimeRatio(String customUptimeRatio) {
        this.customUptimeRatio = customUptimeRatio;
    }


    public String getStatusText() {
        return switch (status) {
            case 0 -> "Paused";
            case 1 -> "Not Checked Yet";
            case 2 -> "Up";
            case 8 -> "Seems Down";
            case 9 -> "Down";
            default -> "Unknown";
        };
    }

    public String getStatusColor() {
        return switch (status) {
            case 2 -> "green";
            case 8, 9 -> "red";
            case 0 -> "grey";
            case 1 -> "blue";
            default -> "orange";
        };
    }

    public String getFormattedUptimeRatio() {
        if (this.customUptimeRatio == null || this.customUptimeRatio.isEmpty()) {
            return "N/A";
        }
        try {
            double ratio = Double.parseDouble(this.customUptimeRatio);
            return UPTIME_FORMAT.format(ratio);
        } catch (NumberFormatException e) {
            return "N/A";
        }
    }

    @Override
    public String toString() {
        return "Monitor{" + "id=" + id + ", friendlyName='" + friendlyName + '\'' + ", url='" + url
                + '\'' + ", status=" + status + " (" + getStatusText() + ")"
                + ", customUptimeRatio='" + customUptimeRatio + '\'' + '}';
    }
}
