package com.linkscale.backend.security;

import com.linkscale.backend.domain.UserRecord;
import com.linkscale.backend.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class LinkScaleUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public LinkScaleUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Email must not be blank");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        UserRecord user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + normalizedEmail));

        return User.builder()
                .username(user.email())
                .password(user.passwordHash())
                .roles("USER")
                .build();
    }
}

