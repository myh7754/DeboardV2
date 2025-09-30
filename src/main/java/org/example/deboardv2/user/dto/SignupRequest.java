package org.example.deboardv2.user.dto;

import lombok.Data;

@Data
public class SignupRequest {
    public String nickname;
    public String password;
    public String email;
//    public String number;
}
