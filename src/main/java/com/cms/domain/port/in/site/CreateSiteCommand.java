package com.cms.domain.port.in.site;

public record CreateSiteCommand(Long ownerUserId, String title, String summary, String contentSchema) {
}
