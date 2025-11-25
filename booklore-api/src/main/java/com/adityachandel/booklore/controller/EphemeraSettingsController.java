package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.settings.AppSettingKey;
import com.adityachandel.booklore.model.dto.settings.EphemeraSettings;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/ephemera-settings")
@RequiredArgsConstructor
@Tag(name = "Ephemera Settings", description = "Endpoints for managing Ephemera integration settings")
public class EphemeraSettingsController {

    private final AppSettingService appSettingService;

    @Operation(summary = "Get Ephemera settings", description = "Retrieve the current Ephemera settings.")
    @ApiResponse(responseCode = "200", description = "Settings returned successfully")
    @GetMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<EphemeraSettings> getSettings() {
        EphemeraSettings settings = appSettingService.getAppSettings().getEphemeraSettings();
        return ResponseEntity.ok(settings != null ? settings : EphemeraSettings.builder().build());
    }

    @Operation(summary = "Update Ephemera settings", description = "Update Ephemera integration settings. Requires admin permission.")
    @ApiResponse(responseCode = "200", description = "Settings updated successfully")
    @PutMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<EphemeraSettings> updateSettings(@RequestBody EphemeraSettings settings) {
        try {
            appSettingService.updateSetting(AppSettingKey.EPHEMERA_SETTINGS, settings);
            return ResponseEntity.ok(settings);
        } catch (JsonProcessingException e) {
            log.error("Failed to update Ephemera settings", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
