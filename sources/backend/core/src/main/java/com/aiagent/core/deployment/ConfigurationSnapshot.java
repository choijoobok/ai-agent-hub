// @feat FEAT-001 FEAT-002 FEAT-302
package com.aiagent.core.deployment;
import java.util.UUID;
/** 비밀 없는 전체 설정의 게시 식별. 종류별 환경 공간과 기동 ID는 분리한다. */
public interface ConfigurationSnapshot {
    String environment();
    String settingsId();
    UUID bootId();
}
