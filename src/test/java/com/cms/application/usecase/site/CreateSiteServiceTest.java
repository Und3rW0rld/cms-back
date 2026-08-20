package com.cms.application.usecase.site;

import com.cms.domain.model.site.Site;
import com.cms.domain.port.in.site.CreateSiteCommand;
import com.cms.domain.port.out.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateSiteServiceTest {

    @Mock
    private SiteRepository siteRepository;

    private CreateSiteService service;

    @BeforeEach
    void setUp() {
        service = new CreateSiteService(siteRepository);
    }

    @Test
    void shouldCreateSiteOwnedByRequester() {
        CreateSiteCommand command = new CreateSiteCommand(1L, "My Portfolio", "A short summary", "portfolio-v1");
        UUID savedId = UUID.randomUUID();
        Instant now = Instant.now();
        Site saved = new Site(savedId, 1L, "My Portfolio", "A short summary", "portfolio-v1", now, now);

        when(siteRepository.save(any(Site.class))).thenReturn(saved);

        Site result = service.create(command);

        ArgumentCaptor<Site> captor = ArgumentCaptor.forClass(Site.class);
        verify(siteRepository).save(captor.capture());
        Site toSave = captor.getValue();
        assertThat(toSave.id()).isNull();
        assertThat(toSave.ownerUserId()).isEqualTo(1L);
        assertThat(toSave.title()).isEqualTo("My Portfolio");
        assertThat(toSave.summary()).isEqualTo("A short summary");
        assertThat(toSave.contentSchema()).isEqualTo("portfolio-v1");

        assertThat(result).isEqualTo(saved);
    }
}
