package com.cms.domain.port.out;

import com.cms.domain.model.user.AuthToken;

public interface TokenIssuer {

    AuthToken issueToken(String subject);
}
