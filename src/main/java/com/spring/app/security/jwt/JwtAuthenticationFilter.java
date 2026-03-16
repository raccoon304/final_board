package com.spring.app.security.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   RestTemplate restTemplate) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.restTemplate = restTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
    	// 1. 헤더에서 먼저 시도
    	String token = resolveToken(request.getHeader("Authorization"));

    	// 2. 헤더에 없으면 쿠키에서 시도
    	if (token == null && request.getCookies() != null) {
    	    for (Cookie cookie : request.getCookies()) {
    	        if ("accessToken".equals(cookie.getName())) {
    	            token = cookie.getValue();
    	            break;
    	        }
    	    }
    	}
    	
    	//  3. 쿠키에도 없으면 URL 파라미터에서 시도
    	if (token == null) {
    	    String paramToken = request.getParameter("token");
    	    if (paramToken != null && !paramToken.isBlank()) {
    	        token = paramToken;

    	        // 쿠키에 심어서 이후 요청(페이지 내 API 등)에도 인증 유지
    	        Cookie tokenCookie = new Cookie("accessToken", token);
    	        tokenCookie.setPath("/");
    	        tokenCookie.setMaxAge(60 * 30); // 30분
    	        response.addCookie(tokenCookie);
    	    }
    	}

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
    	            org.springframework.http.HttpHeaders headers =
    	                new org.springframework.http.HttpHeaders();
    	            headers.add("Cookie", "refreshToken=" + refreshToken);

    	            org.springframework.http.HttpEntity<?> entity =
    	                new org.springframework.http.HttpEntity<>(headers);

    	            org.springframework.http.ResponseEntity<java.util.Map> result =
    	                restTemplate.exchange(
    	                    "http://localhost:8001/auth/reissue", // ✅ 수정
    	                    org.springframework.http.HttpMethod.POST,
    	                    entity,
    	                    java.util.Map.class
    	                );

    	            if (result.getStatusCode().is2xxSuccessful() && result.getBody() != null) {
    	                String newToken = (String) result.getBody().get("accessToken");
    	                token = newToken;

    	                Cookie newTokenCookie = new Cookie("accessToken", newToken);
    	                newTokenCookie.setPath("/");
    	                newTokenCookie.setMaxAge(60 * 30);
    	                response.addCookie(newTokenCookie);

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

    	    // SecurityContext 세션에 저장
    	    SecurityContext context = SecurityContextHolder.createEmptyContext();
    	    context.setAuthentication(authentication);
    	    SecurityContextHolder.setContext(context);

    	    HttpSession session = request.getSession(true);
    	    session.setAttribute(
    	        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
    	        context
    	    );
    	}
        
        filterChain.doFilter(request, response);
    }

    private String resolveToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) return null;
        if (!bearerToken.startsWith("Bearer ")) return null;
        return bearerToken.substring(7);
    }
}