package com.project.backend.api.rest;

import com.project.backend.api.dto.request.PayerOnboardingRequest;
import com.project.backend.api.dto.response.AuthenticatedSessionResponse;
import com.project.backend.api.mapper.ApiMapper;
import com.project.backend.application.command.CompletePayerOnboardingCommand;
import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.port.in.command.CommandHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, single-use invitation redemption endpoint for the intended payer. */
@RestController
@RequestMapping(path = "/api/v1/onboarding", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Onboarding")
public class OnboardingController {
    private final CommandHandler<CompletePayerOnboardingCommand, AuthenticatedSession> completePayerOnboarding;

    public OnboardingController(CommandHandler<CompletePayerOnboardingCommand, AuthenticatedSession> completePayerOnboarding) {
        this.completePayerOnboarding = completePayerOnboarding;
    }

    @PostMapping(path = "/payer", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Redeem a payer invitation, activate the account, and accept the loan")
    public AuthenticatedSessionResponse completePayerOnboarding(
            @Valid @RequestBody PayerOnboardingRequest request, HttpServletRequest httpRequest) {
        return ApiMapper.response(completePayerOnboarding.execute(new CompletePayerOnboardingCommand(
                request.invitationToken(), request.password().toCharArray(), httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"))));
    }
}
