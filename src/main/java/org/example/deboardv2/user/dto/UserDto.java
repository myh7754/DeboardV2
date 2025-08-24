package org.example.deboardv2.user.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;
import org.example.deboardv2.user.entity.Role;
import org.example.deboardv2.user.entity.User;

@Data
public class UserDto {
    Long id;
    String email;
    String nickname;
    Role role;

    public static UserDto from(User user) {
        UserDto userDto = new UserDto();
        userDto.id = user.getId();
        userDto.nickname = user.getNickname();
        userDto.role = user.getRole();
        userDto.email = user.getEmail();
        return userDto;
    }
}
