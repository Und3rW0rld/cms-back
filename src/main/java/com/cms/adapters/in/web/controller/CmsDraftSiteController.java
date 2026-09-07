package com.cms.adapters.in.web.controller;

import com.cms.adapters.config.SecurityConstants;
import com.cms.adapters.in.security.CmsUserDetails;
import com.cms.adapters.in.web.constant.ApiHeaders;
import com.cms.adapters.in.web.dto.request.UpdateDraftSiteRequestDTO;
import com.cms.adapters.in.web.dto.response.DraftSiteResponseDTO;
import com.cms.adapters.in.web.util.HttpHeaderUtils;
import com.cms.adapters.in.web.validation.ValidEtag;
import com.cms.adapters.out.web.mapper.SiteWebMapper;
import com.cms.domain.model.site.DraftSite;
import com.cms.domain.port.in.site.GetDraftSiteCommand;
import com.cms.domain.port.in.site.GetDraftSiteUseCase;
import com.cms.domain.port.in.site.UpdateDraftSiteUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/cms/sites")
@RequiredArgsConstructor
@Slf4j
@Tag(name = SecurityConstants.TAG_CMS_DRAFTS)
public class CmsDraftSiteController {

    private final GetDraftSiteUseCase getDraftSiteUseCase;
    private final UpdateDraftSiteUseCase updateDraftSiteUseCase;
    private final SiteWebMapper siteMapper;

    @GetMapping("/{id}/draft")
    public ResponseEntity<DraftSiteResponseDTO> getDraftSiteById(@PathVariable UUID id,
                                                                 @AuthenticationPrincipal CmsUserDetails principal) {
        log.debug("Received request to get draft site by site id: {} and userid: {}", id, principal.getUserId());
        DraftSite result = getDraftSiteUseCase.getDraftSiteById(new GetDraftSiteCommand(id, principal.getUserId()));
        return ResponseEntity.ok().eTag(HttpHeaderUtils.toEtag(result.version())).body(siteMapper.toDraftSiteResponse(result));
    }

    @PutMapping("/{id}/draft")
    public ResponseEntity<DraftSiteResponseDTO> updateDraftSite(@PathVariable UUID id,
                                                                @ValidEtag @RequestHeader(value = ApiHeaders.IF_MATCH) String ifMatch,
                                                                @AuthenticationPrincipal CmsUserDetails principal,
                                                                @Valid @RequestBody UpdateDraftSiteRequestDTO request) {
        log.debug("Received request to update draft site by site id: {} and userid: {}", id, principal.getUserId());
        DraftSite result = updateDraftSiteUseCase.updateDraftSite(request.toCommand(id, principal.getUserId(), HttpHeaderUtils.fromEtag(ifMatch)));

        return ResponseEntity.ok().eTag(HttpHeaderUtils.toEtag(result.version())).body(siteMapper.toDraftSiteResponse(result));
    }
}
