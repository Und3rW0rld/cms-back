package com.cms.adapters.in.web.dto.response;

import com.cms.adapters.config.SecurityConstants;
import com.cms.domain.model.user.AuthToken;

public record AuthResponseDTO(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static AuthResponseDTO from(AuthToken token) {
        return new AuthResponseDTO(token.accessToken(), SecurityConstants.BEARER_PREFIX.trim(), token.expiresInMillis());
    }
}
