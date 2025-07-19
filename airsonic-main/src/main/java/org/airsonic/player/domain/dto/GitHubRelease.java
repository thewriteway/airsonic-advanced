package org.airsonic.player.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRelease {

    @JsonProperty("tag_name")
    private String tagName;

    @JsonProperty("target_commitish")
    private String targetCommitish;
    private boolean draft;
    private boolean prerelease;
    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("published_at")
    private Instant publishedAt;

    @JsonProperty("created_at")
    private Instant createdAt;
    private List<GitHubAsset> assets;

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getTargetCommitish() {
        return targetCommitish;
    }

    public void setTargetCommitish(String targetCommitish) {
        this.targetCommitish = targetCommitish;
    }

    public boolean isDraft() {
        return draft;
    }

    public void setDraft(boolean draft) {
        this.draft = draft;
    }

    public boolean isPrerelease() {
        return prerelease;
    }

    public void setPrerelease(boolean prerelease) {
        this.prerelease = prerelease;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<GitHubAsset> getAssets() {
        return assets;
    }

    public void setAssets(List<GitHubAsset> assets) {
        this.assets = assets;
    }
}
