package org.example.deboardv2.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.deboardv2.user.entity.Role;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenBody {
    private Long memberId;
    private String nickname;
    private Role role;
}
