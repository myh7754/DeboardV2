package org.example.deboardv2.user.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.comment.repository.CommentsRepository;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.redis.RedisKeyConstants;
import org.example.deboardv2.redis.service.RedisService;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.config.JwtConfig;
import org.example.deboardv2.user.dto.*;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.example.deboardv2.user.service.AuthService;
import org.example.deboardv2.user.service.JwtTokenProvider;
import org.example.deboardv2.user.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;



@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final UserService userService;
    private final UserRepository userRepository;
    private final RedisService redisService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;
    private final PostRepository postRepository;
    private final CommentsRepository commentsRepository;
    @Override
    @Transactional
    public User signUp(SignupRequest signupRequest) {
        String email  = signupRequest.getEmail();
        try {
            // 이메일 미인증시 이메일 인증 요구
            if (!redisService.checkAndDelete(RedisKeyConstants.EMAIL_AUTH + "certified:" + email)) {
                throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
            }
            signupRequest.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
            return userService.create(signupRequest);
        } catch(DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.NICKNAME_DUPLICATED);
        }

    }

    //로그인
    @Override
    public LoginResponse signIn(SignInRequest signInRequest) {
        // 이메일로 사용자 찾기 (Optional 사용)
        User readUser = userRepository.findByEmail(signInRequest.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_MISMATCH));

        if (!passwordEncoder.matches(signInRequest.getPassword(), readUser.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }
        TokenBody tokenBody = new TokenBody(readUser.getId(),readUser.getNickname() ,readUser.getRole());
        // access token
        String access = jwtTokenProvider.issue(tokenBody, jwtConfig.getValidation().getAccess());
        String refresh = jwtTokenProvider.issue(tokenBody, jwtConfig.getValidation().getRefresh());
        return new LoginResponse(new JwtToken(access, refresh),UserDto.from(readUser));
    }

    @Override
    public void logout(String refresh) {
        // redis에 refresh 저장 (블랙리스트)
        if (refresh == null || refresh.isEmpty()) {
            // 토큰이 없으면 바로 리턴
            return;
        }
        try {
            TokenBody tokenBody = jwtTokenProvider.parseJwt(refresh);
            redisService.setValueWithExpire(RedisKeyConstants.REFRESH_TOKEN + tokenBody.getMemberId(), refresh, jwtConfig.getValidation().getRefresh());
        } catch (Exception e) {
            // 토큰 파싱 실패 시 (만료된 토큰 등) 로그아웃 처리 계속 진행
            log.debug("로그아웃 시 토큰 파싱 실패 (만료된 토큰일 수 있음): {}", e.getMessage());
        }
    }

    // 중복검사
    private boolean duplicateEmail(String email) {
        return userService.checkEmail(email);
    }

    // 중복검사 및 메일인증
    @Override
    public String sendEmailAuthCode(String email) {
        String code = generateRandomCode();
        if (duplicateEmail(email)) {
            throw new CustomException(ErrorCode.EMAIL_DUPLICATED);
        }
        redisService.setValueWithExpire(RedisKeyConstants.EMAIL_AUTH + email, code, Duration.ofMinutes(3));
        mailService.sendSimpleMailMessage(email, code);
        return email;
    }


    // 인증번호 검사
    @Override
    public Boolean validEmail(String email, String inputCode) {
        String redisKey = RedisKeyConstants.EMAIL_AUTH + email;
        Object value = redisService.getValue(redisKey);
        if (value == null || !value.equals(inputCode)) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_ERROR);
        }
        redisService.setValue(RedisKeyConstants.EMAIL_AUTH + "certified:" + email, true);
        redisService.deleteValue(redisKey);
        return true;
    }

    @Override
    public String reissue(String refresh) {
        boolean b = jwtTokenProvider.validateToken(refresh);
        if (b) {
            TokenBody tokenBody = jwtTokenProvider.parseJwt(refresh);
            // 만약 이게 있다면 블랙리스트에 등록된거
            Object blackList = redisService.getValue(RedisKeyConstants.REFRESH_TOKEN + tokenBody.getMemberId());
            if  (blackList != null && blackList.equals(refresh)) {
                throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
            }
            // 없다면 여기서 등록되지 않은 refreshToken임
            // 여기까지 왔다면 올바른 인증을 한거임 그럼 accessToken발급

            return jwtTokenProvider.issue(tokenBody, jwtConfig.getValidation().getAccess());
        } else {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    @Override
    public void authCheck(Long id,String entityType) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        TokenBody tokenBody = (TokenBody) authentication.getPrincipal();
        Long memberId = tokenBody.getMemberId();
        boolean authorized = switch (entityType) {
            case "POST" -> postRepository.existsByIdAndAuthorId(id, memberId);
            case "COMMENT" -> commentsRepository.existsByCommentsIdAndAuthorId(id, memberId);
            default -> false;
        };
        if (!authorized) {
            // 권한 없으면 FORBIDDEN 예외 던지기
            throw new CustomException(ErrorCode.FORBIDDEN);
        }


    }
    // 6자리 랜덤숫자 생성
    private String generateRandomCode() {
        return String.valueOf(SECURE_RANDOM.nextInt(900000) + 100000);
    }

}
