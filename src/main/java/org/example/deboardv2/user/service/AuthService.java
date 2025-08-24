package org.example.deboardv2.user.service;

import org.example.deboardv2.user.dto.JwtToken;
import org.example.deboardv2.user.dto.LoginResponse;
import org.example.deboardv2.user.dto.SignInRequest;
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.entity.User;

public interface AuthService {

    public User signUp(SignupRequest signupRequest);
    public LoginResponse signIn(SignInRequest signInRequest);
    public void logout(String refreshToken);
    public String sendEmailAuthCode(String email);
    public Boolean validEmail(String email, String inputCode);
    public String reissue(String refresh);
}
