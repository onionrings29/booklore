package com.adityachandel.booklore.service.ephemera;

import com.adityachandel.booklore.model.dto.settings.EphemeraSettings;
import com.adityachandel.booklore.model.dto.settings.UserEphemeraSettings;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.ephemera")
public class EphemeraProperties {

    private final AppSettingService appSettingService;

    private String baseUrl;
    private long connectTimeoutMs = 2000;
    private long readTimeoutMs = 30000;
    private List<String> allowedPaths = List.of("/**");
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    private boolean injectUserHeaders = true;

    public EphemeraProperties(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
    }

    /**
     * Gets the effective base URL for Ephemera for a specific user
     */
    public String getEffectiveBaseUrl(UserEphemeraSettings userSettings) {
        if (userSettings != null && userSettings.isEnabled() &&
            userSettings.getServerIp() != null && userSettings.getServerPort() != null) {
            return "http://" + userSettings.getServerIp() + ":" + userSettings.getServerPort();
        }

        // Fall back to global settings for backward compatibility
        return getGlobalBaseUrl();
    }

    /**
     * Gets the effective base URL for Ephemera, prioritizing database settings over application.yaml
     * @deprecated Use getEffectiveBaseUrl(UserEphemeraSettings) instead for user-specific settings
     */
    @Deprecated
    public String getEffectiveBaseUrl() {
        return getGlobalBaseUrl();
    }

    private String getGlobalBaseUrl() {
        EphemeraSettings settings = appSettingService.getAppSettings().getEphemeraSettings();
        if (settings != null && settings.isEnabled() && settings.getServerIp() != null && settings.getServerPort() != null) {
            return "http://" + settings.getServerIp() + ":" + settings.getServerPort();
        }
        // No fallback - ephemera must be explicitly configured
        return baseUrl != null && !baseUrl.isBlank() ? baseUrl : null;
    }

    /**
     * Checks if ephemera is enabled for a specific user
     */
    public boolean isEnabled(UserEphemeraSettings userSettings) {
        return userSettings != null && userSettings.isEnabled();
    }

    /**
     * Checks if ephemera is enabled (global settings)
     * @deprecated Use isEnabled(UserEphemeraSettings) instead for user-specific settings
     */
    @Deprecated
    public boolean isEnabled() {
        EphemeraSettings settings = appSettingService.getAppSettings().getEphemeraSettings();
        return settings != null && settings.isEnabled();
    }

    /**
     * Checks if the ephemera button should be shown in the UI
     * @deprecated Use isEnabled(UserEphemeraSettings) instead for user-specific settings
     */
    @Deprecated
    public boolean isShowButton() {
        EphemeraSettings settings = appSettingService.getAppSettings().getEphemeraSettings();
        return settings != null && settings.isShowButton();
    }
}

