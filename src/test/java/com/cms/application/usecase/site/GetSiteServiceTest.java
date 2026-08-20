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
    private SiteRepository siteRepository;

    private GetSiteService service;

    @BeforeEach
    void setUp() {
        service = new GetSiteService(siteRepository);
    }

    @Test
    void shouldReturnSiteWhenRequesterIsOwner() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now();
        Site site = new Site(siteId, 1L, "My Portfolio", null, null, now, now);
        SiteWithPublicationState result = new SiteWithPublicationState(site, true);

        when(siteRepository.findByIdWithPublicationState(siteId)).thenReturn(Optional.of(result));

        SiteWithPublicationState found = service.getById(new GetSiteCommand(siteId, 1L));

        assertThat(found.site()).isEqualTo(site);
        assertThat(found.published()).isTrue();
    }

    @Test
    void shouldThrowNotFoundWhenSiteDoesNotExist() {
        UUID siteId = UUID.randomUUID();
        when(siteRepository.findByIdWithPublicationState(siteId)).thenReturn(Optional.empty());

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

        when(siteRepository.findByIdWithPublicationState(siteId)).thenReturn(Optional.of(result));

        assertThatThrownBy(() -> service.getById(new GetSiteCommand(siteId, 2L)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
