package com.linkscale.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * Custom CSRF token request handler suitable for Single-Page Applications and REST APIs.
 * Resolves CSRF token from either {@code X-XSRF-TOKEN} or {@code X-CSRF-TOKEN} header,
 * or falls back to standard request parameter.
 */
public class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    public SpaCsrfTokenRequestHandler() {
        // Set request attribute name to null to prevent unnecessary wrapping
        setCsrfRequestAttributeName(null);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        if (!StringUtils.hasText(headerValue)) {
            headerValue = request.getHeader("X-CSRF-TOKEN");
        }
        return StringUtils.hasText(headerValue) ? headerValue : super.resolveCsrfTokenValue(request, csrfToken);
    }
}
