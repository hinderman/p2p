package com.project.backend.api.rest;

import com.project.backend.api.dto.request.RegisterLenderRequest;
import com.project.backend.api.dto.request.ResendAccountVerificationRequest;
import com.project.backend.api.dto.request.VerifyAccountEmailRequest;
import com.project.backend.api.dto.response.AuthenticatedSessionResponse;
import com.project.backend.api.mapper.ApiMapper;
import com.project.backend.application.command.RegisterLenderCommand;
import com.project.backend.application.command.ResendAccountVerificationCommand;
import com.project.backend.application.command.VerifyAccountEmailCommand;
import com.project.backend.application.port.in.ApplicationMediator;
import com.project.backend.domain.valueobject.EmailAddress;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public sign-up surface for lenders.
 *
 * <p>Registration and resend answer {@code 202} whatever the address turns out
 * to be, so neither can be used to discover who holds an account. Only the
 * verification endpoint returns a session, and only in exchange for a token that
 * was delivered to the mailbox.
 */
@RestController
@RequestMapping(path = "/api/v1/registration", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Registration")
public class RegistrationController {
    private final ApplicationMediator mediator;

    public RegistrationController(ApplicationMediator mediator) {
        this.mediator = mediator;
    }

    @PostMapping(path = "/lender", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Open a lender account",
            description = "Always accepted. If the address can hold a new account a verification link is sent; "
                    + "if it is already registered its owner is told instead. The response never differs.")
    @ApiResponse(responseCode = "202", description = "Registration accepted")
    @ApiResponse(responseCode = "422", description = "The password does not satisfy the password policy")
    @ApiResponse(responseCode = "429", description = "Too many attempts for this address or origin")
    public ResponseEntity<Void> registerLender(
            @Valid @RequestBody RegisterLenderRequest request, HttpServletRequest httpRequest) {
        mediator.send(new RegisterLenderCommand(
                new EmailAddress(request.email()), request.password().toCharArray(),
                request.firstName(), request.lastName(), httpRequest.getRemoteAddr()));
        return ResponseEntity.accepted().build();
    }

    @PostMapping(path = "/verification", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirm the address and activate the account")
    @ApiResponse(responseCode = "200", description = "Account activated and signed in")
    @ApiResponse(responseCode = "400", description = "The link is invalid, expired, or already used")
    public AuthenticatedSessionResponse verifyEmail(@Valid @RequestBody VerifyAccountEmailRequest request) {
        return ApiMapper.response(mediator.send(new VerifyAccountEmailCommand(request.verificationToken())));
    }

    @PostMapping(path = "/verification/resend", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Send a replacement verification link",
            description = "Supersedes any link still pending for the address. Always accepted.")
    @ApiResponse(responseCode = "202", description = "Request accepted")
    @ApiResponse(responseCode = "429", description = "Too many attempts for this address or origin")
    public ResponseEntity<Void> resendVerification(
            @Valid @RequestBody ResendAccountVerificationRequest request, HttpServletRequest httpRequest) {
        mediator.send(new ResendAccountVerificationCommand(
                new EmailAddress(request.email()), httpRequest.getRemoteAddr()));
        return ResponseEntity.accepted().build();
    }
}
