package org.example.deboardv2.user.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;
import org.example.deboardv2.user.dto.MemberDetails;
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.dto.UpdateRequest;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(unique = true, nullable = false)
    private String nickname;
    @Column(unique = true, nullable = false)
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    private String email;
    private String password;
    @Setter
    @Enumerated(EnumType.STRING)
    private Role role;
    @Enumerated(EnumType.STRING)
    private Provider provider;

    @Builder
    private User(MemberDetails memberDetails) {
        this.nickname = memberDetails.getName();
        this.email = memberDetails.getEmail();
        this.role = Role.ROLE_MEMBER;
        this.provider = Provider.valueOf(memberDetails.getProvider());
    }

    public static User toEntity(SignupRequest dto) {
        User user = new User();
        user.nickname = dto.getNickname();
        user.email = dto.getEmail();
        user.password = dto.getPassword();
        user.role = Role.ROLE_MEMBER;
        return user;
    }

    public void update(UpdateRequest dto) {
        this.nickname = dto.getNickname();
        this.email = dto.getEmail();
        this.password = dto.getPassword();
    }
}
