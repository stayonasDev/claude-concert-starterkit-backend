package starters.springboot.claude.starterkit.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import starters.springboot.claude.starterkit.user.domain.Role;

/**
 * accessToken 발급/검증 (docs/api-spec.md 1.2). app.jwt.secret은 개발 편의를 위한 기본값이
 * application.yaml에 들어있으며, 실제 배포 시에는 반드시 환경변수로 덮어써야 한다.
 */
@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long validityMillis;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                             @Value("${app.jwt.validity-ms:3600000}") long validityMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMillis = validityMillis;
    }

    public String createToken(Long userId, Role role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMillis);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /** 서명/만료 검증에 실패하면 JwtException(또는 하위 클래스)을 던진다. */
    public AuthenticatedUser parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = Long.valueOf(claims.getSubject());
        Role role = Role.valueOf(claims.get(ROLE_CLAIM, String.class));
        return new AuthenticatedUser(userId, role);
    }
}
