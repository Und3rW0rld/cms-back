package com.cms.adapters.in.web.controller;

import com.cms.adapters.config.SecurityConstants;
import com.cms.adapters.in.security.CmsUserDetails;
import com.cms.adapters.in.web.dto.response.SiteResponseDTO;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.PublishSiteCommand;
import com.cms.domain.port.in.site.PublishSiteUseCase;
import com.cms.domain.port.in.site.UnpublishSiteCommand;
import com.cms.domain.port.in.site.UnpublishSiteUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/cms/sites")
@RequiredArgsConstructor
@Slf4j
@Tag(name = SecurityConstants.TAG_CMS_PUBLISHED)
public class CmsPublishedSiteController {

    private final PublishSiteUseCase publishSiteUseCase;
    private final UnpublishSiteUseCase unpublishSiteUseCase;

    @PostMapping("/{siteId}/publish")
    public ResponseEntity<SiteResponseDTO> publishSite(
            @PathVariable("siteId") UUID siteId,
            @AuthenticationPrincipal CmsUserDetails principal
            ) {
        SiteWithPublicationState result = publishSiteUseCase.publishSite(new PublishSiteCommand(siteId, principal.getUserId()));
        return ResponseEntity.ok().body(SiteResponseDTO.from(result));
    }

    @PostMapping("/{siteId}/unpublish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unpublishSite(
            @PathVariable("siteId") UUID siteId,
            @AuthenticationPrincipal CmsUserDetails principal
    ) {
        unpublishSiteUseCase.unpublishSite(new UnpublishSiteCommand(siteId, principal.getUserId()));
    }

}
