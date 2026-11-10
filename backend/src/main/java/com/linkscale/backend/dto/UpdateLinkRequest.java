package com.linkscale.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class UpdateLinkRequest {

    @Size(max = 255, message = "title must not exceed 255 characters")
    private String title;

    @JsonIgnore
    private boolean titleSet;

    private OffsetDateTime expiresAt;

    @JsonIgnore
    private boolean expiresAtSet;

    private Boolean active;

    @JsonIgnore
    private boolean activeSet;

    @JsonCreator
    public UpdateLinkRequest() {
    }

    public UpdateLinkRequest(String title, OffsetDateTime expiresAt, Boolean active) {
        this.title = title;
        this.titleSet = true;
        this.expiresAt = expiresAt;
        this.expiresAtSet = true;
        this.active = active;
        this.activeSet = true;
    }

    @JsonProperty("title")
    public void setTitle(String title) {
        this.title = title;
        this.titleSet = true;
    }

    @JsonProperty("expires_at")
    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
        this.expiresAtSet = true;
    }

    @JsonProperty("expiresAt")
    public void setExpiresAtCamel(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
        this.expiresAtSet = true;
    }

    @JsonProperty("active")
    public void setActive(Boolean active) {
        this.active = active;
        this.activeSet = true;
    }

    public String getTitle() {
        return title;
    }

    @JsonIgnore
    public boolean isTitleSet() {
        return titleSet;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    @JsonIgnore
    public boolean isExpiresAtSet() {
        return expiresAtSet;
    }

    public Boolean getActive() {
        return active;
    }

    @JsonIgnore
    public boolean isActiveSet() {
        return activeSet;
    }
}
