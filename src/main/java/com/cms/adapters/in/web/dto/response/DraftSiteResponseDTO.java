package com.cms.adapters.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record DraftSiteResponseDTO(
        @Schema(name = "content", description = "Draft content of the site, in JSON (key-value) format.", example = "{\"title\": \"My Portfolio\", \"summary\": \"A showcase of my work.\"}")
        JsonNode content,
        @Schema(name = "version", description = "Version number of the draft content, used for optimistic locking.", example = "1")
        long version,
        @Schema(name = "updatedAt", description = "Timestamp of the last update to the draft content.", example = "2024-06-01T12:00:00Z")
        Instant updateAt
) {
}
