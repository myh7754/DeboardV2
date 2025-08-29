package org.example.deboardv2.user.service;

import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.dto.UpdateRequest;
import org.example.deboardv2.user.entity.User;

public interface UserService {
    public User getUserById(Long userId);
    public User getUserById(String email);
    public User getUserReferenceById(Long userId);
    public Long getCurrentUserId();
    public String getCurrentUserNickname();
    public User getCurrentUser();
    public User create(SignupRequest signupRequest);
    public User create(User user);
    public boolean checkEmail(String email);
    public boolean checkNickname(String nickname);
    public void update(Long userId, UpdateRequest dto);
    public void delete(Long userId);

    public User getUserByNickname(String author);
}
