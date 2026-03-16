package com.spring.app.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy; // ✅ 추가
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.spring.app.member.mapper.MemberMapper;
import com.spring.app.security.filter.DormantAccountFilter;
import com.spring.app.security.jwt.JwtAuthenticationFilter;
import com.spring.app.security.provider.LoginTypeAuthenticationProvider;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;  // ✅ 추가 (HttpSession 제거)

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final MemberMapper memberMapper;
    private final DormantAccountFilter dormantAccountFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(MemberMapper memberMapper, DormantAccountFilter dormantAccountFilter, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.memberMapper = memberMapper;
        this.dormantAccountFilter = dormantAccountFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) ->
                response.sendRedirect(request.getContextPath() + "/security/noAuthenticated");
    }

    @Bean
    AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                response.sendRedirect(request.getContextPath() + "/security/noAuthorized");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            LoginTypeAuthenticationProvider loginTypeAuthenticationProvider
    ) throws Exception {

        http.csrf(csrf -> csrf.disable());
        http.authenticationProvider(loginTypeAuthenticationProvider);

        // ✅ STATELESS 추가
        http.sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        String[] excludeUri = {
            "/",
            "/index",
            "/member/login",
            "/member/registerMember",
            "/member/registerCompanyMember",
            "/member/findAccount/**",
            "/security/noAuthenticated",
            "/security/noAuthorized",
            "/security/loginEnd",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/opendata/**",
            "/upload/**",
            "/photoupload/**",
            "/emailattachfile/**",
            "/images/**",
            "/job/**",
            "/api/job/**",
            "/companyinfo/**",
            "/auth/login",
            "/auth/reissue",
            "/auth/check",
            "/community",
            "/community/**"
        };

        http.authorizeHttpRequests(auth -> auth
            .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
            .requestMatchers(excludeUri).permitAll()
            .requestMatchers(
                    "/member/login",
                    "/member/registerMember",
                    "/member/registerMemberEnd",
                    "/member/registerCompanyMember",
                    "/member/registerCompanyMemberEnd",
                    "/member/registerSuccess",
                    "/member/findAccount",
                    "/member/dormant",
                    "/member/dormant/unlock",
                    "/member/password/reset",
                    "/member/password/send-code",
                    "/member/password/verify-code",
                    "/member/find/memberId",
                    "/member/find/memberPassword",
                    "/member/find/companyPassword",
                    "/member/find/companyId",
                    "/api/members/check-memberid",
                    "/api/members/check-email",
                    "/api/members/check-bizno"
            ).permitAll()
            .requestMatchers("/jobseeker/**").hasRole("MEMBER")
            .requestMatchers("/api/mypage/**").hasRole("MEMBER")
            .requestMatchers("/api/resume/**").hasRole("MEMBER")
            .requestMatchers("/api/companyinfo/**").hasRole("MEMBER")
            .requestMatchers("/api/offer/**").hasRole("MEMBER")
            .requestMatchers("/api/scrap/**").hasRole("MEMBER")
            .requestMatchers("/api/follow/**").hasRole("MEMBER")
            .requestMatchers("/api/recent/**").hasRole("MEMBER")
            .requestMatchers("/security/special/**").hasAnyRole("ADMIN", "USER_SPECIAL")
            .requestMatchers("/security/admin/**").hasRole("ADMIN")
            .requestMatchers("/emp/**").hasRole("ADMIN")
            .requestMatchers("/company/**").hasRole("COMPANY")
            .anyRequest().authenticated()
        );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(dormantAccountFilter, JwtAuthenticationFilter.class);

        http.logout(logout -> logout
            .logoutUrl("/security/logout")
            .addLogoutHandler((request, response, authentication) -> {
                // 세션 무효화 → refreshToken 쿠키 삭제로 변경
                Cookie refreshCookie = new Cookie("refreshToken", null);
                refreshCookie.setMaxAge(0);
                refreshCookie.setPath("/");
                response.addCookie(refreshCookie);
            })
            .logoutSuccessUrl("/index")
        );

        http.exceptionHandling(ex -> ex
            .authenticationEntryPoint(customAuthenticationEntryPoint())
            .accessDeniedHandler(customAccessDeniedHandler())
        );

        http.headers(headers -> headers
            .frameOptions(frame -> frame.sameOrigin())
        );

        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers(
                "/bootstrap-4.6.2-dist/**",
                "/css/**",
                "/fullcalendar_5.10.1/**",
                "/Highcharts-10.3.1/**",
                "/images/**",
                "/jquery-ui-1.13.1.custom/**",
                "/js/**",
                "/smarteditor/**",
                "/resources/photo_upload/**"
        );
    }
}