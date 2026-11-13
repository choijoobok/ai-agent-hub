// @feat FEAT-505
package com.aiagent.system.api.message;

import com.aiagent.api.message.RequestLocale;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.message.service.MessageBundleQueryService;
import com.aiagent.system.api.message.response.MessageBundleResponse;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.MessageProps;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 메시지 번들.
 *
 * <p><b>{@code public} 인 것이 이 API 의 존재 이유다.</b> 로그인 화면에도 문구가 있고 사용자
 * 언어({@code LOCALE_CD})는 로그인해야 알 수 있다 — 순환이며, 그것을 끊는 것이 이 호출이다.
 * 정적 폴백 문구로 로그인 화면을 먼저 그리는 대안은 <b>문구의 원본을 둘로 만든다</b>.
 *
 * <p>정식 경로는 {@code /api/system/public/message/getBundle} 하나다. 로그인 전에도 쓰므로
 * 공개 계층을 유지하되 system 모듈의 소유권을 경로에 드러낸다.
 */
@RestController
public class MessageController {

    private final MessageBundleQueryService bundles;
    private final RequestLocale locale;
    private final ConfigLookup configs;

    public MessageController(
            MessageBundleQueryService bundles,
            RequestLocale locale,
            ConfigLookup configs) {
        this.bundles = bundles;
        this.locale = locale;
        this.configs = configs;
    }

    /**
     * @param lang 프론트가 정한 언어(의 2순위 규칙). 없으면 {@code Accept-Language}
     * @param v 클라이언트가 들고 있는 번들의 버전. 같으면 본문 없이 끝낸다(sys/code.md)
     */
    @GetMapping("/api/system/public/message/getBundle")
    public ApiResponse<MessageBundleResponse> getBundle(
            @RequestParam(required = false) String lang, @RequestParam(required = false) Long v) {

        String resolved = lang == null ? locale.current() : locale.normalize(lang);
        MessageBundleQueryService.Bundle bundle = bundles.get(resolved, v);
        List<String> langs = configs.get(MessageProps.class).supported();

        MessageBundleResponse body =
                bundle.unchanged()
                        ? MessageBundleResponse.unchanged(resolved, langs, bundle.version())
                        : MessageBundleResponse.of(resolved, langs, bundle.version(), bundle.messages());

        return ApiResponse.success(body, TraceId.current());
    }
}
