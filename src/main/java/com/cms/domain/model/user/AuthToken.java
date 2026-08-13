package com.cms.domain.model.user;

public record AuthToken(String accessToken, long expiresInMillis) {
}
