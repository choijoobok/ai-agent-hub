package com.aiagent.api.auth;

import com.aiagent.domain.user.service.UserAccountService;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.AuthLoginProps;
import com.aiagent.support.config.props.AuthPasswordProps;
import com.aiagent.support.time.BusinessCalendar;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정을 읽어 {@code UserDetails} 4개 플래그로 옮긴다 — sys/user-role.md.
 *
 * <p><b>리포지토리를 직접 부르지 않는다.</b> api 는 domain 의 서비스까지만 본다 —
 * 리포지토리는 domain 의 내부이며, 그것을 api 로 노출하면 트랜잭션 경계가 서비스라는
 * 규칙(backend/packages.md)을 우회하는 길이 생긴다.
 *
 * <p>잠금·기간·비밀번호 만료는 <b>컬럼이 아니라 계산 결과</b>다. 잠금 자동 해제를 배치로
 * 하지 않는 이유가 여기에 있다 — 조회 시 계산하므로 "30분 지났는데 아직 잠김" 이 생기지
 * 않는다(backend/security.md).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserAccountService userAccountService;
    private final ConfigLookup configs;
    private final BusinessCalendar calendar;

    public AppUserDetailsService(
            UserAccountService userAccountService, ConfigLookup configs, BusinessCalendar calendar) {
        this.userAccountService = userAccountService;
        this.configs = configs;
        this.calendar = calendar;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = UsernameNotFoundException.class)
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        return userAccountService
                .find(userId)
                .map(user -> AppUserDetails.of(
                        user,
                        calendar.now(),
                        calendar.today(),
                        configs.get(AuthLoginProps.class).lockReleaseMinutes(),
                        configs.get(AuthPasswordProps.class).expireDays()))
                // 이 예외는 이력에만 FAIL_NO_USER 로 남고 응답 문구는 FAIL_PWD 와 같다(sys/logs.md).
                .orElseThrow(() -> new UsernameNotFoundException(userId));
    }
}
