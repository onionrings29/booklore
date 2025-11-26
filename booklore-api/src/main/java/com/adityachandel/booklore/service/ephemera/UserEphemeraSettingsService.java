package com.adityachandel.booklore.service.ephemera;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.UserEphemeraSettings;
import com.adityachandel.booklore.model.entity.UserEphemeraSettingsEntity;
import com.adityachandel.booklore.repository.UserEphemeraSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEphemeraSettingsService {

    private final UserEphemeraSettingsRepository repository;
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

        UserEphemeraSettingsEntity entity = repository.findByUserId(user.getId())
                .map(existing -> {
                    existing.setEnabled(settings.isEnabled());
                    existing.setServerIp(settings.getServerIp());
                    existing.setServerPort(settings.getServerPort());
                    return existing;
                })
                .orElseGet(() -> UserEphemeraSettingsEntity.builder()
                        .userId(user.getId())
                        .enabled(settings.isEnabled())
                        .serverIp(settings.getServerIp())
                        .serverPort(settings.getServerPort())
                        .build());

        entity = repository.save(entity);
        log.info("Updated ephemera settings for user {} - enabled: {}, serverIp: {}, serverPort: {}",
                user.getId(), settings.isEnabled(), settings.getServerIp(), settings.getServerPort());
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
