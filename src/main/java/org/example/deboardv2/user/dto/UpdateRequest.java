package org.example.deboardv2.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRequest {
    @Size(min = 8, max = 100)
    private String password;

    @Size(min = 2, max = 20)
    private String nickname;

    private String number;

    @Email
    private String email;
}
