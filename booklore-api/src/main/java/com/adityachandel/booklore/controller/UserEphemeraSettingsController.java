package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.settings.UserEphemeraSettings;
import com.adityachandel.booklore.service.ephemera.UserEphemeraSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/user-ephemera-settings")
@RequiredArgsConstructor
@Tag(name = "User Ephemera Settings", description = "Endpoints for managing user-specific Ephemera integration settings")
public class UserEphemeraSettingsController {

    private final UserEphemeraSettingsService service;

    @Operation(summary = "Get current user's Ephemera settings", description = "Retrieve the current user's Ephemera settings.")
    @ApiResponse(responseCode = "200", description = "Settings returned successfully")
    @GetMapping
    public ResponseEntity<UserEphemeraSettings> getSettings() {
        UserEphemeraSettings settings = service.getCurrentUserSettings();
        return ResponseEntity.ok(settings);
    }

    @Operation(summary = "Update current user's Ephemera settings", description = "Update the current user's Ephemera integration settings.")
    @ApiResponse(responseCode = "200", description = "Settings updated successfully")
    @PutMapping
    public ResponseEntity<UserEphemeraSettings> updateSettings(@RequestBody UserEphemeraSettings settings) {
        UserEphemeraSettings updated = service.updateSettings(settings);
        return ResponseEntity.ok(updated);
    }
}
