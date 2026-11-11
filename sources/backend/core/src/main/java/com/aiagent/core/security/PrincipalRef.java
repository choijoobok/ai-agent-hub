// @feat FEAT-301
package com.aiagent.core.security;

import java.io.Serializable;
import java.util.Objects;

/** 서버가 검증한 영역 안에서만 의미가 있는 안정 주체 ID. */
public record PrincipalRef(AuthDomain domain, String subjectId) implements Serializable {
    private static final long serialVersionUID = 1L;
    public PrincipalRef {
        Objects.requireNonNull(domain);
        if (subjectId == null || subjectId.isBlank() || subjectId.length() > 80)
            throw new IllegalArgumentException("주체 식별자가 올바르지 않다");
    }
    public String indexKey() { return domain == AuthDomain.ADMIN ? subjectId : "PORTAL:" + subjectId; }
}
