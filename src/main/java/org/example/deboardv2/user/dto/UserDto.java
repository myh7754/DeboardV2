package org.example.deboardv2.user.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;
import org.example.deboardv2.user.entity.Role;

@Data
public class UserDto {
    String nickname;
    String username;
    Role role;

    public UserDto(Long id, String nickname, @Email(message = "올바른 이메일 형식이어야 합니다.") String email) {
    }
}
