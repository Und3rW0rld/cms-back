package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.Site;
import com.cms.domain.port.out.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SiteOwnershipGuardTest {

    @Mock
    private SiteRepository siteRepository;

    private SiteOwnershipGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SiteOwnershipGuard(siteRepository);
    }

    @Test
    void shouldReturnSiteWhenRequesterIsOwner() {
        UUID siteId = UUID.randomUUID();
        long requesterUserId = 1L;
        Site site = new Site(siteId, requesterUserId, "Title", null, null, null, null);

        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));

        Site result = guard.requireOwnershipSite(siteId, requesterUserId);

        assertThat(result).isEqualTo(site);
        verify(siteRepository).findById(siteId);
    }

    @Test
    void shouldThrowAccessDeniedWhenRequesterIsNotOwner() {
        UUID siteId = UUID.randomUUID();
        long requesterUserId = 1L;
        long ownerUserId = 2L;
        Site site = new Site(siteId, ownerUserId, "Title", null, null, null, null);

        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));

        assertThatThrownBy(() -> guard.requireOwnershipSite(siteId, requesterUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have access to this site");

        verify(siteRepository).findById(siteId);
    }

    @Test
    void shouldThrowNotFoundWhenSiteDoesNotExist() {
        UUID siteId = UUID.randomUUID();
        long requesterUserId = 1L;

        when(siteRepository.findById(siteId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> guard.requireOwnershipSite(siteId, requesterUserId))
                .isInstanceOf(NotFoundException.class);
        verify(siteRepository).findById(siteId);
    }


}
