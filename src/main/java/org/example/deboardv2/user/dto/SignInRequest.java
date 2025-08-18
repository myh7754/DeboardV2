package org.example.deboardv2.user.dto;

import lombok.Data;

@Data
public class SignInRequest {
    public String password;
    public String email;
}
