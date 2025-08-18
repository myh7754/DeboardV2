package org.example.deboardv2.user.service;

import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.user.dto.MemberDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

@Slf4j
public class OAuth2UserFactory {
    public static MemberDetails create(String provider, OAuth2User oauth2User) {
        Map<String, Object> attributes = oauth2User.getAttributes();
        switch (provider.toUpperCase()) {
            case "GOOGLE" -> {
                return MemberDetails.builder()
                        .name(attributes.get("name").toString())
                        .email(attributes.get("email").toString())
                        .provider(provider.toUpperCase())
                        .attributes(attributes)
                        .build();
            }
            case "KAKAO" -> {
                Map<String, String> properties = (Map<String, String>) attributes.get("properties");
                return MemberDetails.builder()
                        .name(properties.get("nickname"))
                        .email(attributes.get("id").toString() + "@kakao.com")
                        .provider(provider.toUpperCase())
                        .attributes(attributes)
                        .build();
            }
            case "NAVER" -> {
                Map<String, String> properties = (Map<String, String>) attributes.get("response");
                return MemberDetails.builder()
                        .name(properties.get("name"))
                        .email(properties.get("email"))
                        .provider(provider.toUpperCase())
                        .attributes(attributes)
                        .build();
            }
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        }
    }
}
