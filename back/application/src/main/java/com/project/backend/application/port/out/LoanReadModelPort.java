package com.project.backend.application.port.out;

import com.project.backend.application.dto.LoanSummary;
import com.project.backend.application.dto.LoanDetail;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.PersonId;

import java.util.List;
import java.util.Optional;

/** Lado de lectura CQRS: puede usar proyecciones sin rehydrate aggregates. */
public interface LoanReadModelPort {
    List<LoanSummary> findByLender(PersonId lenderId);
    List<LoanSummary> findByPayer(PersonId payerId);
    Optional<LoanDetail> findDetail(LoanId loanId, PersonId participantId);
}
