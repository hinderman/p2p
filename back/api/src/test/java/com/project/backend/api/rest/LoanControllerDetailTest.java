package com.project.backend.api.rest;

import com.project.backend.application.dto.LoanDetail;
import com.project.backend.application.dto.LoanTermsDetail;
import com.project.backend.application.port.in.ApplicationMediator;
import com.project.backend.application.query.GetLoanDetailQuery;
import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.loan.RatePeriod;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PersonId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanControllerDetailTest {
    @Test
    void exposes_the_contractual_detail_and_forwards_the_authenticated_account() throws Exception {
        UUID loanId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ApplicationMediator mediator = mock(ApplicationMediator.class);
        Money principal = new Money(new BigDecimal("1000.0000"), "COP");
        when(mediator.query(any(GetLoanDetailQuery.class))).thenReturn(new LoanDetail(new LoanId(loanId),
                new PersonId(UUID.randomUUID()), LoanStatus.ACTIVE, principal, principal,
                Instant.parse("2026-08-31T12:00:00Z"), new LoanTermsDetail(1, new BigDecimal("2.12345678"),
                RatePeriod.MONTHLY_EFFECTIVE, InterestCalculationMethod.SIMPLE, DayCountBasis.THIRTY_360,
                AmortizationMethod.FIXED_PAYMENT, CapitalPrepaymentPolicy.REDUCE_PAYMENT, 1,
                LocalDate.of(2026, 9, 30), "America/Bogota"), null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new LoanController(new CurrentAccountResolver(), mediator)).build();
        Principal principalIdentity = accountId::toString;

        mvc.perform(get("/api/v1/loans/{loanId}", loanId).principal(principalIdentity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanId").value(loanId.toString()))
                .andExpect(jsonPath("$.terms.interestRatePercentage").value("2.12345678"))
                .andExpect(jsonPath("$.paymentPlan").doesNotExist());

        ArgumentCaptor<GetLoanDetailQuery> query = ArgumentCaptor.forClass(GetLoanDetailQuery.class);
        verify(mediator).query(query.capture());
        assertEquals(accountId, query.getValue().accountId().value());
        assertEquals(loanId, query.getValue().loanId().value());
    }
}
