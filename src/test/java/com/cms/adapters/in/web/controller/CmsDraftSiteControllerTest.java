package com.cms.adapters.in.web.controller;

import com.cms.adapters.in.security.CmsUserDetails;
import com.cms.adapters.in.web.dto.request.UpdateDraftSiteRequestDTO;
import com.cms.adapters.in.web.dto.response.DraftSiteResponseDTO;
import com.cms.adapters.out.web.mapper.SiteWebMapper;
import com.cms.domain.model.site.Content;
import com.cms.domain.model.site.DraftSite;
import com.cms.domain.port.in.site.GetDraftSiteCommand;
import com.cms.domain.port.in.site.GetDraftSiteUseCase;
import com.cms.domain.port.in.site.UpdateDraftSiteCommand;
import com.cms.domain.port.in.site.UpdateDraftSiteUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CmsDraftSiteControllerTest {

    @Mock
    private GetDraftSiteUseCase getDraftSiteUseCase;

    @Mock
    private UpdateDraftSiteUseCase updateDraftSiteUseCase;

    @Mock
    private SiteWebMapper siteWebMapper;

    private CmsDraftSiteController controller;
    private CmsUserDetails principal;


    @BeforeEach
    void setUp() {
        controller = new CmsDraftSiteController(getDraftSiteUseCase, updateDraftSiteUseCase, siteWebMapper);
        principal = new CmsUserDetails(1L, "owner@example.com", "hash", true, List.of());
    }

    @Test
    void shouldReturn200WithDraftSiteOnGetDraft() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Content content = new Content("{}");

        DraftSite draftSite = new DraftSite(id, 1L, content, now);
        JsonNode jsonNode = JsonNodeFactory.instance.objectNode();

        when(getDraftSiteUseCase.getDraftSiteById(new GetDraftSiteCommand(id, 1L)))
                .thenReturn(draftSite);

        when(siteWebMapper.toDraftSiteResponse(draftSite))
                .thenReturn(new DraftSiteResponseDTO(jsonNode, 1L, now));

        ResponseEntity<DraftSiteResponseDTO> response =
                controller.getDraftSiteById(id, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isEqualTo(jsonNode);
    }

    @Test
    void shouldReturn200WithDraftSiteOnUpdateDraft() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Content updatedContent = new Content("{\"title\":\"Updated\"}");
        DraftSite updatedDraft = new DraftSite(id, 2L, updatedContent, now);

        JsonNode jsonNode = JsonNodeFactory.instance.objectNode();
        DraftSiteResponseDTO responseDto = new DraftSiteResponseDTO(jsonNode, 2L, now);

        UpdateDraftSiteRequestDTO request = new UpdateDraftSiteRequestDTO("{\"title\":\"Updated\"}");
        String ifMatch = "\"1\"";

        when(updateDraftSiteUseCase.updateDraftSite(new UpdateDraftSiteCommand(id, 1L, request.content(), 1L)))
                .thenReturn(updatedDraft);
        when(siteWebMapper.toDraftSiteResponse(updatedDraft)).thenReturn(responseDto);

        ResponseEntity<DraftSiteResponseDTO> response =
                controller.updateDraftSite(id, ifMatch, principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"2\"");
        assertThat(response.getBody()).isEqualTo(responseDto);
    }
}
