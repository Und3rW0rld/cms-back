package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.Content;
import com.cms.domain.model.site.DraftSite;
import com.cms.domain.model.site.PublishedSite;
import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.PublishSiteCommand;
import com.cms.domain.port.out.DraftSiteRepository;
import com.cms.domain.port.out.PublishedSiteRepository;
import com.cms.domain.port.out.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishSiteServiceTest {

    @Mock
    SiteOwnershipGuard siteOwnershipGuard;
    @Mock
    PublishedSiteRepository publishedSiteRepository;
    @Mock
    SiteRepository siteRepository;
    @Mock
    DraftSiteRepository draftSiteRepository;

    private PublishSiteService service;

    @BeforeEach
    void setUp() {
        service = new PublishSiteService(
                siteOwnershipGuard, publishedSiteRepository, draftSiteRepository, siteRepository);
    }

    @Test
    void shouldPublishDraftContentAndReturnPublishedSite() {
        UUID siteId = UUID.randomUUID();
        long userId = 1L;
        Instant now = Instant.now();
        Content draftContent = new Content("{\"title\":\"Portfolio\"}");
        DraftSite draft = new DraftSite(siteId, 3L, draftContent, now);
        Site site = new Site(siteId, userId, "Title", null, "portfolio-v1", now, now);
        SiteWithPublicationState published = new SiteWithPublicationState(site, true);

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId)).thenReturn(site);
        when(draftSiteRepository.getDraftSiteById(siteId)).thenReturn(Optional.of(draft));
        when(siteRepository.findByIdWithPublicationState(siteId)).thenReturn(Optional.of(published));

        SiteWithPublicationState result = service.publishSite(new PublishSiteCommand(siteId, userId));

        assertThat(result).isEqualTo(published);
        assertThat(result.published()).isTrue();

        ArgumentCaptor<PublishedSite> captor = ArgumentCaptor.forClass(PublishedSite.class);
        verify(publishedSiteRepository).upsert(captor.capture());
        PublishedSite snapshot = captor.getValue();
        assertThat(snapshot.id()).isEqualTo(siteId);
        assertThat(snapshot.content()).isEqualTo(draftContent);
        assertThat(snapshot.publishedAt()).isNotNull();

        verify(draftSiteRepository, never()).save(any());
        verify(draftSiteRepository, never()).update(any(), any(), anyLong());
    }

    @Test
    void shouldThrowNotFoundWhenSiteDoesNotExist() {
        UUID siteId = UUID.randomUUID();
        long userId = 1L;

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId))
                .thenThrow(new NotFoundException("Site not found: " + siteId));

        assertThatThrownBy(() -> service.publishSite(new PublishSiteCommand(siteId, userId)))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(draftSiteRepository, publishedSiteRepository);
    }

    @Test
    void shouldThrowAccessDeniedWhenRequesterIsNotOwner() {
        UUID siteId = UUID.randomUUID();
        long userId = 2L;

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId))
                .thenThrow(new AccessDeniedException("You do not have access to this site"));

        assertThatThrownBy(() -> service.publishSite(new PublishSiteCommand(siteId, userId)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(draftSiteRepository, publishedSiteRepository);
    }

    @Test
    void shouldThrowNotFoundWhenDraftDoesNotExist() {
        UUID siteId = UUID.randomUUID();
        long userId = 1L;

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId))
                .thenReturn(new Site(siteId, userId, "Title", null, null, Instant.now(), Instant.now()));
        when(draftSiteRepository.getDraftSiteById(siteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publishSite(new PublishSiteCommand(siteId, userId)))
                .isInstanceOf(NotFoundException.class);

        verify(publishedSiteRepository, never()).upsert(any());
    }

    @Test
    void shouldThrowNotFoundWhenSiteDisappearsAfterUpsert() {
        UUID siteId = UUID.randomUUID();
        long userId = 1L;
        DraftSite draft = new DraftSite(siteId, 1L, Content.empty(), Instant.now());

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId))
                .thenReturn(new Site(siteId, userId, "Title", null, null, Instant.now(), Instant.now()));
        when(draftSiteRepository.getDraftSiteById(siteId)).thenReturn(Optional.of(draft));
        when(siteRepository.findByIdWithPublicationState(siteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publishSite(new PublishSiteCommand(siteId, userId)))
                .isInstanceOf(NotFoundException.class);

        verify(publishedSiteRepository).upsert(any());
    }
}
