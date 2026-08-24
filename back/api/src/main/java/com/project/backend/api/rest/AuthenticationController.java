package com.project.backend.api.rest;

import com.project.backend.api.dto.request.SignInRequest;
import com.project.backend.api.dto.request.RefreshSessionRequest;
import com.project.backend.api.dto.response.AuthenticatedSessionResponse;
import com.project.backend.api.mapper.ApiMapper;
import com.project.backend.application.command.SignInCommand;
import com.project.backend.application.command.RefreshSessionCommand;
import com.project.backend.application.command.RevokeSessionsCommand;
import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.domain.valueobject.EmailAddress;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;

/** The sole business endpoint intentionally available without an access token. */
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication")
public class AuthenticationController {
    private final CommandHandler<SignInCommand, AuthenticatedSession> signIn;
    private final CommandHandler<RefreshSessionCommand, AuthenticatedSession> refreshSession;
    private final CommandHandler<RevokeSessionsCommand, Void> revokeSessions;
    private final CurrentAccountResolver currentAccount;

    public AuthenticationController(
            CommandHandler<SignInCommand, AuthenticatedSession> signIn,
            CommandHandler<RefreshSessionCommand, AuthenticatedSession> refreshSession,
            CommandHandler<RevokeSessionsCommand, Void> revokeSessions,
            CurrentAccountResolver currentAccount) {
        this.signIn = signIn;
        this.refreshSession = refreshSession;
        this.revokeSessions = revokeSessions;
        this.currentAccount = currentAccount;
    }

    @PostMapping(path = "/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an authenticated session")
    @ApiResponse(responseCode = "200", description = "Session tokens issued")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public AuthenticatedSessionResponse signIn(@Valid @RequestBody SignInRequest request, HttpServletRequest httpRequest) {
        return ApiMapper.response(signIn.execute(new SignInCommand(
                new EmailAddress(request.email()), request.password().toCharArray(), httpRequest.getRemoteAddr())));
    }

    @PostMapping(path = "/sessions/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rotate a refresh token")
    public AuthenticatedSessionResponse refresh(@Valid @RequestBody RefreshSessionRequest request) {
        return ApiMapper.response(refreshSession.execute(new RefreshSessionCommand(request.refreshToken())));
    }

    @DeleteMapping(path = "/sessions")
    @Operation(summary = "Revoke all active sessions for the authenticated account")
    public org.springframework.http.ResponseEntity<Void> revokeAll(Principal principal) {
        revokeSessions.execute(new RevokeSessionsCommand(currentAccount.requireAccountId(principal)));
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}
