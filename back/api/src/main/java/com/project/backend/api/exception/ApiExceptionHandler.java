package com.project.backend.api.exception;

import com.project.backend.application.exception.AccessDeniedException;
import com.project.backend.application.exception.AuthenticationFailedException;
import com.project.backend.application.exception.InvitationInvalidException;
import com.project.backend.application.exception.InvalidPaymentProofException;
import com.project.backend.application.exception.MalwareDetectedException;
import com.project.backend.application.exception.FileScanUnavailableException;
import com.project.backend.application.exception.IdempotencyConflictException;
import com.project.backend.application.exception.OperationNotAllowedException;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.application.exception.RateLimitExceededException;
import com.project.backend.application.exception.VerificationInvalidException;
import com.project.backend.domain.exception.DomainRuleViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps domain and application failures to a stable RFC 9457 problem contract. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ProblemDetail> handleInvalidCredentials(AuthenticationFailedException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "authentication_failed", "Authentication failed", exception.getMessage());
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    ResponseEntity<ProblemDetail> handleAuthenticationRequired(AuthenticationRequiredException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "authentication_required", "Authentication required", exception.getMessage());
    }

    @ExceptionHandler(InvitationInvalidException.class)
    ResponseEntity<ProblemDetail> handleInvalidInvitation(InvitationInvalidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "invitation_invalid", "Invitation invalid", exception.getMessage());
    }

    @ExceptionHandler(VerificationInvalidException.class)
    ResponseEntity<ProblemDetail> handleInvalidVerification(VerificationInvalidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "verification_invalid", "Verification invalid", exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "access_denied", "Access denied", exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "resource_not_found", "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ProblemDetail> handleIdempotencyConflict(IdempotencyConflictException exception) {
        return problem(HttpStatus.CONFLICT, "idempotency_conflict", "Idempotency conflict", exception.getMessage());
    }

    @ExceptionHandler({OperationNotAllowedException.class, DomainRuleViolation.class})
    ResponseEntity<ProblemDetail> handleBusinessRuleViolation(RuntimeException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "business_rule_violation", "Business rule violation", exception.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException exception) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded", "Too many requests", exception.getMessage());
    }

    @ExceptionHandler(InvalidPaymentProofException.class)
    ResponseEntity<ProblemDetail> handleInvalidPaymentProof(InvalidPaymentProofException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_payment_proof", "Invalid payment proof", exception.getMessage());
    }

    @ExceptionHandler(MalwareDetectedException.class)
    ResponseEntity<ProblemDetail> handleMalwareDetected(MalwareDetectedException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "malware_detected", "Unsafe file", exception.getMessage());
    }

    @ExceptionHandler(FileScanUnavailableException.class)
    ResponseEntity<ProblemDetail> handleFileScanUnavailable(FileScanUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "file_scan_unavailable", "File scan unavailable", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleInvalidRequest(MethodArgumentNotValidException exception) {
        Map<String, String> violations = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            violations.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail detail = createProblem(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed",
                "One or more request fields are invalid");
        detail.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(detail);
    }

    /**
     * A path variable or query parameter that cannot be converted to its declared
     * type is a client defect, not a server failure: without this it would reach
     * the catch-all handler and be reported as a 500, which misleads the caller
     * into retrying and pollutes error monitoring.
     */
    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class, IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> handleMalformedRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "malformed_request", "Malformed request",
                "The request body, path, or query parameters are invalid");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoRoute(NoResourceFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "route_not_found", "Route not found", "The requested route does not exist");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "payment_proof_too_large", "Payment proof too large",
                "The payment proof must not exceed 15 MB");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpectedFailure(Exception exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal server error",
                "An unexpected error occurred");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String title, String detail) {
        return ResponseEntity.status(status).body(createProblem(status, code, title, detail));
    }

    private static ProblemDetail createProblem(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
