package starters.springboot.claude.starterkit.user.dto;

import starters.springboot.claude.starterkit.user.domain.User;

public record SignUpResponse(Long id, String email, String name) {

    public static SignUpResponse from(User user) {
        return new SignUpResponse(user.getId(), user.getEmail(), user.getName());
    }
}
