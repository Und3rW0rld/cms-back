package com.cms.adapters.in.web.dto.request;

import com.cms.domain.port.in.site.CreateSiteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSiteRequestDTO(
        @Schema(description = "Site title, shown in the CMS listing.", example = "Santiago Acevedo — Portfolio")
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Schema(description = "Short summary for the CMS listing display. Not derived from content.",
                example = "Backend Developer Portfolio")
        @Size(max = 255, message = "Summary must be at most 255 characters")
        String summary,

        @Schema(description = "Frontend rendering hint (e.g. \"portfolio-v1\"). The backend never interprets "
                + "content structure — this value tells the consuming frontend which layout/schema version "
                + "to render. Changing it in production is a breaking change for deployed frontends.",
                example = "portfolio-v1")
        @Size(max = 100, message = "Content schema must be at most 100 characters")
        String contentSchema
) {
    public CreateSiteCommand toCommand(Long ownerUserId) {
        return new CreateSiteCommand(ownerUserId, title, summary, contentSchema);
    }
}
