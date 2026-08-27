package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.GetSiteCommand;
import com.cms.domain.port.out.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSiteServiceTest {

    @Mock
    private SiteOwnershipGuard siteOwnershipGuard;

    private GetSiteService service;

    @BeforeEach
    void setUp() {
        service = new GetSiteService(siteOwnershipGuard);
    }

    @Test
    void shouldReturnSiteWhenRequesterIsOwner() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now();
        Site site = new Site(siteId, 1L, "My Portfolio", null, null, now, now);
        SiteWithPublicationState result = new SiteWithPublicationState(site, true);

        when(siteOwnershipGuard.requireOwnershipSiteWithPublicationState(siteId, 1L)).thenReturn(result);

        SiteWithPublicationState found = service.getById(new GetSiteCommand(siteId, 1L));

        assertThat(found.site()).isEqualTo(site);
        assertThat(found.published()).isTrue();
    }

    @Test
    void shouldThrowNotFoundWhenSiteDoesNotExist() {
        UUID siteId = UUID.randomUUID();
        when(siteOwnershipGuard.requireOwnershipSiteWithPublicationState(siteId, 1L)).thenThrow(new NotFoundException("Site not found: " + siteId));

        assertThatThrownBy(() -> service.getById(new GetSiteCommand(siteId, 1L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(siteId.toString());
    }

    @Test
    void shouldThrowAccessDeniedWhenRequesterIsNotOwner() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now();
        Site site = new Site(siteId, 1L, "My Portfolio", null, null, now, now);
        SiteWithPublicationState result = new SiteWithPublicationState(site, false);

        when(siteOwnershipGuard.requireOwnershipSiteWithPublicationState(siteId, 2L)).thenThrow(new AccessDeniedException("You do not have access to this site"));

        assertThatThrownBy(() -> service.getById(new GetSiteCommand(siteId, 2L)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
