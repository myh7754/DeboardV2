package org.example.deboardv2.user.dto;

import lombok.Data;

@Data
public class UpdateRequest {
    public String password;
    public String nickname;
    public String number;
    public String email;
}
