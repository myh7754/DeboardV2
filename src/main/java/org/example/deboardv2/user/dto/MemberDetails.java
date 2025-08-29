package org.example.deboardv2.user.dto;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
@ToString
public class MemberDetails implements OAuth2User {
    @Setter
    private Long id;
    @Setter
    private String name;
    private String email;

    @Setter
    private String role;
    private String provider;
    private Map<String, Object> attributes;

    public static MemberDetails from(User member) {
        MemberDetails memberDetail = new MemberDetails();
        memberDetail.id = member.getId();
        memberDetail.name = member.getNickname();
        memberDetail.email = member.getEmail();
        memberDetail.role = member.getRole().toString();
        return memberDetail;
    }

    @Builder
    public MemberDetails(String name, String email,String provider,Map<String, Object> attributes) {
        this.name = name;
        this.email = email;
        this.provider = provider;
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getName() {
        return name;
    }


}