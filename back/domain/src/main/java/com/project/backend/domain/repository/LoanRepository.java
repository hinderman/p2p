package com.project.backend.domain.repository;

import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.LoanId;

import java.util.List;

/** Port for the Loan aggregate. Its implementation belongs to infrastructure. */
public interface LoanRepository extends Repository<Loan, LoanId> {

    List<Loan> findActiveByLender(PersonId lenderId);

    List<Loan> findActiveByPayer(PersonId payerId);
}
