package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.exception.VersionMismatchException;
import com.cms.domain.model.site.Content;
import com.cms.domain.model.site.DraftSite;
import com.cms.domain.port.in.site.UpdateDraftSiteCommand;
import com.cms.domain.port.out.DraftSiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateDraftSiteServiceTest {

    @Mock
    private SiteOwnershipGuard siteOwnershipGuard;

    @Mock
    private DraftSiteRepository draftSiteRepository;

    private UpdateDraftSiteService service;

    @BeforeEach
    void setUp() {
        service = new UpdateDraftSiteService(siteOwnershipGuard, draftSiteRepository);
    }

    @Test
    void shouldUpdateDraftWhenVersionMatches() {
        UUID siteId = UUID.randomUUID();
        long userId = 1L;
        long expectedVersion = 3L;

        Instant now = Instant.now();
        DraftSite current = new DraftSite(siteId, expectedVersion, new Content("{\"a\":1}"), now);
        DraftSite updated = new DraftSite(siteId, expectedVersion + 1, new Content("{\"a\":2}"), now.plusSeconds(1));

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId)).thenReturn(null);
        when(draftSiteRepository.getDraftSiteById(siteId)).thenReturn(Optional.of(current));
        when(draftSiteRepository.update(siteId, new Content("{\"a\":2}"), expectedVersion)).thenReturn(updated);

        DraftSite result = service.updateDraftSite(new UpdateDraftSiteCommand(siteId, userId, "{\"a\":2}", expectedVersion));

        assertThat(result).isEqualTo(updated);
        verify(siteOwnershipGuard).requireOwnershipSite(siteId, userId);
        verify(draftSiteRepository).getDraftSiteById(siteId);
        verify(draftSiteRepository).update(siteId, new Content("{\"a\":2}"), expectedVersion);
    }

    @Test
    void shouldThrowNotFoundWhenDraftDoesNotExist() {
        UUID siteId = UUID.randomUUID();
        long userId = 1L;
        long expectedVersion = 1L;

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId)).thenReturn(null);
        when(draftSiteRepository.getDraftSiteById(siteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDraftSite(new UpdateDraftSiteCommand(siteId, userId, "{}", expectedVersion)))
                .isInstanceOf(NotFoundException.class);

        verify(siteOwnershipGuard).requireOwnershipSite(siteId, userId);
        verify(draftSiteRepository).getDraftSiteById(siteId);
    }

    @Test
    void shouldThrowVersionMismatchWhenExpectedVersionIsStale() {
        UUID siteId = UUID.randomUUID();
        long userId = 1L;
        long expectedVersion = 3L;

        DraftSite current = new DraftSite(siteId, 4L, new Content("{\"a\":1}"), Instant.now());

        when(siteOwnershipGuard.requireOwnershipSite(siteId, userId)).thenReturn(null);
        when(draftSiteRepository.getDraftSiteById(siteId)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.updateDraftSite(new UpdateDraftSiteCommand(siteId, userId, "{\"a\":2}", expectedVersion)))
                .isInstanceOf(VersionMismatchException.class);

        verify(siteOwnershipGuard).requireOwnershipSite(siteId, userId);
        verify(draftSiteRepository).getDraftSiteById(siteId);
    }
}

