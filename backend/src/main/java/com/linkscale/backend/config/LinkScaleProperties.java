package com.linkscale.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "linkscale")
public record LinkScaleProperties(
        String baseUrl,
        DevMode devMode,
        Generator generator
) {
    public record DevMode(boolean allowUnauthenticated, String fixtureEmail) {}
    public record Generator(int codeLength, int maxRetries) {}
}

