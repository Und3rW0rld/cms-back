package com.cms.adapters.in.web.dto.response;

import com.cms.adapters.config.SecurityConstants;
import com.cms.domain.model.user.AuthToken;
import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponseDTO(
        @Schema(description = "JWT access token. Embeds userId and roles as claims — no DB round-trip needed "
                + "on subsequent authenticated requests.")
        String accessToken,

        @Schema(description = "Authorization header scheme to use with the token.", example = "Bearer")
        String tokenType,

        @Schema(description = "Token lifetime in milliseconds from issuance.", example = "86400000")
        long expiresIn
) {
    public static AuthResponseDTO from(AuthToken token) {
        return new AuthResponseDTO(token.accessToken(), SecurityConstants.BEARER_PREFIX.trim(), token.expiresInMillis());
    }
}
