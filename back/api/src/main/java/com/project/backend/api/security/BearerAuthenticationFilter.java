package com.project.backend.api.security;

import com.project.backend.application.port.out.AccessTokenValidationPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Converts a verified bearer token into the principal consumed by REST adapters. */
@Component
public final class BearerAuthenticationFilter extends OncePerRequestFilter {
    private final AccessTokenValidationPort tokens;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public BearerAuthenticationFilter(AccessTokenValidationPort tokens, AuthenticationEntryPoint authenticationEntryPoint) {
        this.tokens = tokens;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith("Bearer ") || authorization.length() == "Bearer ".length()) {
            authenticationEntryPoint.commence(request, response, new org.springframework.security.core.AuthenticationException("Invalid bearer token") { });
            return;
        }
        var token = tokens.validate(authorization.substring("Bearer ".length())).orElse(null);
        if (token == null) {
            authenticationEntryPoint.commence(request, response, new org.springframework.security.core.AuthenticationException("Invalid bearer token") { });
            return;
        }
        var authorities = token.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(token.accountId().value().toString(), token, authorities));
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }
}
