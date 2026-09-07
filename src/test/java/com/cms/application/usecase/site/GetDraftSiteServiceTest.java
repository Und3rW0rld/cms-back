package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.Content;
import com.cms.domain.model.site.DraftSite;
import com.cms.domain.model.site.Site;
import com.cms.domain.port.in.site.GetDraftSiteCommand;
import com.cms.domain.port.out.DraftSiteRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GetDraftSiteServiceTest {

    @Mock
    SiteOwnershipGuard siteOwnershipGuard;

    @Mock
    DraftSiteRepository draftSiteRepository;

    GetDraftSiteService service;

    @BeforeEach
    void setUp() {
        service = new GetDraftSiteService(siteOwnershipGuard, draftSiteRepository);
    }

    @Test
    void shouldGetDraftSiteByIdWhenRequesterIsOwner() {
        UUID siteId = UUID.randomUUID();
        long userId = 1L;

        Site site = new Site(siteId, userId, "Title", null, null, null, null);
        DraftSite draftSite = new DraftSite(siteId, userId, new Content("{}"), Instant.now());

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId))
                .thenReturn(site);
        when(draftSiteRepository.getDraftSiteById(siteId))
                .thenReturn(Optional.of(draftSite));

        DraftSite result = service.getDraftSiteById(
                new GetDraftSiteCommand(siteId, userId)
        );

        assertThat(result).isEqualTo(draftSite);

        verify(siteOwnershipGuard).requireOwnershipSite(siteId, userId);
        verify(draftSiteRepository).getDraftSiteById(siteId);
    }

    @Test
    void shouldThrowNotFoundWhenDraftSiteDoesNotExist() {
        UUID siteId = UUID.randomUUID();
        long userId = 1L;

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId))
                .thenReturn(new Site(siteId, userId, "Title", null, null, null, null));
        when(draftSiteRepository.getDraftSiteById(siteId))
                .thenReturn(Optional.empty());


        assertThatThrownBy(() -> service.getDraftSiteById(new GetDraftSiteCommand(siteId, userId)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowAccessDeniedWhenRequesterIsNotOwner() {
        UUID siteId = UUID.randomUUID();
        long userId = 2L;

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId))
                .thenThrow(new AccessDeniedException("You do not have access to this site"));

        assertThatThrownBy(() -> service.getDraftSiteById(new GetDraftSiteCommand(siteId, userId)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
