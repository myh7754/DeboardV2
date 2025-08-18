package org.example.deboardv2.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtToken {
    String accessToken;
    String refreshToken;
}
