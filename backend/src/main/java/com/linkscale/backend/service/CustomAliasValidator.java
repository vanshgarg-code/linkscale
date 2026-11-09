package com.linkscale.backend.service;

import com.linkscale.backend.exception.InvalidCustomAliasException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class CustomAliasValidator {

    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$");

    private static final Set<String> RESERVED_NAMES = Set.of(
            "actuator", "admin", "analytics", "api", "auth", "config", "dashboard",
            "docs", "error", "favicon", "health", "info", "link", "links", "login",
            "logout", "metrics", "public", "r", "register", "robots", "root",
            "signup", "static", "status", "swagger", "swagger-ui", "user", "users",
            "v1", "v2"
    );

    public String validateAndNormalize(String rawAlias) {
        if (rawAlias == null || rawAlias.isBlank()) {
            throw new InvalidCustomAliasException("Custom alias must not be blank");
        }

        String alias = rawAlias.trim().toLowerCase(Locale.ROOT);

        if (!ALIAS_PATTERN.matcher(alias).matches()) {
            throw new InvalidCustomAliasException(
                    "Custom alias must be 3-32 characters long, lowercase alphanumeric, and may contain internal hyphens"
            );
        }

        if (RESERVED_NAMES.contains(alias)) {
            throw new InvalidCustomAliasException("Custom alias '" + alias + "' is a reserved keyword");
        }

        return alias;
    }
}

