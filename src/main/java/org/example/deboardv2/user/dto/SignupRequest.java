package org.example.deboardv2.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {
    @NotBlank
    @Size(min = 2, max = 20)
    private String nickname;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotBlank
    @Email
    private String email;
}
