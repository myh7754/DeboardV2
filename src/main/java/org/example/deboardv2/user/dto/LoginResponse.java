package org.example.deboardv2.user.dto;

import lombok.Data;

@Data
public class LoginResponse {
    JwtToken jwtToken;
    UserDto userDto;

    public LoginResponse(JwtToken jwtToken, UserDto from) {
        this.jwtToken = jwtToken;
        this.userDto = from;
    }
}
