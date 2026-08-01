package starters.springboot.claude.starterkit.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;
import starters.springboot.claude.starterkit.user.dto.LoginRequest;
import starters.springboot.claude.starterkit.user.dto.LoginResponse;
import starters.springboot.claude.starterkit.user.dto.SignUpRequest;
import starters.springboot.claude.starterkit.user.security.JwtTokenProvider;

/**
 * docs/use-cases.md UC-02.
 */
@Transactional
class AuthServiceTest extends ContainerTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        userService.signUp(new SignUpRequest("login-test@example.com", "password123", "홍길동", null));
    }

    @Test
    void 올바른_자격증명으로_로그인하면_유효한_토큰이_발급된다() {
        LoginResponse response = authService.login(new LoginRequest("login-test@example.com", "password123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(jwtTokenProvider.parse(response.accessToken()).id()).isNotNull();
    }

    @Test
    void 비밀번호가_틀리면_예외가_발생한다() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("login-test@example.com", "wrong-password")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 존재하지_않는_이메일이면_예외가_발생한다() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("no-such-user@example.com", "password123")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }
}
