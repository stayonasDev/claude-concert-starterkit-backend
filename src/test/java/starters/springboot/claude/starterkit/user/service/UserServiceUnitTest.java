package starters.springboot.claude.starterkit.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.user.domain.User;
import starters.springboot.claude.starterkit.user.dto.SignUpRequest;
import starters.springboot.claude.starterkit.user.dto.SignUpResponse;
import starters.springboot.claude.starterkit.user.repository.UserRepository;

/**
 * Repository/PasswordEncoder를 Mockito로 mock하는 순수 단위 테스트.
 * 컨테이너 기동이 필요 없어 {@link UserServiceTest}(TestContainers 통합 테스트)보다 빠르게 실행된다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void 정상_회원가입시_비밀번호를_암호화해_저장한다() {
        SignUpRequest request = new SignUpRequest("user@example.com", "P@ssw0rd!", "홍길동", "010-1234-5678");
        given(userRepository.existsByEmail("user@example.com")).willReturn(false);
        given(passwordEncoder.encode("P@ssw0rd!")).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        SignUpResponse response = userService.signUp(request);

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.name()).isEqualTo("홍길동");
        verify(passwordEncoder).encode("P@ssw0rd!");
    }

    @Test
    void 이미_가입된_이메일이면_저장하지_않고_예외가_발생한다() {
        SignUpRequest request = new SignUpRequest("dup@example.com", "P@ssw0rd!", "홍길동", null);
        given(userRepository.existsByEmail("dup@example.com")).willReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.signUp(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(userRepository, never()).save(any());
    }
}
