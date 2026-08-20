package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.PatchSiteCommand;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatchSiteServiceTest {

    @Mock
    private SiteRepository siteRepository;

    private PatchSiteService service;

    @BeforeEach
    void setUp() {
        service = new PatchSiteService(siteRepository);
    }

    @Test
    void shouldOverwriteOnlyProvidedFieldsLastWriteWins() {
        UUID siteId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(3600);
        Instant updatedAt = Instant.now().minusSeconds(60);
        Site existing = new Site(siteId, 1L, "Old Title", "Old Summary", "portfolio-v1", createdAt, updatedAt);
        SiteWithPublicationState current = new SiteWithPublicationState(existing, true);

        when(siteRepository.findByIdWithPublicationState(siteId)).thenReturn(Optional.of(current));
        when(siteRepository.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));

        // Only title provided — summary and contentSchema should be preserved
        PatchSiteCommand command = new PatchSiteCommand(siteId, 1L, "New Title", null, null);

        SiteWithPublicationState result = service.patch(command);

        ArgumentCaptor<Site> captor = ArgumentCaptor.forClass(Site.class);
        verify(siteRepository).save(captor.capture());
        Site saved = captor.getValue();
        assertThat(saved.title()).isEqualTo("New Title");
        assertThat(saved.summary()).isEqualTo("Old Summary");
        assertThat(saved.contentSchema()).isEqualTo("portfolio-v1");
        assertThat(saved.createdAt()).isEqualTo(createdAt);
        assertThat(saved.updatedAt()).isAfter(updatedAt);

        // Publication state is untouched by PATCH
        assertThat(result.published()).isTrue();
    }

    @Test
    void shouldThrowNotFoundWhenSiteDoesNotExist() {
        UUID siteId = UUID.randomUUID();
        when(siteRepository.findByIdWithPublicationState(siteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patch(new PatchSiteCommand(siteId, 1L, "Title", null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowAccessDeniedWhenRequesterIsNotOwner() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now();
        Site existing = new Site(siteId, 1L, "Title", null, null, now, now);
        SiteWithPublicationState current = new SiteWithPublicationState(existing, false);

        when(siteRepository.findByIdWithPublicationState(siteId)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.patch(new PatchSiteCommand(siteId, 2L, "New Title", null, null)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
