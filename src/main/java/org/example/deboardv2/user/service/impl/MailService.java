package org.example.deboardv2.user.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;
    // Smtp의 메일 보내는 방식이 2가지 있음
    // 1. SimpleMailMessage를 통한 간단한 텍스트 전송 방법
    @Async("mailTaskExecutor")
    public void sendSimpleMailMessage(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        try {
            message.setTo(email); // 수신자 이메일
            message.setSubject("Deboard 가입 메일 인증입니다.");
            message.setText("인증번호는 다음과 같습니다 : " + code);
            message.setFrom("myh4755@gmail.com"); // 발신자 이메일
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 2. MimeMessage를 통한 html, 첨부파일, 수신자 복수 지정 등 복잡한 메일 전송 방법
    @Async("mailTaskExecutor")
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


}
