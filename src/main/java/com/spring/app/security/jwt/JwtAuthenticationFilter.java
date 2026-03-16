package com.spring.app.security.jwt;

import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. 헤더에서 시도
        String token = resolveToken(request.getHeader("Authorization"));      
        
        // 4. accessToken 없거나 만료 시 refreshToken으로 재발급
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            String refreshToken = null;
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("refreshToken".equals(cookie.getName())) {
                        refreshToken = cookie.getValue();
                        break;
                    }
                }
            }

            if (refreshToken != null) {
                try {
                    org.springframework.web.client.RestTemplate rt =
                        new org.springframework.web.client.RestTemplate();

                    org.springframework.http.HttpHeaders headers =
                        new org.springframework.http.HttpHeaders();
                    headers.add("Cookie", "refreshToken=" + refreshToken);

                    org.springframework.http.HttpEntity<?> entity =
                        new org.springframework.http.HttpEntity<>(headers);

                    org.springframework.http.ResponseEntity<java.util.Map> result =
                        rt.exchange(
                        		"http://user-service/auth/reissue",
                            org.springframework.http.HttpMethod.POST,
                            entity,
                            java.util.Map.class
                        );

                    if (result.getStatusCode().is2xxSuccessful() && result.getBody() != null) {
                        String newToken = (String) result.getBody().get("accessToken");
                        token = newToken;
                        
                     //  추가
                        Cookie newTokenCookie = new Cookie("newAccessToken", newToken);
                        newTokenCookie.setPath("/");
                        newTokenCookie.setMaxAge(10);
                        response.addCookie(newTokenCookie);

                        System.out.println("===== 8002 accessToken 자동 재발급 성공");
                        
                        response.setHeader("Authorization", "Bearer " + newToken);
                        response.setHeader("Access-Control-Expose-Headers", "Authorization"); 

                     
                        System.out.println("===== 8002 accessToken 자동 재발급 성공");
                    }
                } catch (Exception e) {
                    System.out.println("===== 8002 토큰 재발급 실패: " + e.getMessage());
                }
            }
        }

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String memberId = jwtTokenProvider.getMemberId(token);
            List<String> roles = jwtTokenProvider.getRoles(token);
            var authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(memberId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) return null;
        if (!bearerToken.startsWith("Bearer ")) return null;
        return bearerToken.substring(7);
    }
}