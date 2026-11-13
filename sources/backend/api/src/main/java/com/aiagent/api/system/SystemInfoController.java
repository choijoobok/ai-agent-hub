package com.aiagent.api.system;

import com.aiagent.api.system.response.SystemInfoResponse;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.message.service.CacheVersionService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시스템 정보 — frontend/tabs.md.
 *
 * <p><b>{@code common} 계층이다</b>(backend/security.md). 특정 메뉴에 소속시킬 수 없고 매핑하려 들면
 * 메뉴를 전부 나열하게 된다 — {@code SYS_API} 시드의 입력이 아니다.
 *
 * <p><b>익명에게 열지 않는다.</b> 빌드 해시와 활성 프로필은 <i>비밀</i> 은 아니지만
 * <b>공격면을 좁히는 데 쓰이는 정보</b>다 — 어떤 커밋이 도는지 알면 그 커밋의 결함을 찾아볼
 * 수 있다. 로그인한 사람에게만 답하는 것으로 충분하며, 그 사람들이 곧 문의하는 사람이다.
 *
 * <p><b>{@link BuildProperties} 를 {@link ObjectProvider} 로 받는다.</b> 그 빈은
 * {@code build-info.properties} 가 있을 때만 생기고, 그 파일은 Gradle 이 만든다 — IDE 가
 * 클래스만 컴파일해 띄운 서버에는 <b>없다</b>. 직접 주입하면 그 환경에서 <b>기동이 실패</b>하며,
 * 개발자는 빌드 정보 때문에 서버를 못 띄우게 된다.
 */
@RestController
public class SystemInfoController {

    private final Environment environment;
    private final ApplicationContext context;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final CacheVersionService cacheVersions;

    public SystemInfoController(
            Environment environment,
            ApplicationContext context,
            ObjectProvider<BuildProperties> buildProperties,
            CacheVersionService cacheVersions) {
        this.environment = environment;
        this.context = context;
        this.buildProperties = buildProperties;
        this.cacheVersions = cacheVersions;
    }

    @GetMapping("/api/system/common/system/getSystemInfo")
    public ApiResponse<SystemInfoResponse> getSystemInfo() {
        Optional<BuildProperties> build = Optional.ofNullable(buildProperties.getIfAvailable());

        return ApiResponse.success(
                new SystemInfoResponse(
                        List.of(environment.getActiveProfiles()),
                        build.map(BuildProperties::getVersion).orElse(null),
                        build.map(properties -> properties.get("commit")).orElse(null),
                        build.map(properties -> properties.get("branch")).orElse(null),
                        build.map(BuildProperties::getTime).orElse(null),
                        Instant.ofEpochMilli(context.getStartupDate()),
                        cacheVersions.all()),
                TraceId.current());
    }
}
