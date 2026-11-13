package com.aiagent.api.message;

import com.aiagent.api.auth.AppUserDetails;
import com.aiagent.core.security.SessionPersistencePort.Identity;
import com.aiagent.domain.progress.RequestLang;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.MessageProps;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 이 요청의 언어 — backend/packages.md ·.
 *
 * <p>순서는 <b>둘</b>이다. 세션이 있으면 {@code SYS_USER.LOCALE_CD}, 없으면 요청의 언어
 * 힌트({@code Accept-Language})다. <b>"서버 기본 언어" 를 3순위로 두지 않는다</b> —
 * {@link #normalize} 가 모든 입력을 지원 언어 하나로 닫으므로 그 자리가 존재하지 않는다.
 *
 * <p><b>필터로 만들지 않는다.</b> {@code LocaleContextHolder} 를 채우는 필터를 두려면 보안
 * 필터 뒤에 끼워야 하는데, 401 을 쓰는 자리는 그보다 <b>앞</b>이라 그때는 비어 있다 —
 * 있는 줄 알고 읽으면 조용히 기본 언어가 나간다. 부르는 자리에서 그때그때 읽는 것이
 * {@code LoginContexts.current()} 와 같은 방식이다.
 *
 * <h2>비동기 경로가 생겼다 — {@link RequestLang} (2026-09-02)</h2>
 *
 * <p>오래 걸리는 작업이 별도 풀에서 도는데(backend/crosscutting.md) 그 스레드에는 요청도
 * {@code SecurityContext} 도 없다. 실패 문구를 사용자 언어로 완성하려면 그 값을
 * <b>맡기기 전에</b> 받아야 하므로, 도메인이 선언한 구멍을 이 클래스가 채운다 —
 * 도메인은 api 를 참조할 수 없기 때문이다({@code LayerDependencyTest}).
 *
 * <p><b>{@code LocaleContextHolder} 전파(backend/crosscutting.md)는 여전히 하지 않는다.</b> 값을 <b>한 번
 * 읽어 넘기는</b> 것으로 이 경로가 닫히고, 홀더를 채우는 필터는 위 문단의 이유로 여전히
 * 두지 않는다.
 */
@Component
public class RequestLocale implements RequestLang {

    private final ConfigLookup configs;

    public RequestLocale(ConfigLookup configs) {
        this.configs = configs;
    }

    @Override
    public String current() {
        String fromSession = principalLocale();
        return normalize(fromSession != null ? fromSession : acceptLanguage());
    }

    /**
     * 어떤 입력이든 지원 언어 하나로 닫는다 — 순위 2.
     *
     * <p>지역을 버리고 언어만 본다({@code ko-KR} → {@code ko}). 모르는 언어는
     * {@code browserFallbackLang} 이며 <b>기본 언어가 아니다</b> — 일본어 브라우저에
     * 한국어를 주는 것은 답이 아니다.
     */
    public String normalize(String raw) {
        MessageProps props = configs.get(MessageProps.class);
        if (raw == null || raw.isBlank()) {
            return props.browserFallbackLang();
        }
        String language = Locale.forLanguageTag(raw.replace('_', '-')).getLanguage();
        if (language.isEmpty()) {
            language = raw.toLowerCase(Locale.ROOT);
        }
        return props.supported().contains(language) ? language : props.browserFallbackLang();
    }

    private static String principalLocale() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof Identity identity) return identity.locale();
        return authentication != null
                        && authentication.getPrincipal() instanceof AppUserDetails details
                ? details.localeCd()
                : null;
    }

    /** 서블릿이 {@code Accept-Language} 를 이미 파싱해 준다 — 헤더를 손으로 자르지 않는다. */
    private static String acceptLanguage() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getLocale() == null ? null : request.getLocale().getLanguage();
        }
        return null;
    }
}
