package com.project.backend.api.dto.response;

import com.project.backend.domain.loan.LoanStatus;

import java.util.UUID;

public record LoanResponse(UUID loanId, LoanStatus status) {
}
