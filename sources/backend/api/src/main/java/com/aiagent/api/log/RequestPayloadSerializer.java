package com.aiagent.api.log;

import com.aiagent.domain.log.LogText;
import com.aiagent.support.config.props.HttpLogProps;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 요청 하나를 <b>{@code PARAMETERS} 한 칸</b>으로 — backend/packages.md.
 *
 * <pre>
 * { "method": "POST",
 *   "query":  { "_rid": ["…"] },
 *   "headers":{ "content-type": "application/json", "cookie": "***" },
 *   "body":   "{\"userId\":\"admin\",\"password\":\"***\"}" }
 * </pre>
 *
 * <p><b>응답 body 는 담지 않는다</b>(backend/packages.md) — 목록 응답이 수 MB 다.
 *
 * <h2>마스킹이 이 클래스의 절반이다</h2>
 *
 * <p>전문을 통째로 저장하므로 마스킹이 없으면 <b>로그가 최대 취약점</b>이다(backend/packages.md).
 * 가리는 규칙이 둘이며 <b>일치 방식이 다르다</b> — 파라미터는 부분({@code newPassword}),
 * 헤더는 완전({@code X-Cookie-Consent} 를 가리지 않으려고).
 *
 * <p><b>body 는 파싱하지 않고 훑는다.</b> JSON 을 트리로 읽어 키를 가리면 <i>깨진 JSON 은
 * 아예 기록되지 않는다</i> — 그런데 <b>깨진 요청이야말로 이력에서 보고 싶은 것</b>이다.
 * 그래서 문자열 위에서 {@code "키": 값} 모양만 찾아 값을 바꾼다.
 *
 * <h2>바이트로 잰다</h2>
 *
 * <p>상한이 문자면 전부 한글일 때 <b>3배가 샌다</b>(backend/packages.md 의 실측). 절단은 UTF-8 <b>바이트
 * 경계</b>에서 하며 — 문자 중간에서 자르면 깨진 바이트가 남는다 — 잘렸음을 끝에 적는다
 * ({@link LogText#TRUNCATED_MARK}).
 *
 * <p><b>패키지가 {@code tools.jackson} 이다</b> — Spring Boot 4.1 은 Jackson 3 을 쓴다
 * ({@code ApiErrorWriter} 와 같은 사정).
 */
@Component
public class RequestPayloadSerializer {

    private static final String MASKED = "***";

    /**
     * 전문으로 담는 Content-Type — backend/packages.md 의 화이트리스트.
     *
     * <p><b>화이트리스트인 것이 요점이다.</b> 블랙리스트로 두면 처음 보는 타입이 그대로
     * 담기고, 그 첫 사례가 이미지든 PDF든 <b>바이너리가 {@code nclob} 에 들어간다</b>.
     */
    private static final List<String> TEXTUAL =
            List.of("application/json", "application/xml", "text/", "application/x-www-form-urlencoded");

    private final ObjectMapper json;

    public RequestPayloadSerializer(ObjectMapper json) {
        this.json = json;
    }

    /**
     * @return 저장할 전문. 캐싱되지 않은 요청(멀티파트·필터 꺼짐)이면 body 자리가 빈다 —
     *     <b>{@code null} 을 돌려주지 않는다</b>: 쿼리와 헤더는 그때도 있고, 그것만으로도
     *     <i>"무엇을 불렀나"</i> 에 답한다
     */
    public String serialize(HttpServletRequest request, HttpLogProps props, int maxBytes) {
        if (SensitiveRequest.excluded(request)) return null;
        ObjectNode root = json.createObjectNode();
        root.put("method", request.getMethod());

        ObjectNode query = root.putObject("query");
        for (String name : Collections.list(request.getParameterNames())) {
            query.put(name, masked(name, props) ? MASKED : String.join(", ", values(request, name)));
        }

        ObjectNode headers = root.putObject("headers");
        for (String name : Collections.list(request.getHeaderNames())) {
            if (SensitiveRequest.secretHeader(name)) continue;
            headers.put(name, maskedHeader(name, props) ? MASKED : request.getHeader(name));
        }

        String body = bodyOf(request);
        if (body != null) {
            root.put("body", maskBody(body, props));
        }
        return LogText.truncate(write(root), maxBytes);
    }

    // ── body ────────────────────────────────────────────────────────────

    /**
     * 캐시된 body. <b>{@code getInputStream} 을 부르지 않는다</b> — 이 코드는 응답이 끝난 뒤에
     * 돌고, 그때 스트림을 건드리면 이미 닫힌 것을 읽거나 컨테이너 상태를 흔든다.
     *
     * <p><b>{@code instanceof} 로 보면 언제나 {@code null} 이다</b>(2026-08-30 실물).
     * 인터셉터가 손에 든 요청은 우리 래퍼가 아니라 <b>Security 가 그 위에 한 겹 더 씌운
     * 것</b>이며({@code SecurityContextHolderAwareRequestWrapper} · {@code FirewalledRequest}),
     * 그래서 헤더·쿼리는 위임으로 나오는데 <b>전문만 조용히 비었다</b>. LOG-01 의 알맹이가
     * 그 한 줄에 달려 있었다.
     *
     * <p>{@link WebUtils#getNativeRequest} 가 래퍼 사슬을 끝까지 벗긴다 — 누가 몇 겹을 더
     * 씌우든 우리 래퍼를 찾는다.
     */
    private static String bodyOf(HttpServletRequest request) {
        ContentCachingRequestWrapper cached =
                WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (cached == null) {
            return null;
        }
        byte[] bytes = cached.getContentAsByteArray();
        if (bytes.length == 0) {
            return null;
        }
        String contentType = cached.getContentType();
        if (!isTextual(contentType)) {
            return "[binary: " + bytes.length + " bytes]";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean isTextual(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return TEXTUAL.stream().anyMatch(lower::startsWith);
    }

    /**
     * {@code "키": "값"} 의 값 자리만 바꾼다 — 문자열 위에서 한다.
     *
     * <p><b>배열도 숫자도 아닌 문자열 값만 본다.</b> 비밀번호·토큰이 그 셋 중 문자열이
     * 아닌 경우는 없고, 넓히면 <b>깨진 body 에서 오작동</b>할 여지가 늘어난다.
     */
    private static String maskBody(String body, HttpLogProps props) {
        String masked = body;
        for (String key : props.maskParameters()) {
            // (?i) 대소문자 무시 · [^"]* 로 접두·접미를 함께 잡는다(newPassword · accessToken).
            masked = masked.replaceAll(
                    "(?i)(\"[^\"]*" + java.util.regex.Pattern.quote(key) + "[^\"]*\"\\s*:\\s*)\"[^\"]*\"",
                    "$1\"" + MASKED + "\"");
        }
        return masked;
    }

    // ── 마스킹 판정 ─────────────────────────────────────────────────────

    /** 파라미터는 <b>부분 일치</b>다 — {@code newPassword} 처럼 접두가 붙는 것이 보통이다. */
    private static boolean masked(String name, HttpLogProps props) {
        String lower = name.toLowerCase(Locale.ROOT);
        return props.maskParameters().stream().anyMatch(key -> lower.contains(key.toLowerCase(Locale.ROOT)));
    }

    /** 헤더는 <b>완전 일치</b>다 — 이름이 표준이라 변형이 없다. */
    private static boolean maskedHeader(String name, HttpLogProps props) {
        return props.maskHeaders().stream().anyMatch(name::equalsIgnoreCase);
    }

    private static List<String> values(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values == null ? List.of() : List.of(values);
    }

    private String write(ObjectNode root) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JacksonException e) {
            // 직렬화가 실패해도 이력 한 행은 남아야 한다 — 전문만 이 한 줄이 된다.
            return "[serialization failed: " + e.getMessage() + "]";
        }
    }
}
