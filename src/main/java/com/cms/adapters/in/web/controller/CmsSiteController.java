package com.cms.adapters.in.web.controller;

import com.cms.adapters.config.SecurityConstants;
import com.cms.adapters.in.security.CmsUserDetails;
import com.cms.adapters.in.web.dto.request.CreateSiteRequestDTO;
import com.cms.adapters.in.web.dto.request.PatchSiteRequestDTO;
import com.cms.adapters.in.web.dto.response.SiteResponseDTO;
import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.CreateSiteUseCase;
import com.cms.domain.port.in.site.DeleteSiteCommand;
import com.cms.domain.port.in.site.DeleteSiteUseCase;
import com.cms.domain.port.in.site.GetSiteCommand;
import com.cms.domain.port.in.site.GetSiteUseCase;
import com.cms.domain.port.in.site.ListSitesUseCase;
import com.cms.domain.port.in.site.PatchSiteUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cms/sites")
@RequiredArgsConstructor
@Slf4j
@Tag(name = SecurityConstants.TAG_CMS_SITES)
public class CmsSiteController {

    private final CreateSiteUseCase createSiteUseCase;
    private final ListSitesUseCase listSitesUseCase;
    private final GetSiteUseCase getSiteUseCase;
    private final PatchSiteUseCase patchSiteUseCase;
    private final DeleteSiteUseCase deleteSiteUseCase;

    @PostMapping
    public ResponseEntity<SiteResponseDTO> create(@Valid @RequestBody CreateSiteRequestDTO request,
                                                    @AuthenticationPrincipal CmsUserDetails principal) {
        log.debug("Received request to create site for user: {}", principal.getUserId());
        Site created = createSiteUseCase.create(request.toCommand(principal.getUserId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(SiteResponseDTO.fromUnpublished(created));
    }

    @GetMapping
    public ResponseEntity<List<SiteResponseDTO>> list(@AuthenticationPrincipal CmsUserDetails principal) {
        log.debug("Received request to list sites for user: {}", principal.getUserId());
        List<SiteWithPublicationState> sites = listSitesUseCase.listByOwner(principal.getUserId());
        return ResponseEntity.ok(sites.stream().map(SiteResponseDTO::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SiteResponseDTO> getById(@PathVariable UUID id,
                                                     @AuthenticationPrincipal CmsUserDetails principal) {
        log.debug("Received request to get site by id: {}", principal.getUserId());
        SiteWithPublicationState result = getSiteUseCase.getById(new GetSiteCommand(id, principal.getUserId()));
        return ResponseEntity.ok(SiteResponseDTO.from(result));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SiteResponseDTO> patch(@PathVariable UUID id,
                                                   @Valid @RequestBody PatchSiteRequestDTO request,
                                                   @AuthenticationPrincipal CmsUserDetails principal) {
        log.debug("Received request to patch site by id: {}", principal.getUserId());
        SiteWithPublicationState result = patchSiteUseCase.patch(request.toCommand(id, principal.getUserId()));
        return ResponseEntity.ok(SiteResponseDTO.from(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                         @AuthenticationPrincipal CmsUserDetails principal) {
        log.debug("Received request to delete site by id: {}", principal.getUserId());
        deleteSiteUseCase.delete(new DeleteSiteCommand(id, principal.getUserId()));
        return ResponseEntity.noContent().build();
    }
}
