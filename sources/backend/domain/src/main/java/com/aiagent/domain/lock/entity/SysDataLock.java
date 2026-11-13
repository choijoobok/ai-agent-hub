package com.aiagent.domain.lock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 분산 락 — backend/persistence.md · sys/batch.md.
 *
 * <p><b>선점 판정은 PK INSERT 의 성공 여부다.</b> 별도의 상태 컬럼도 <i>잠김 여부</i> 플래그도
 * 없다 — 행이 있으면 잠긴 것이고 없으면 풀린 것이다. 플래그로 두면 <i>읽고-판단하고-쓰는</i>
 * 사이가 생겨 락을 만드는 코드가 <b>스스로 락을 필요로 한다</b>.
 *
 * <p><b>감사 4컬럼이 없다</b>(sys/batch.md). 인프라 테이블이며 기계가 쓴다 — <i>"누가 이 행을
 * 만들었나"</i> 라는 질문이 성립하지 않는다. {@link #userId} 는 감사가 아니라 <b>진단용</b>
 * 이고 FK 도 걸지 않는다(sys/batch.md).
 *
 * <h2>시각이 셋이고 답하는 것이 다르다</h2>
 *
 * <table>
 *   <tr><td>{@link #creDtm}</td><td>언제 잡았나 — <b>진단</b></td></tr>
 *   <tr><td>{@link #expireDtm}</td><td><b>장애 안전망</b> — 프로세스가 죽어 해제를 못 했을 때의 회수 시한</td></tr>
 *   <tr><td>{@link #releaseDtm}</td><td><b>쿨다운</b> — 작업이 끝나도 이 시각까지는 다시 못 잡는다</td></tr>
 * </table>
 *
 * <p>뒤의 둘을 한 컬럼으로 합치지 않는 이유가 backend/persistence.md 다 — 합치면 <b>값의 의미가 옵션에 따라
 * 바뀌고</b>, 기본값을 그대로 둔 채 옵션만 끄면 같은 키가 5분에 한 번만 도는 사고가 난다.
 */
@Entity
@Table(name = "SYS_DATA_LOCK")
@IdClass(SysDataLockId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SysDataLock {

    @Id
    @Column(name = "LOCK_TYPE", length = 50, nullable = false)
    private String lockType;

    @Id
    @Column(name = "LOCK_KEY", length = 200, nullable = false)
    private String lockKey;

    /** 이 시각이 지난 락은 <b>회수 대상</b>이다 — 조건부 DELETE 가 읽는다(backend/persistence.md). */
    @Column(name = "EXPIRE_DTM", nullable = false)
    private Instant expireDtm;

    /** 쿨다운 종료 시각. {@code null} 이면 <b>작업 종료 즉시 해제</b>다(backend/persistence.md). */
    @Column(name = "RELEASE_DTM")
    private Instant releaseDtm;

    /** sys/batch.md — {@code SYS_BATCH_HISTORY} · {@code SYS_NOTI_OUTBOX} 와 <b>같은 값</b>이다. */
    @Column(name = "NODE_ID", length = 100, nullable = false)
    private String nodeId;

    /** 진단용 — 누가 유발했나. FK 를 걸지 않는다(sys/batch.md). */
    @Column(name = "USER_ID", length = 30)
    private String userId;

    /** 배치 lease 소유 실행. 일반 잠금과 쿨다운만 남은 행은 null이다. FK는 두지 않는다. */
    @Column(name = "BATCH_HIST_ID")
    private Long batchHistId;

    @Column(name = "CRE_DTM", nullable = false)
    private Instant creDtm;

    public SysDataLock(
            String lockType,
            String lockKey,
            Instant expireDtm,
            Instant releaseDtm,
            String nodeId,
            String userId,
            Instant creDtm) {
        this.lockType = lockType;
        this.lockKey = lockKey;
        this.expireDtm = expireDtm;
        this.releaseDtm = releaseDtm;
        this.nodeId = nodeId;
        this.userId = userId;
        this.creDtm = creDtm;
    }
}
