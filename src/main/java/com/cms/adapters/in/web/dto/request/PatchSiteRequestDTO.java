package com.cms.adapters.in.web.dto.request;

import com.cms.domain.port.in.site.PatchSiteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PatchSiteRequestDTO(
        @Schema(description = "New title. Omit to leave unchanged.", example = "Santiago Acevedo — Portfolio")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Schema(description = "New summary. Omit to leave unchanged.", example = "Backend Developer Portfolio")
        @Size(max = 255, message = "Summary must be at most 255 characters")
        String summary,

        @Schema(description = "New content schema hint. Omit to leave unchanged. "
                + "Changing it in production is a breaking change for deployed frontends.",
                example = "portfolio-v2")
        @Size(max = 100, message = "Content schema must be at most 100 characters")
        String contentSchema
) {
    public PatchSiteCommand toCommand(UUID siteId, Long requesterUserId) {
        return new PatchSiteCommand(siteId, requesterUserId, title, summary, contentSchema);
    }
}
