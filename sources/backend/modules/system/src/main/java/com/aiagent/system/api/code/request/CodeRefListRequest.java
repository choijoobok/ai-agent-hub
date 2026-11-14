// @feat FEAT-504
package com.aiagent.system.api.code.request;

import jakarta.validation.constraints.*;

/** 참조값의 공백/대소문자는 업무 값이므로 변형하지 않는다. */
public record CodeRefListRequest(@NotBlank @Size(max=30) String grpCd,
        @NotBlank @Size(max=30) String refCd,@NotNull @Size(min=1,max=200) String refVal) {}
