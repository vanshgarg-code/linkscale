package com.linkscale.backend;

import com.linkscale.backend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityCookieTests {

    @Test
    void productionCsrfCookieStaysSecureBehindAnHttpProxyConnection() {
        var repository = new SecurityConfig().csrfTokenRepository(true);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        repository.saveToken(repository.generateToken(request), request, response);

        var cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    void developmentCsrfCookieWorksOverLocalHttp() {
        var repository = new SecurityConfig().csrfTokenRepository(false);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        repository.saveToken(repository.generateToken(request), request, response);

        var cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.isHttpOnly()).isFalse();
    }
}
