package com.adityachandel.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EphemeraSettings {
    private boolean enabled;
    private String serverIp;
    private Integer serverPort;
    private boolean showButton;
}
