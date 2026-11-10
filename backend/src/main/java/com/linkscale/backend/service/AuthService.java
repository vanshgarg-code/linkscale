package com.linkscale.backend.service;

import com.linkscale.backend.domain.UserRecord;
import com.linkscale.backend.dto.RegisterRequest;
import com.linkscale.backend.dto.UserResponse;
import com.linkscale.backend.exception.DuplicateEmailException;
import com.linkscale.backend.exception.UserNotFoundException;
import com.linkscale.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(request.password());

        Long userId = userRepository.insertUserOnConflictDoNothing(normalizedEmail, passwordHash)
                .orElseThrow(() -> new DuplicateEmailException("Email is already registered: " + normalizedEmail));

        UserRecord user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found immediately after registration: " + userId));

        return new UserResponse(user.id(), user.email(), user.createdAt());
    }

    public UserRecord getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UserNotFoundException("No authenticated user in current session");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found for current session: " + email));
    }

    public UserResponse getCurrentAccount(Authentication authentication) {
        UserRecord user = getAuthenticatedUser(authentication);
        return new UserResponse(user.id(), user.email(), user.createdAt());
    }
}

