package org.example.deboardv2.user.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.deboardv2.user.service.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final OAuth2SuccessHandler successHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CustomOAuth2UserService customOAuth2UserService) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                // 여기서 userInfoEndpoint로 customoauthservice를 지정하는 이유는 기본 설정된 loadUser말고 내가 직접 커스텀해서 사용하는
                // loadUser를 이용하여 만들기 위함
                .oauth2Login(oauth -> oauth
                        .successHandler(successHandler)
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService)))
                .authorizeHttpRequests((auth) -> {
                    auth
                            .requestMatchers(
                                    "/api/auth/**",
                                    "/swagger-ui.html",
                                    "/swagger-ui/**",
                                    "/v3/api-docs/**",
                                    "/swagger-resources/**"
                            ).permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
//                        .requestMatchers("/admin").hasRole("ADMIN")
//                        .requestMatchers("/my/**").hasAnyRole("ADMIN", "USER")
                            .anyRequest().permitAll();
                })
                .exceptionHandling(
                        exception -> exception.authenticationEntryPoint(
                                (req, resp, ex) -> {
                                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                    resp.setContentType("application/json");
                                    resp.getWriter().write("{\"error\": \"Unauthorized\"}");
                                }
                        )
                )
                // 필터의 순서를 보장해 주기위해 등록
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
