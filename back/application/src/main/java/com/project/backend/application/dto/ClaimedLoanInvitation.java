package com.project.backend.application.dto;

import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.LoanInvitationId;
import com.project.backend.domain.valueobject.LoanTermId;

/** Invitation metadata available only after its one-time token has been claimed. */
public record ClaimedLoanInvitation(
        LoanInvitationId invitationId,
        LoanId loanId,
        LoanTermId loanTermId,
        EmailAddress recipientEmail) {
}
