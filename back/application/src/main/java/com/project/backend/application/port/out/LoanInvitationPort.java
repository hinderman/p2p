package com.project.backend.application.port.out;

import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.LoanId;

public interface LoanInvitationPort {
    /** Records the invitation and its asynchronous delivery; it does not send email inline. */
    void scheduleInvitation(LoanId loanId, LoanTermId loanTermId, EmailAddress recipient);
}
