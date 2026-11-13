// @feat FEAT-501
package com.aiagent.system.api.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiagent.core.error.BusinessException;
import com.aiagent.domain.user.entity.SysUser;
import com.aiagent.domain.user.service.*;
import com.aiagent.system.api.auth.request.PasswordChangeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Optional;

/** 실제 원자성과 구쿠키 거부는 systemAuthTest에서 검증한다. 여기서는 회전의 호출 조건을 지킨다. */
class PasswordChangeSessionTest {
    private final PasswordChangeService passwords = mock(PasswordChangeService.class);
    private final LoginService login = mock(LoginService.class);
    private final UserAccountService users = mock(UserAccountService.class);
    private final AuthController controller = new AuthController(login, users, null, null, passwords, null, null, null, null, null);
    @Test void successfulPasswordChangeRotatesOnlyAfterCredentialWrite() {
        var user = mock(SysUser.class);
        when(users.find("admin")).thenReturn(Optional.of(user));
        var request = new MockHttpServletRequest();
        controller.changePassword(new PasswordChangeRequest("old", "NewPassw0rd!"), () -> "admin", request);
        var order = inOrder(passwords, login);
        order.verify(passwords).changePassword("admin", "old", "NewPassw0rd!");
        order.verify(login).rotatePassword(user);
        assertThat(request.getSession(false)).isNull();
    }
    @Test void rejectionDoesNotRotateOrClearRestriction() {
        doThrow(new BusinessException(UserErrorCode.PWD_MISMATCH)).when(passwords).changePassword("admin", "wrong", "NewPassw0rd!");
        assertThatThrownBy(() -> controller.changePassword(new PasswordChangeRequest("wrong", "NewPassw0rd!"), () -> "admin", new MockHttpServletRequest())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(login);
    }
}
