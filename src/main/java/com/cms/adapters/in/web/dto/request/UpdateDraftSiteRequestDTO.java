package com.cms.adapters.in.web.dto.request;

import com.cms.adapters.in.web.validation.ValidJson;
import com.cms.domain.port.in.site.UpdateDraftSiteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UpdateDraftSiteRequestDTO(
        @NotBlank
        @ValidJson
        @Schema(
                description = "Draft content of the site, in JSON (key-value) format.",
                example = "{\"title\": \"My Portfolio\", \"summary\": \"A showcase of my work.\"}"
        )
        String content
) {
        public UpdateDraftSiteCommand toCommand(UUID siteId, long userId, long expectedVersion) {
                return new UpdateDraftSiteCommand(siteId, userId, content, expectedVersion);
        }
}
