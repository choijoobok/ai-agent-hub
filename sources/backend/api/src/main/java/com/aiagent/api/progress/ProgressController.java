package com.aiagent.api.progress;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.progress.JobProgressStore;
import com.aiagent.domain.progress.JobProgressView;
import org.springframework.data.domain.AuditorAware;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 진행률 폴링 — backend/crosscutting.md · sys/batch.md · playbook/INDEX.md.
 *
 * <p><b>{@code common} 계층이다</b>(backend/security.md). 진행률은 <i>어느 화면에서 시작한 작업인가</i> 와
 * 무관하게 같은 모양이고, 메뉴에 매핑하려 들면 <b>오래 걸리는 작업을 가진 메뉴를 전부</b>
 * 나열하게 된다 — {@code SYS_API} 시드의 입력이 아니다.
 *
 * <p><b>인가는 소유 검사 하나다</b>(sys/batch.md). {@code jobId} 만 알면 조회되는 형태이며,
 * UUID 라 추측이 어렵지만 <i>"추측하기 어렵다"</i> 는 인가가 아니다 — 진행률 문구에는
 * 업무 데이터가 섞이므로(<i>"주문 A-1023 처리 중"</i>) 새면 그것은 진행률이 아니라
 * <b>업무 정보의 노출</b>이다.
 *
 * <p><b>없는 것과 남의 것이 같은 응답이다.</b> sys/batch.md 이 그 이유를 적었다 — 갈라 주면
 * <i>그 {@code jobId} 는 존재한다</i> 가 새어 나가고, 감추려던 것이 <b>존재 여부</b>이므로
 * 그 한 줄로 목적이 사라진다.
 *
 * <p><b>상태는 404 가 아니라 400 이다</b>(2026-09-02 — 구현). sys/batch.md 은 404 로 적었지만
 * backend/api.md 의 상태 코드는 <b>넷으로 닫혀 있고</b>({@code ErrorType}) 404 가 그 안에 없다.
 * 여기서 다섯째를 여는 것은 이 슬라이스의 결정이 아니며, <b>지켜야 하는 성질은 상태
 * 숫자가 아니라 <i>둘이 구별되지 않는 것</i></b> 이다 — 그것은 같은 코드로 답하는 것으로
 * 이미 만족된다. 상태를 맞추는 일은 backend/api.md 의 미결로 남긴다.
 */
@RestController
public class ProgressController {

    private final JobProgressStore store;
    private final AuditorAware<String> auditor;

    public ProgressController(JobProgressStore store, AuditorAware<String> auditor) {
        this.store = store;
        this.auditor = auditor;
    }

    @GetMapping("/api/system/common/progress/getProgress/{jobId}")
    public ApiResponse<JobProgressView> getProgress(@PathVariable String jobId) {
        String userId = auditor.getCurrentAuditor()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));

        JobProgressView view = store.find(jobId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        return ApiResponse.success(view, TraceId.current());
    }
}
