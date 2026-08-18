package com.project.backend.application.dto;

import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.valueobject.LoanId;

public record LoanAccepted(LoanId loanId, LoanStatus status) {
}
