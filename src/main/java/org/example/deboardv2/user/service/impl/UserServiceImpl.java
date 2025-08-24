package org.example.deboardv2.user.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.dto.UpdateRequest;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.example.deboardv2.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;


//import static org.example.deboardv2.system.exception.ExceptionHandler.USER_NOT_FOUND_ERROR;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElseThrow(
                ()-> new CustomException(ErrorCode.USER_NOT_FOUND)
        );
    }

    @Override
    public User getUserById(String email) {
        return userRepository.findByEmail(email).orElseThrow(
                ()-> new CustomException(ErrorCode.USER_NOT_FOUND)
        );
    }

    @Override
    public User getUserReferenceById(Long userId) {
        return userRepository.getReferenceById(userId);
    }


    @Override
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long memberId = (Long) authentication.getPrincipal();

        return userRepository.findById(memberId).orElseThrow(
                ()-> new CustomException(ErrorCode.USER_NOT_FOUND)
        );
    }

    @Override
    public User create(SignupRequest signupRequest) {
        return userRepository.save(User.toEntity(signupRequest));
    }

    @Override
    public User create(User user) {
        return userRepository.save(user);
    }

    @Override
    public boolean checkEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean checkNickname(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.NICKNAME_DUPLICATED);
        }
        return false;
    }

    @Override
    @Transactional
    public void update(Long userId, UpdateRequest dto) {
        User referenceById = userRepository.getReferenceById(userId);
        referenceById.update(dto);;
    }

    @Override
    @Transactional
    public void delete(Long userId) {
        userRepository.deleteById(userId);
    }

    @Override
    public User getUserByNickname(String author) {
        return userRepository.findByNickname(author).orElseThrow(
                ()-> new CustomException(ErrorCode.USER_NOT_FOUND)
        );
    }
}
