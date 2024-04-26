package org.airsonic.player.service.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Chapter {

    private Integer id;

    @JsonProperty("time_base")
    private String timeBase;

    private Long start;

    @JsonProperty("start_time")
    private String startTime;

    private Long end;

    @JsonProperty("end_time")
    private String endTime;

    private String title;

    private Map<String, String> tags = new HashMap<>();

    public Chapter() {
    }

    public Chapter(Integer id, String timeBase, Long start, String startTime, Long end, String endTime, String title,
            Map<String, String> tags) {
        this.id = id;
        this.timeBase = StringUtils.trimToNull(timeBase);
        this.start = start;
        this.startTime = StringUtils.trimToNull(startTime);
        this.end = end;
        this.endTime = StringUtils.trimToNull(endTime);
        this.title = StringUtils.trimToNull(title);
        this.tags = tags == null ? new HashMap<>() : tags;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTimeBase() {
        return timeBase;
    }

    public void setTimeBase(String timeBase) {
        this.timeBase = timeBase;
    }

    public Long getStart() {
        return start;
    }

    public void setStart(Long start) {
        this.start = start;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public Double getStartTimeSeconds() {
        return Optional.ofNullable(startTime).map(Double::parseDouble).orElse(null);
    }

    public Long getEnd() {
        return end;
    }

    public void setEnd(Long end) {
        this.end = end;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Double getEndTimeSeconds() {
        return Optional.ofNullable(endTime).map(Double::parseDouble).orElse(null);
    }

    public String getTitle() {
        if (title == null) {
            return this.tags.get("title");
        }
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
}
