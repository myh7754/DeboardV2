package org.example.deboardv2.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.user.dto.MemberDetails;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserService userService;
    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();

        MemberDetails memberDetails = OAuth2UserFactory.create(provider, oAuth2User);
        String email = memberDetails.getEmail();
        User user = userRepository.findByEmail(email)
                .orElseGet(() ->
                        userService.create(
                        User.builder()
                        .memberDetails(memberDetails)
                        .build())
                );

        memberDetails.setId(user.getId());
        memberDetails.setRole(user.getRole().toString());
        return memberDetails;
    }
}
