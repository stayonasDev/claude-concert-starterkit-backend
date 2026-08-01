package starters.springboot.claude.starterkit.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.user.domain.User;
import starters.springboot.claude.starterkit.user.dto.SignUpRequest;
import starters.springboot.claude.starterkit.user.dto.SignUpResponse;
import starters.springboot.claude.starterkit.user.repository.UserRepository;

/**
 * docs/use-cases.md UC-01.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .phone(request.phone())
                .build();

        return SignUpResponse.from(userRepository.save(user));
    }
}
