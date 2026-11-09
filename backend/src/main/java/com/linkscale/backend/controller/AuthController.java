package com.linkscale.backend.controller;

import com.linkscale.backend.domain.UserRecord;
import com.linkscale.backend.dto.CsrfResponse;
import com.linkscale.backend.dto.LoginRequest;
import com.linkscale.backend.dto.RegisterRequest;
import com.linkscale.backend.dto.UserResponse;
import com.linkscale.backend.repository.UserRepository;
import com.linkscale.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthService authService,
                          UserRepository userRepository,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          CsrfTokenRepository csrfTokenRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/api/v1/auth/csrf")
    public ResponseEntity<CsrfResponse> getCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        }
        if (csrfToken == null) {
            csrfToken = csrfTokenRepository.generateToken(request);
        }
        csrfTokenRepository.saveToken(csrfToken, request, response);
        response.setHeader("X-XSRF-TOKEN", csrfToken.getToken());

        return ResponseEntity.ok(new CsrfResponse(
                csrfToken.getToken(),
                csrfToken.getHeaderName(),
                csrfToken.getParameterName()
        ));
    }

    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletRequest httpRequest,
                                             HttpServletResponse httpResponse) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        // Protect against session fixation
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        // Clear previous CSRF token and issue fresh token for the new authenticated session
        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);
        CsrfToken freshToken = csrfTokenRepository.generateToken(httpRequest);
        csrfTokenRepository.saveToken(freshToken, httpRequest, httpResponse);
        httpRequest.setAttribute(CsrfToken.class.getName(), freshToken);
        httpRequest.setAttribute(freshToken.getParameterName(), freshToken);
        httpResponse.setHeader("X-XSRF-TOKEN", freshToken.getToken());

        UserRecord user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("User not found after successful authentication: " + normalizedEmail));

        return ResponseEntity.ok(new UserResponse(user.id(), user.email(), user.createdAt()));
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse,
                                      Authentication authentication) {
        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.logout(httpRequest, httpResponse, authentication);

        // Clear previous CSRF token and generate fresh token for post-logout caller
        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);
        CsrfToken freshToken = csrfTokenRepository.generateToken(httpRequest);
        csrfTokenRepository.saveToken(freshToken, httpRequest, httpResponse);
        httpRequest.setAttribute(CsrfToken.class.getName(), freshToken);
        httpRequest.setAttribute(freshToken.getParameterName(), freshToken);
        httpResponse.setHeader("X-XSRF-TOKEN", freshToken.getToken());

        return ResponseEntity.ok().build();
    }

    @GetMapping({"/api/v1/auth/me", "/api/v1/users/me", "/api/v1/account"})
    public ResponseEntity<UserResponse> getCurrentAccount(Authentication authentication) {
        UserResponse response = authService.getCurrentAccount(authentication);
        return ResponseEntity.ok(response);
    }
}
