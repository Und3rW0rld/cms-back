package com.cms.application.usecase.site;

import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.out.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListSitesServiceTest {

    @Mock
    private SiteRepository siteRepository;

    private ListSitesService service;

    @BeforeEach
    void setUp() {
        service = new ListSitesService(siteRepository);
    }

    @Test
    void shouldReturnSitesWithPublicationStateForOwner() {
        Instant now = Instant.now();
        Site published = new Site(UUID.randomUUID(), 1L, "Published Site", null, null, now, now);
        Site draft = new Site(UUID.randomUUID(), 1L, "Draft Site", null, null, now, now);
        List<SiteWithPublicationState> expected = List.of(
                new SiteWithPublicationState(published, true),
                new SiteWithPublicationState(draft, false)
        );

        when(siteRepository.findByOwnerWithPublicationState(1L)).thenReturn(expected);

        List<SiteWithPublicationState> result = service.listByOwner(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(SiteWithPublicationState::published).containsExactly(true, false);
    }

    @Test
    void shouldReturnEmptyListWhenOwnerHasNoSites() {
        when(siteRepository.findByOwnerWithPublicationState(99L)).thenReturn(List.of());

        List<SiteWithPublicationState> result = service.listByOwner(99L);

        assertThat(result).isEmpty();
    }
}
