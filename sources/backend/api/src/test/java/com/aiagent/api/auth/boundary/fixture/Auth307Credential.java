// @feat FEAT-301
package com.aiagent.api.auth.boundary.fixture;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 공통 세션과 JPA 업무 저장의 같은 물리 트랜잭션을 검증하는 합성 자료다. */
@Entity @Table(name="AUTH307_CREDENTIAL")
public class Auth307Credential {
    @Id private String id;
    private String valueText;
    protected Auth307Credential() {}
    public Auth307Credential(String id,String valueText){this.id=id;this.valueText=valueText;}
}
