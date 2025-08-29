package org.example.deboardv2.user.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.redis.service.RedisService;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.config.JwtConfig;
import org.example.deboardv2.user.dto.*;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.service.AuthService;
import org.example.deboardv2.user.service.JwtTokenProvider;
import org.example.deboardv2.user.service.UserService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.util.List;



@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final UserService userService;
    private final RedisService redisService;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private static final String EMAIL_PREFIX = "email:";
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;
    private final PostRepository postRepository;

    //회원가입
    @Override
    @Transactional
    public User signUp(SignupRequest signupRequest) {
        String email  = signupRequest.getEmail();
        // 이메일 미인증시 이메일 인증 요구
        if (!Boolean.TRUE.equals(redisService.getValue(EMAIL_PREFIX+"certified:"+email))){
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        redisService.deleteValue(EMAIL_PREFIX+"certified:"+email);
        signupRequest.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
        return userService.create(signupRequest);
    }

    //로그인
    @Override
    public LoginResponse signIn(SignInRequest signInRequest) {
        User readUser = userService.getUserById(signInRequest.getEmail());
        if (!readUser.getEmail().equals(signInRequest.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_MISMATCH);
        }
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
        TokenBody tokenBody = jwtTokenProvider.parseJwt(refresh);

        redisService.setValueWithExpire("refresh:"+tokenBody.getMemberId(), refresh, jwtConfig.getValidation().getRefresh());
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
        redisService.setValueWithExpire(EMAIL_PREFIX+email,code, Duration.ofMinutes(3));
        sendSimpleMailMessage(email, code);
        return email;
    }


    // 인증번호 검사
    @Override
    public Boolean validEmail(String email, String inputCode) {
        String redisKey = EMAIL_PREFIX + email;
        Object value = redisService.getValue(redisKey);
        if (value == null || !value.equals(inputCode)) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_ERROR);
        }
        redisService.setValue(EMAIL_PREFIX+"certified:"+email,true);
        redisService.deleteValue(redisKey);
        return true;
    }

    @Override
    public String reissue(String refresh) {
        boolean b = jwtTokenProvider.validateToken(refresh);
        if (b) {
            TokenBody tokenBody = jwtTokenProvider.parseJwt(refresh);
            // 만약 이게 있다면 블랙리스트에 등록된거
            Object blackList = redisService.getValue("refresh:" + tokenBody.getMemberId());
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
            default -> false;
        };
        if (!authorized) {
            // 권한 없으면 FORBIDDEN 예외 던지기
            throw new CustomException(ErrorCode.FORBIDDEN);
        }


    }
//
//    @Override
//    public Boolean authCheck(String nickname) {
//        String currentUserNickname = userService.getCurrentUserNickname();
//        return currentUserNickname.equals(nickname);
//    }

    // 6자리 랜덤숫자 생성
    private String generateRandomCode() {
        return String.valueOf((int)((Math.random() * 900000) + 100000)); // 6자리 숫자
    }

    // Smtp의 메일 보내는 방식이 2가지 있음
    // 1. SimpleMailMessage를 통한 간단한 텍스트 전송 방법
    private void sendSimpleMailMessage(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        try {
            message.setTo(email); // 수신자 이메일
            message.setSubject("Deboard 가입 메일 인증입니다.");
            message.setText("인증번호는 다음과 같습니다 : " + code);
            message.setFrom("myh4755@gmail.com"); // 발신자 이메일
            mailSender.send(message);
        } catch (Exception e) {
            log.info("메일 발송 실패");
            throw new RuntimeException(e);
        }
    }

    // 2. MimeMessage를 통한 html, 첨부파일, 수신자 복수 지정 등 복잡한 메일 전송 방법
    public void sendMimeMessage() throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        // 수신자 여러 명 (To)
        String[] toRecipients = {"to1@example.com", "to2@example.com"};

        // html 메시지 내용
        String html = """
        <h1>환영합니다!</h1>
        <p><b>회원가입</b>을 축하드립니다.</p>
        <a href="https://your-site.com">사이트로 이동</a>
    """;
        // 첨부파일
        List<File> files = List.of(
                new File("C:/Users/you/Desktop/test1.pdf"),
                new File("C:/Users/you/Desktop/test2.jpg")
        );
        helper.setTo(toRecipients);
        helper.setSubject("제목");
        helper.setText(html, true);  // true = HTML 모드
        helper.setFrom("your-email@gmail.com");
        // 여러 첨부파일
        for (File file : files) {
            helper.addAttachment(file.getName(), file);
        }
        mailSender.send(message);
    }



    private void validatePasswordRule(String password) {
//        if (password.length() < 8) {
//            throw new WeakPasswordException("비밀번호는 8자 이상이어야 합니다.");
//        }
        // 더 많은 규칙을 여기에 추가 가능 (영문+숫자+특수문자 등)
    }
}
