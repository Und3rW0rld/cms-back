package com.cms.adapters.in.web.controller;

import com.cms.adapters.in.security.CmsUserDetails;
import com.cms.adapters.in.web.dto.response.SiteResponseDTO;
import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.PublishSiteCommand;
import com.cms.domain.port.in.site.PublishSiteUseCase;
import com.cms.domain.port.in.site.UnpublishSiteCommand;
import com.cms.domain.port.in.site.UnpublishSiteUseCase;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmsPublishedSiteControllerTest {

    @Mock
    private PublishSiteUseCase publishSiteUseCase;

    @Mock
    private UnpublishSiteUseCase unpublishSiteUseCase;

    private CmsPublishedSiteController controller;
    private CmsUserDetails principal;

    @BeforeEach
    void setUp() {
        controller = new CmsPublishedSiteController(publishSiteUseCase, unpublishSiteUseCase);
        principal = new CmsUserDetails(1L, "owner@example.com", "hash", true, List.of());
    }

    @Test
    void shouldReturn200WithPublishedSite() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now();
        Site site = new Site(siteId, 1L, "My Portfolio", "A short summary", "portfolio-v1", now, now);
        SiteWithPublicationState published = new SiteWithPublicationState(site, true);

        when(publishSiteUseCase.publishSite(new PublishSiteCommand(siteId, 1L))).thenReturn(published);

        ResponseEntity<SiteResponseDTO> response = controller.publishSite(siteId, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(siteId);
        assertThat(response.getBody().published()).isTrue();
        assertThat(response.getBody().title()).isEqualTo("My Portfolio");

        verify(publishSiteUseCase).publishSite(new PublishSiteCommand(siteId, 1L));
    }

    @Test
    void shouldUnpublishSite() {
        UUID siteId = UUID.randomUUID();

        controller.unpublishSite(siteId, principal);

        verify(unpublishSiteUseCase).unpublishSite(new UnpublishSiteCommand(siteId, 1L));
    }
}
