// @feat FEAT-509
package com.aiagent.domain.notification;

import com.aiagent.domain.notification.entity.SysNotiInbox;
import com.aiagent.domain.notification.repository.SysNotiInboxRepository;
import com.aiagent.domain.notification.source.SourceNotification.*;
import com.aiagent.domain.message.service.MessageService;
import com.aiagent.core.message.MsgKey;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림을 넣는 <b>유일한 진입점</b> — screen/admin/system/notice/requirements.md.
 *
 * <h2>왜 서비스 하나를 두는가</h2>
 *
 * <p>기존 요청 처리·비밀번호/권한 만료 예고·배치 실패와 CR-034 외부 사건이 같은 저장 진입을 쓴다.
 * 각자 {@code SYS_NOTI_INBOX} 에 직접 쓰면 {@link NotiType} 선택과 저장 형식이 갈린다.
 *
 * <p>그리고 <b>외부 채널이 붙을 때 고칠 자리가 한 곳</b>이어야 한다. screen/README.md 가
 * <i>"인터페이스만 스텁으로 두면 첫 사용자가 시그니처를 정한다"</i> 고 경계한 것의
 * 반대편이다 — 스텁이 아니라 <b>실물 하나</b>를 두고, 채널이 늘면 여기 안에서 갈린다.
 *
 * <h2>지금은 인앱뿐이다 — outbox 가 없다</h2>
 *
 * <p>screen/admin/system/notice/requirements.md. 메일·SMS 어댑터가 없는 배포이므로 {@code SYS_NOTI_OUTBOX} 는 <b>테이블
 * 자체를 만들지 않았다</b>. sys/notification.md 이 <i>"인앱 알림은 outbox 를 거치지 않는다 — DB 에
 * 행을 쓰는 것이 곧 도달"</i> 로 이미 갈라 두었으므로 인앱만으로 성립한다.
 *
 * <p><b>릴레이도 백오프도 재시도도 없다.</b> 그 셋은 <i>밖으로 나가는 것</i>의 장치이고,
 * 여기서는 커밋이 곧 도달이다.
 *
 * <h2>트랜잭션을 열지 않는다 — 부르는 쪽에 참여한다</h2>
 *
 * <p>{@code Propagation.REQUIRED} 가 기본이며 그것이 의도다. 요청 처리(screen/admin/system/notice/requirements.md)가
 * 롤백되면 <b>알림도 없어야</b> 하고, 새 트랜잭션을 열면 <i>처리는 실패했는데 처리됐다는
 * 알림만 간</i> 상태가 만들어진다.
 */
@Service
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final SysNotiInboxRepository inboxes;
    private final Clock clock;
    private final MessageService messages;

    public NotificationSender(SysNotiInboxRepository inboxes, Clock clock, MessageService messages) {
        this.inboxes = inboxes;
        this.clock = clock;
        this.messages = messages;
    }

    /** 검증된 현재 수신자만 같은 REQUIRED에 저장한다. 정확 ID를 flush한 뒤 호출자가 참조를 기록한다. */
    @Transactional
    public Stored storeSourceChangeNotification(VerifiedSourceNotificationOrigin origin, RecipientSnapshot snapshot) {
        snapshot.consume(origin);
        String titleKey = origin.kind() == ChangeKind.RETIRED
                ? MsgKey.NOTI_PORTAL_RETIRED : MsgKey.NOTI_PORTAL_ORG_MOVED;
        var rows = snapshot.sortedDistinctAdminIds().stream().map(id -> SysNotiInbox.builder()
                .userId(id).notiTypeCd(NotiType.PORTAL_CHANGE)
                .title(messages.text(titleKey, snapshot.language(id)))
                .content(messages.text(MsgKey.NOTI_PORTAL_CONTENT, snapshot.language(id)))
                .linkMenuCd(origin.safeTargetRef().menuCd()).linkParam(origin.safeTargetRef().targetRef())
                .creDtm(clock.instant()).build()).toList();
        var stored = inboxes.saveAllAndFlush(rows);
        if (stored.size() != snapshot.sortedDistinctAdminIds().size())
            throw new IllegalStateException("알림 저장 결과가 수신자 수와 다르다");
        return new Stored(stored.stream().map(row -> new StoredNotification(row.getUserId(), row.getNotiId())).toList(),
                snapshot.policyVersion());
    }

    /**
     * 한 사람에게 — <b>이동 링크 없이</b>.
     *
     * <p>{@code LINK_MENU_CD} 가 {@code null} 이면 이동 없음이다(sys/notification.md). 요청 처리 알림이
     * 그 경우이며, 결과를 보러 갈 자리가 알림 모달의 탭이고 <b>탭에는 {@code MENU_CD} 가
     * 없다</b>(screen/_standalone/README.md · screen/admin/system/notice/requirements.md).
     */
    @Transactional
    public void send(String userId, NotiType type, String title, String content) {
        send(userId, type, title, content, null, null);
    }

    /**
     * 한 사람에게 — 클릭하면 메뉴로 간다.
     *
     * @param linkMenuCd <b>URL 이 아니라 메뉴 코드</b>다(sys/notification.md). 권한이 없어진 메뉴로 가는
     * 알림은 화면이 이동 버튼을 비활성한다 — <b>알림이 권한을 주지는 않는다</b>(screen/README.md)
     */
    @Transactional
    public void send(
            String userId,
            NotiType type,
            String title,
            String content,
            String linkMenuCd,
            String linkParam) {
        inboxes.save(SysNotiInbox.builder()
                .userId(userId)
                .notiTypeCd(type)
                .title(title)
                .content(content)
                .linkMenuCd(linkMenuCd)
                .linkParam(linkParam)
                .creDtm(clock.instant())
                .build());
    }

    /**
     * 여러 사람에게 같은 알림 — 만료 예고·배치 실패가 쓴다(screen/admin/system/notice/requirements.md).
     *
     * <p><b>수신자마다 행이 하나다.</b> 한 행에 여러 수신자를 담으면 읽음 상태를 사람마다
     * 가질 수 없고, 그러면 배지가 성립하지 않는다(frontend/tabs.md).
     *
     * <p>{@code saveAll} 이라 JDBC 배치로 묶인다({@code hibernate.jdbc.batch_size}).
     * 채번은 {@code allocationSize=50} 이라 50건마다 한 번 {@code SYS_SEQUENCE} 를 친다 —
     * 이 메서드가 그 설정의 유일한 부담 지점이다.
     */
    @Transactional
    public void sendAll(
            List<String> userIds,
            NotiType type,
            String title,
            String content,
            String linkMenuCd,
            String linkParam) {
        if (userIds.isEmpty()) {
            /*
              **비어 있는 것이 정상일 수 있다** — 만료 예정자가 없는 날이 그렇다.
              로그를 남기는 것은 "보냈는데 아무도 못 받았다" 와 "보낼 사람이 없었다" 가
              운영에서 같아 보이기 때문이다.
            */
            log.debug("알림 대상이 없다 — {} · {}", type, title);
            return;
        }
        java.time.Instant now = clock.instant();
        inboxes.saveAll(userIds.stream()
                .map(userId -> SysNotiInbox.builder()
                        .userId(userId)
                        .notiTypeCd(type)
                        .title(title)
                        .content(content)
                        .linkMenuCd(linkMenuCd)
                        .linkParam(linkParam)
                        .creDtm(now)
                        .build())
                .toList());
    }
}
