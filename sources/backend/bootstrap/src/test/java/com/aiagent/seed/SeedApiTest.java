package com.aiagent.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiagent.domain.authz.ActionType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 인가 시드가 스스로와 맞는가 — backend/api.md · sys/menu.md.
 *
 * <p><b>{@code checkApiSeed} 가 보는 것과 다른 것을 본다.</b> 그쪽은 <i>"재생성 결과 ==
 * 커밋본"</i> 이라 <b>생성기가 틀리면 둘 다 같이 틀린다.</b> 여기서 보는 것은 <b>시드끼리의
 * 정합</b>이다 — 없는 API 를 가리키는 매핑, 없는 메뉴를 가리키는 매핑, 아무도 가질 수 없는
 * 권한을 요구하는 매핑.
 *
 * <p>세 번째가 특히 조용하다. 매핑도 있고 API 도 있는데 <b>그 (메뉴, 액션) 을 가진 역할이
 * 없으면</b> 결과는 403 이고, 화면은 <i>"권한이 없습니다"</i> 라고만 말한다 — 원인은 시드에 있다.
 */
class SeedApiTest {

    private static final Path DATA = SeedRows.CHANGELOG.resolve("data");

    private static final String APIS = "006-init-api.csv";
    private static final String MAPPINGS = "007-init-menu-api.csv";

    @Nested
    @DisplayName("API 마스터")
    class ApiMaster {

        @Test
        @DisplayName("비어 있지 않다 — 스캔이 0건이면 전 업무 화면이 403 이다")
        void notEmpty() {
            // 조용히 지나가면 원인이 "권한이 없다" 로 보인다. 생성기의 클래스패스가 어긋난 것이다.
            assertThat(rowsOf(APIS)).isNotEmpty();
        }

        @Test
        @DisplayName("모든 행이 업무 API 경로다 — public·common 은 판정 대상이 아니다")
        void onlyServicePaths() {
            assertThat(rowsOf(APIS)).allSatisfy(row ->
                    assertThat(row.getFirst()).matches("/api/[A-Za-z0-9_-]+/service/.+"));
        }

        @Test
        @DisplayName("액션이 4종 중 하나다 — 오타는 아무도 가질 수 없는 권한이 된다")
        void actionIsOneOfFour() {
            Set<String> allowed = Set.of(ActionType.values()).stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            assertThat(rowsOf(APIS)).allSatisfy(row -> assertThat(row.get(3)).isIn(allowed));
        }

        @Test
        @DisplayName("이름이 비어 있지 않다 — 카탈로그의 컬럼이 쓸모없어진다")
        void everyApiHasAName() {
            assertThat(rowsOf(APIS)).allSatisfy(row -> assertThat(row.get(1)).isNotBlank());
        }

        @Test
        @DisplayName("경로가 겹치지 않는다 — PK 충돌은 배포에서 터진다")
        void urlsAreUnique() {
            List<String> urls = rowsOf(APIS).stream().map(List::getFirst).toList();
            assertThat(urls).doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("메뉴 매핑")
    class MenuMapping {

        @Test
        @DisplayName("매핑이 가리키는 API 가 전부 있다 — FK 가 거부하면 기동이 실패한다")
        void everyMappedApiExists() {
            Set<String> urls = rowsOf(APIS).stream().map(List::getFirst).collect(Collectors.toSet());

            assertThat(rowsOf(MAPPINGS)).allSatisfy(row -> assertThat(row.get(1)).isIn(urls));
        }

        @Test
        @DisplayName("매핑이 가리키는 메뉴가 URL 있는 리프다 — 구조 노드는 권한 대상이 아니다")
        void everyMappedMenuIsALeaf() {
            Set<String> leaves = SeedRows.menus().stream()
                    .filter(row -> SeedRows.str(row, "MENU_URL") != null)
                    .map(row -> SeedRows.str(row, "MENU_CD"))
                    .collect(Collectors.toSet());

            assertThat(leaves).isNotEmpty();
            assertThat(rowsOf(MAPPINGS)).allSatisfy(row -> assertThat(row.getFirst()).isIn(leaves));
        }

        /**
         * <b>매핑만으로는 아무도 그 API 를 부를 수 없다.</b> 판정은 (메뉴, 액션) 쌍이 유효
         * 권한에 있는가이므로(backend/security.md), 그 쌍을 가진 역할이 하나도 없으면 결과는 403 이다.
         *
         * <p>템플릿에서 그 역할은 {@code ADMIN} 하나다 — 사용 프로젝트가 자기 역할을 더하기
         * 전까지 <b>부트스트랩 권한이 곧 상한</b>이며, 그것을 넘는 매핑은 <b>돌아 본 적 없는
         * API</b> 가 된다.
         */
        @Test
        @DisplayName("매핑된 (메뉴, 액션) 을 가진 역할이 있다 — 없으면 그 API 는 언제나 403 이다")
        void someRoleCanReachEveryMapping() {
            Map<String, String> actionOf = rowsOf(APIS).stream()
                    .collect(Collectors.toMap(List::getFirst, row -> row.get(3)));

            /*
              <b>파일 이름을 박지 않는다.</b> 부트스트랩 부여는 004 에서 시작했지만 뒤에
              오는 메뉴는 새 changelog 로 부여를 더한다(backend/persistence.md — 사람 소유 시드는 기존
              changeset 을 고치지 않는다). 이름으로 잡으면 <b>뒤에 온 부여가 검사 밖으로
              나가고</b>, 그때 이 검사는 조용히 통과한다.
            */
            Set<String> granted = SeedRows
                    .insertsAll("SYS_ROLE_MENU_AUTH").stream()
                    .map(row -> SeedRows.str(row, "MENU_CD") + ":" + SeedRows.str(row, "ACTION_CD"))
                    .collect(Collectors.toSet());

            assertThat(granted).isNotEmpty();
            assertThat(rowsOf(MAPPINGS)).allSatisfy(row ->
                    assertThat(row.getFirst() + ":" + actionOf.get(row.get(1))).isIn(granted));
        }
    }

    /** 헤더를 뺀 행. 생성물이라 값에 쉼표가 있으면 따옴표로 감싸여 있다. */
    private static List<List<String>> rowsOf(String file) {
        Path path = DATA.resolve(file);
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(SeedApiTest::splitCsv)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("시드 파일을 읽지 못했다: " + path, e);
        }
    }

    private static List<String> splitCsv(String line) {
        List<String> cells = new java.util.ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (quoted && ch == '"' && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                cell.append('"');
                index++;
            } else if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
