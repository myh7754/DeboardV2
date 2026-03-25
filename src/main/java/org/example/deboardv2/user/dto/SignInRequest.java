package org.example.deboardv2.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SignInRequest {
    @NotBlank
    public String password;

    @NotBlank
    @Email
    public String email;
}
