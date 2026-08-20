package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.Site;
import com.cms.domain.port.in.site.DeleteSiteCommand;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteSiteServiceTest {

    @Mock
    private SiteRepository siteRepository;

    private DeleteSiteService service;

    @BeforeEach
    void setUp() {
        service = new DeleteSiteService(siteRepository);
    }

    @Test
    void shouldDeleteSiteWhenRequesterIsOwner() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now();
        Site existing = new Site(siteId, 1L, "Title", null, null, now, now);

        when(siteRepository.findById(siteId)).thenReturn(Optional.of(existing));

        service.delete(new DeleteSiteCommand(siteId, 1L));

        verify(siteRepository).deleteById(siteId);
    }

    @Test
    void shouldThrowNotFoundWhenSiteDoesNotExist() {
        UUID siteId = UUID.randomUUID();
        when(siteRepository.findById(siteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(new DeleteSiteCommand(siteId, 1L)))
                .isInstanceOf(NotFoundException.class);

        verify(siteRepository, never()).deleteById(any());
    }

    @Test
    void shouldThrowAccessDeniedWhenRequesterIsNotOwnerAndNotDelete() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now();
        Site existing = new Site(siteId, 1L, "Title", null, null, now, now);

        when(siteRepository.findById(siteId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(new DeleteSiteCommand(siteId, 2L)))
                .isInstanceOf(AccessDeniedException.class);

        verify(siteRepository, never()).deleteById(any());
    }
}
