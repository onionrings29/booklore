package com.adityachandel.booklore.service.ephemera;

import com.adityachandel.booklore.model.dto.settings.EphemeraSettings;
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

    private String baseUrl = "http://10.129.20.50:8286";
    private long connectTimeoutMs = 2000;
    private long readTimeoutMs = 30000;
    private List<String> allowedPaths = List.of("/**");
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    private boolean injectUserHeaders = true;

    public EphemeraProperties(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
    }

    /**
     * Gets the effective base URL for Ephemera, prioritizing database settings over application.yaml
     */
    public String getEffectiveBaseUrl() {
        EphemeraSettings settings = appSettingService.getAppSettings().getEphemeraSettings();
        if (settings != null && settings.isEnabled() && settings.getServerIp() != null && settings.getServerPort() != null) {
            return "http://" + settings.getServerIp() + ":" + settings.getServerPort();
        }
        return baseUrl;
    }

    /**
     * Checks if ephemera is enabled (either in database settings or by having a baseUrl configured)
     */
    public boolean isEnabled() {
        EphemeraSettings settings = appSettingService.getAppSettings().getEphemeraSettings();
        return settings != null && settings.isEnabled();
    }

    /**
     * Checks if the ephemera button should be shown in the UI
     */
    public boolean isShowButton() {
        EphemeraSettings settings = appSettingService.getAppSettings().getEphemeraSettings();
        return settings != null && settings.isShowButton();
    }
}

