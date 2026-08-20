package com.cms.adapters.in.web.dto.response;

import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record SiteResponseDTO(
        @Schema(description = "Site identifier.")
        UUID id,

        @Schema(description = "Site title.", example = "Santiago Acevedo — Portfolio")
        String title,

        @Schema(description = "Short summary for the CMS listing display.", example = "Backend Developer Portfolio")
        String summary,

        @Schema(description = "Frontend rendering hint — tells the consuming frontend which layout/schema "
                + "version to render. Null if never set.", example = "portfolio-v1")
        String contentSchema,

        @Schema(description = "True if a site_published row exists for this site — publication state is "
                + "row existence, not a status column (docs §2).")
        boolean published,

        @Schema(description = "When this site was created.")
        Instant createdAt,

        @Schema(description = "When this site's metadata (title/summary/contentSchema) was last updated. "
                + "Does not reflect draft/publish content changes.")
        Instant updatedAt
) {
    public static SiteResponseDTO from(SiteWithPublicationState result) {
        return new SiteResponseDTO(
                result.site().id(),
                result.site().title(),
                result.site().summary(),
                result.site().contentSchema(),
                result.published(),
                result.site().createdAt(),
                result.site().updatedAt()
        );
    }

    public static SiteResponseDTO fromUnpublished(Site site) {
        return new SiteResponseDTO(
                site.id(), site.title(), site.summary(), site.contentSchema(),
                false, site.createdAt(), site.updatedAt()
        );
    }
}
