package starters.springboot.claude.starterkit.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.user.domain.User;
import starters.springboot.claude.starterkit.user.dto.LoginRequest;
import starters.springboot.claude.starterkit.user.dto.LoginResponse;
import starters.springboot.claude.starterkit.user.repository.UserRepository;
import starters.springboot.claude.starterkit.user.security.JwtTokenProvider;

/**
 * docs/use-cases.md UC-02.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createToken(user.getId(), user.getRole());
        return new LoginResponse(accessToken);
    }
}
