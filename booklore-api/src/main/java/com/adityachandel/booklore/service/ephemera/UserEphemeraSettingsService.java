package com.adityachandel.booklore.service.ephemera;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.UserEphemeraSettings;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.UserEphemeraSettingsEntity;
import com.adityachandel.booklore.repository.UserEphemeraSettingsRepository;
import com.adityachandel.booklore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEphemeraSettingsService {

    private final UserEphemeraSettingsRepository repository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public UserEphemeraSettings getCurrentUserSettings() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        return repository.findByUserId(user.getId())
                .map(this::mapToDto)
                .orElseGet(() -> UserEphemeraSettings.builder()
                        .enabled(false)
                        .serverIp(null)
                        .serverPort(null)
                        .build());
    }

    @Transactional(readOnly = true)
    public UserEphemeraSettings getUserSettings(Long userId) {
        return repository.findByUserId(userId)
                .map(this::mapToDto)
                .orElseGet(() -> UserEphemeraSettings.builder()
                        .enabled(false)
                        .serverIp(null)
                        .serverPort(null)
                        .build());
    }

    @Transactional
    public UserEphemeraSettings updateSettings(UserEphemeraSettings settings) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        // Auto-enable when both serverIp and serverPort are provided
        boolean shouldEnable = settings.getServerIp() != null && !settings.getServerIp().isBlank()
                && settings.getServerPort() != null;

        UserEphemeraSettingsEntity entity = repository.findByUserId(user.getId())
                .map(existing -> {
                    existing.setEnabled(shouldEnable);
                    existing.setServerIp(settings.getServerIp());
                    existing.setServerPort(settings.getServerPort());
                    return existing;
                })
                .orElseGet(() -> {
                    BookLoreUserEntity userEntity = userRepository.findById(user.getId())
                            .orElseThrow(() -> new RuntimeException("User not found: " + user.getId()));
                    return UserEphemeraSettingsEntity.builder()
                            .user(userEntity)
                            .enabled(shouldEnable)
                            .serverIp(settings.getServerIp())
                            .serverPort(settings.getServerPort())
                            .build();
                });

        entity = repository.save(entity);
        log.info("Updated ephemera settings for user {} - enabled: {}, serverIp: {}, serverPort: {}",
                user.getId(), shouldEnable, settings.getServerIp(), settings.getServerPort());
        return mapToDto(entity);
    }

    private UserEphemeraSettings mapToDto(UserEphemeraSettingsEntity entity) {
        return UserEphemeraSettings.builder()
                .enabled(entity.getEnabled())
                .serverIp(entity.getServerIp())
                .serverPort(entity.getServerPort())
                .build();
    }
}
