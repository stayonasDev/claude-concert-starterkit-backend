package starters.springboot.claude.starterkit.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;
import starters.springboot.claude.starterkit.user.dto.SignUpRequest;
import starters.springboot.claude.starterkit.user.dto.SignUpResponse;
import starters.springboot.claude.starterkit.user.repository.UserRepository;

/**
 * docs/use-cases.md UC-01.
 */
@Transactional
class UserServiceTest extends ContainerTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 정상적으로_회원가입하면_비밀번호가_암호화되어_저장된다() {
        SignUpRequest request = new SignUpRequest("user1@example.com", "password123", "홍길동", "010-1234-5678");

        SignUpResponse response = userService.signUp(request);

        assertThat(response.email()).isEqualTo("user1@example.com");
        var saved = userRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("password123");
    }

    @Test
    void 이미_가입된_이메일이면_예외가_발생한다() {
        SignUpRequest request = new SignUpRequest("dup@example.com", "password123", "홍길동", null);
        userService.signUp(request);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.signUp(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
