package com.cms.adapters.in.web.controller;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmsSiteControllerTest {

    @Mock
    private CreateSiteUseCase createSiteUseCase;

    @Mock
    private ListSitesUseCase listSitesUseCase;

    @Mock
    private GetSiteUseCase getSiteUseCase;

    @Mock
    private PatchSiteUseCase patchSiteUseCase;

    @Mock
    private DeleteSiteUseCase deleteSiteUseCase;

    private CmsSiteController controller;
    private CmsUserDetails principal;

    @BeforeEach
    void setUp() {
        controller = new CmsSiteController(createSiteUseCase, listSitesUseCase, getSiteUseCase,
                patchSiteUseCase, deleteSiteUseCase);
        principal = new CmsUserDetails(1L, "owner@example.com", "hash", true, List.of());
    }

    @Test
    void shouldReturn201WithUnpublishedSiteOnCreate() {
        CreateSiteRequestDTO request = new CreateSiteRequestDTO("My Portfolio", "A short summary", "portfolio-v1");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Site created = new Site(id, 1L, "My Portfolio", "A short summary", "portfolio-v1", now, now);

        when(createSiteUseCase.create(request.toCommand(1L))).thenReturn(created);

        ResponseEntity<SiteResponseDTO> response = controller.create(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id);
        assertThat(response.getBody().published()).isFalse();
    }

    @Test
    void shouldReturn200WithSitesOnList() {
        Instant now = Instant.now();
        Site published = new Site(UUID.randomUUID(), 1L, "Published Site", null, null, now, now);
        when(listSitesUseCase.listByOwner(1L)).thenReturn(List.of(new SiteWithPublicationState(published, true)));

        ResponseEntity<List<SiteResponseDTO>> response = controller.list(principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).published()).isTrue();
    }

    @Test
    void shouldReturn200WithSiteOnGetById() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Site site = new Site(id, 1L, "My Portfolio", null, null, now, now);
        when(getSiteUseCase.getById(new GetSiteCommand(id, 1L)))
                .thenReturn(new SiteWithPublicationState(site, false));

        ResponseEntity<SiteResponseDTO> response = controller.getById(id, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id);
    }

    @Test
    void shouldReturn200WithPatchedSiteOnPatch() {
        UUID id = UUID.randomUUID();
        PatchSiteRequestDTO request = new PatchSiteRequestDTO("New Title", null, null);
        Instant now = Instant.now();
        Site patched = new Site(id, 1L, "New Title", null, null, now, now);

        when(patchSiteUseCase.patch(request.toCommand(id, 1L)))
                .thenReturn(new SiteWithPublicationState(patched, false));

        ResponseEntity<SiteResponseDTO> response = controller.patch(id, request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().title()).isEqualTo("New Title");
    }

    @Test
    void shouldReturn204OnDelete() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = controller.delete(id, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        org.mockito.Mockito.verify(deleteSiteUseCase).delete(new DeleteSiteCommand(id, 1L));
    }
}
