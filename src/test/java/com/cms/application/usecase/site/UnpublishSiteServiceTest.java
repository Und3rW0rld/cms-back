package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.Site;
import com.cms.domain.port.in.site.UnpublishSiteCommand;
import com.cms.domain.port.out.PublishedSiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnpublishSiteServiceTest {

    @Mock
    private SiteOwnershipGuard siteOwnershipGuard;

    @Mock
    private PublishedSiteRepository publishedSiteRepository;

    private UnpublishSiteService service;

    @BeforeEach
    void setUp() {
        service = new UnpublishSiteService(siteOwnershipGuard, publishedSiteRepository);
    }

    @Test
    void shouldDeletePublishedRowWhenRequesterIsOwner() {
        UUID siteId = UUID.randomUUID();
        long userId = 1L;
        Instant now = Instant.now();

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId))
                .thenReturn(new Site(siteId, userId, "Title", null, null, now, now));

        service.unpublishSite(new UnpublishSiteCommand(siteId, userId));

        verify(siteOwnershipGuard).requireOwnershipSite(siteId, userId);
        verify(publishedSiteRepository).deleteBySiteId(siteId);
        verify(publishedSiteRepository, never()).upsert(any());
    }

    @Test
    void shouldThrowNotFoundWhenSiteDoesNotExist() {
        UUID siteId = UUID.randomUUID();
        long userId = 1L;

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId))
                .thenThrow(new NotFoundException("Site not found: " + siteId));

        assertThatThrownBy(() -> service.unpublishSite(new UnpublishSiteCommand(siteId, userId)))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(publishedSiteRepository);
    }

    @Test
    void shouldThrowAccessDeniedWhenRequesterIsNotOwner() {
        UUID siteId = UUID.randomUUID();
        long userId = 2L;

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId))
                .thenThrow(new AccessDeniedException("You do not have access to this site"));

        assertThatThrownBy(() -> service.unpublishSite(new UnpublishSiteCommand(siteId, userId)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(publishedSiteRepository);
    }
}
