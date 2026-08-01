package starters.springboot.claude.starterkit.user.security;

import org.springframework.security.core.Authentication;
import starters.springboot.claude.starterkit.user.domain.Role;

/**
 * JWT에서 파싱한 인증 주체. Spring Security의 Authentication#getPrincipal()로 노출된다.
 */
public record AuthenticatedUser(Long id, Role role) {

    public static AuthenticatedUser from(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
