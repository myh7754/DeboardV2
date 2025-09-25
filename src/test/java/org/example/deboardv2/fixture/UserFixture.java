package org.example.deboardv2.fixture;

import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.entity.User;

import java.util.ArrayList;
import java.util.List;

public class UserFixture {
    // 단일 유저 생성
    public static User createUser(int index) {
        SignupRequest request = new SignupRequest();
        request.setNickname("user" + index);
        request.setEmail("user" + index + "@example.com");
        request.setPassword("password" + index);
        request.setNumber("010-0000-" + String.format("%04d", index));
        return User.toEntity(request);
    }

    // 100명의 유저 리스트 생성
    public static List<User> create100Users() {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            users.add(createUser(i));
        }
        return users;
    }
}
