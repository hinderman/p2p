package com.project.backend.api.config;

import com.project.backend.api.security.BearerAuthenticationFilter;
import com.project.backend.api.security.ProblemAccessDeniedHandler;
import com.project.backend.api.security.ProblemAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/** HTTP authorization policy. Ownership is additionally enforced by application use cases. */
@Configuration
@EnableWebSecurity
public class ApiSecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            BearerAuthenticationFilter bearerAuthenticationFilter,
            ProblemAuthenticationEntryPoint authenticationEntryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/sessions", "/api/v1/auth/sessions/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/onboarding/payer").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/registration/lender",
                                "/api/v1/registration/verification", "/api/v1/registration/verification/resend").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/sessions").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/loans").hasRole("LENDER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/loans/lender").hasRole("LENDER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/loans/payer").hasRole("PAYER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/loans/*").hasAnyRole("LENDER", "PAYER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/loans/*/payments/pending").hasRole("LENDER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/loans/*/payments").hasRole("PAYER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payment-proofs").hasRole("PAYER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/*/approval", "/api/v1/payments/*/rejection", "/api/v1/payments/*/reversal").hasRole("LENDER")
                        .anyRequest().denyAll())
                .addFilterBefore(bearerAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .build();
    }
}
