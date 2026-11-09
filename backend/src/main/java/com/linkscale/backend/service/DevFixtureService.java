package com.linkscale.backend.service;

import com.linkscale.backend.config.LinkScaleProperties;
import com.linkscale.backend.domain.UserRecord;
import com.linkscale.backend.exception.UnauthenticatedCreationDisabledException;
import com.linkscale.backend.repository.UserRepository;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
public class DevFixtureService {

    private final UserRepository userRepository;
    private final LinkScaleProperties properties;
    private final Environment environment;

    public DevFixtureService(UserRepository userRepository,
                             LinkScaleProperties properties,
                             Environment environment) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.environment = environment;
    }

    public Long getOrCreateDevFixtureUserId() {
        boolean isDevOrTest = environment.acceptsProfiles(Profiles.of("dev", "test"));
        boolean isAllowed = properties.devMode() != null && properties.devMode().allowUnauthenticated();

        if (!isDevOrTest || !isAllowed) {
            throw new UnauthenticatedCreationDisabledException(
                    "Unauthenticated link creation is disabled. Enable dev-mode.allow-unauthenticated in dev/test profile."
            );
        }

        String email = properties.devMode().fixtureEmail();
        return userRepository.insertUserOnConflictDoNothing(email, "{noop}dev_fixture_password_hash")
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(UserRecord::id)
                        .orElseThrow(() -> new IllegalStateException("Failed to resolve fixture user: " + email)));
    }
}

