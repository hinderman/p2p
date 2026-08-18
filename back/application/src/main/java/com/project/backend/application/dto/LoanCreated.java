package com.project.backend.application.dto;

import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.valueobject.LoanId;

public record LoanCreated(LoanId loanId, LoanStatus status) {
}
