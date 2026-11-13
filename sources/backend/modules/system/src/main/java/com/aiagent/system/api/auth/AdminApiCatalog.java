// @feat FEAT-501
package com.aiagent.system.api.auth;

import com.aiagent.api.auth.v2.ModuleApiCatalog;
import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import org.springframework.beans.factory.annotation.Autowired;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.domain.progress.JobProgressStore;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/** 승인된 정확 API 목록. 실제 handler를 자동 허용 목록으로 승격하지 않는다. */
@Component
public final class AdminApiCatalog {
    public record Entry(String id, String method, String path, String handler, String action) {}
    private static final Set<String> EXCLUSIVE = Set.of("API-501", "API-503", "API-504", "API-505", "API-508",
            "API-511", "API-512", "API-513", "API-514", "API-516", "API-518", "API-525", "API-528",
            "API-532", "API-534", "API-539", "API-540", "API-541", "API-542", "API-601");
    private static final Set<String> RESTRICTED = Set.of("API-506", "API-507", "API-508", "API-509");
    private final Map<String, Entry> entries;
    private final ModuleApiCatalog catalog;

    public AdminApiCatalog(Environment environment, JobProgressStore progress) throws java.io.IOException { this(environment,progress,List.of()); }
    @Autowired
    public AdminApiCatalog(Environment environment, JobProgressStore progress,List<InstalledAdminRoutes> installed) throws java.io.IOException {
        boolean local = Arrays.asList(environment.getActiveProfiles()).contains("local");
        boolean sample = ClassUtils.isPresent("com.aiagent.sample.api.excel.SampleExcelController", getClass().getClassLoader());
        if (sample && !local) throw new IllegalStateException("sample은 local 프로파일에서만 활성화할 수 있다");
        var values = new LinkedHashMap<String, Entry>();
        try (var input = new ClassPathResource("administrator-apis.txt").getInputStream()) {
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] part = line.split("\\|", -1);
                if (part.length != 5) throw new IllegalStateException("관리자 API 선언 형식이 다르다");
                var entry = new Entry(part[0], part[1], part[2], part[3], part[4]);
                if (entry.id().startsWith("SAMPLE-") && !sample) continue;
                if (values.put(entry.id(), entry) != null) throw new IllegalStateException("관리자 API ID가 중복됐다");
            }
        }
        for(var contribution:installed)for(var route:contribution.routes()) {
            var entry=new Entry(route.id(),route.method(),route.path(),route.handler(),route.action());
            if(values.putIfAbsent(entry.id(),entry)!=null)throw new IllegalStateException("소유 관리자 API ID가 중복됐다");
        }
        entries = Map.copyOf(values);
        catalog = new ModuleApiCatalog(values.values().stream().map(e -> operation(e, progress)).toList(), local);
    }
    private ModuleApiCatalog.Operation operation(Entry entry, JobProgressStore progress) {
        Capability capability = switch (entry.id()) {
            case "API-601" -> Capability.PREPARE_CSRF;
            case "API-501" -> Capability.LOGIN;
            case "API-503", "API-504", "API-505" -> Capability.RECOVERY;
            case "API-508" -> Capability.PASSWORD_CHANGE;
            case "API-514" -> Capability.RESET;
            case "API-509" -> Capability.LOGOUT;
            default -> Capability.READ;
        };
        Set<AuthLevel> levels = entry.path().contains("/public/") ? Set.of(AuthLevel.values())
                : RESTRICTED.contains(entry.id()) ? Set.of(AuthLevel.PASSWORD_CHANGE_REQUIRED, AuthLevel.AUTHENTICATED)
                : Set.of(AuthLevel.AUTHENTICATED);
        boolean self = !entry.path().contains("/service/");
        return new ModuleApiCatalog.Operation(entry.id(), entry.method(), entry.path(), ModuleApiCatalog.Domain.ADMIN,
                levels, self ? null : entry.action(), self, capability, null,
                entry.id().equals("API-603") ? (principal, jobId) -> progress.find(jobId, principal.subjectId()).isPresent() : null,
                EXCLUSIVE.contains(entry.id()));
    }
    public Entry entry(String id) {
        Entry entry = entries.get(id);
        if (entry == null) throw new IllegalStateException("승인된 관리자 API가 아니다");
        return entry;
    }
    public ModuleApiCatalog catalog() { return catalog; }
    public Map<String, String> expectedHandlers() {
        return entries.values().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Entry::id, Entry::handler));
    }
}
