package com.linkscale.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CsrfResponse(
        String token,
        @JsonProperty("header_name")
        String headerName,
        @JsonProperty("parameter_name")
        String parameterName
) {
    @JsonProperty("headerName")
    public String getHeaderNameCamel() {
        return headerName;
    }

    @JsonProperty("parameterName")
    public String getParameterNameCamel() {
        return parameterName;
    }
}

