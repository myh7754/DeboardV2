package org.example.deboardv2.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRequest {
    @Size(min = 8, max = 100)
    public String password;

    @Size(min = 2, max = 20)
    public String nickname;

    public String number;

    @Email
    public String email;
}
