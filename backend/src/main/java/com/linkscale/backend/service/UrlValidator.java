package com.linkscale.backend.service;

import com.linkscale.backend.config.LinkScaleProperties;
import com.linkscale.backend.exception.InvalidDestinationUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class UrlValidator {

    private final LinkScaleProperties properties;

    public UrlValidator(LinkScaleProperties properties) {
        this.properties = properties;
    }

    public String validateAndNormalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidDestinationUrlException("Destination URL must not be blank");
        }

        String trimmed = rawUrl.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidDestinationUrlException("Malformed destination URL: " + e.getMessage());
        }

        if (!uri.isAbsolute()) {
            throw new InvalidDestinationUrlException("Destination URL must be absolute (e.g. https://example.com)");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new InvalidDestinationUrlException("Only HTTP and HTTPS destination URLs are permitted");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidDestinationUrlException("Destination URL must include a valid host");
        }

        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new InvalidDestinationUrlException("Destination URL must not contain embedded user credentials");
        }

        checkRedirectLoop(uri, scheme, host);

        return trimmed;
    }

    private void checkRedirectLoop(URI uri, String scheme, String host) {
        try {
            URI baseUri = new URI(properties.baseUrl());
            String baseHost = baseUri.getHost();
            if (baseHost == null) {
                return;
            }

            int basePort = baseUri.getPort() != -1 ? baseUri.getPort() : ("https".equalsIgnoreCase(baseUri.getScheme()) ? 443 : 80);
            int uriPort = uri.getPort() != -1 ? uri.getPort() : ("https".equalsIgnoreCase(scheme) ? 443 : 80);

            boolean isSameHost = host.equalsIgnoreCase(baseHost) || (isLoopback(host) && isLoopback(baseHost));

            if (isSameHost && uriPort == basePort) {
                String path = uri.getPath();
                if (path != null && (path.startsWith("/r/") || path.equals("/r") || path.startsWith("/api/"))) {
                    throw new InvalidDestinationUrlException("Destination URL must not point to LinkScale redirect or API endpoints");
                }
            }
        } catch (URISyntaxException ignored) {
        }
    }

    private boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }
}

