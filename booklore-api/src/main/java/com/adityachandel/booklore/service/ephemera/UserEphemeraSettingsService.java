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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Test connection to the user's configured Ephemera server
     */
    public Map<String, Object> testConnection() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        UserEphemeraSettings settings = getCurrentUserSettings();

        Map<String, Object> result = new HashMap<>();

        if (settings.getServerIp() == null || settings.getServerIp().isBlank() || settings.getServerPort() == null) {
            result.put("success", false);
            result.put("message", "Server IP and port must be configured");
            return result;
        }

        String healthUrl = "http://" + settings.getServerIp() + ":" + settings.getServerPort() + "/health";

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                result.put("success", true);
                result.put("message", "Successfully connected to Ephemera server");
                result.put("statusCode", response.statusCode());
                result.put("response", response.body());
                log.info("Ephemera connection test successful for user {} to {}:{}",
                        user.getId(), settings.getServerIp(), settings.getServerPort());
            } else {
                result.put("success", false);
                result.put("message", "Server responded with status code: " + response.statusCode());
                result.put("statusCode", response.statusCode());
                log.warn("Ephemera connection test failed for user {} - status code: {}",
                        user.getId(), response.statusCode());
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Failed to connect to Ephemera server: " + e.getMessage());
            log.error("Ephemera connection test failed for user {} to {}:{}",
                    user.getId(), settings.getServerIp(), settings.getServerPort(), e);
        }

        return result;
    }
}
