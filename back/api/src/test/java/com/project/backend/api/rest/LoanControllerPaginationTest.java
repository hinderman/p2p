package com.project.backend.api.rest;

import com.project.backend.api.exception.ApiExceptionHandler;
import com.project.backend.application.command.CreateLoanCommand;
import com.project.backend.application.dto.LoanCreated;
import com.project.backend.application.dto.LoanSummary;
import com.project.backend.application.dto.Page;
import com.project.backend.application.dto.PageRequest;
import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.in.query.QueryHandler;
import com.project.backend.application.query.ListLenderLoansQuery;
import com.project.backend.application.query.ListPayerLoansQuery;
import com.project.backend.application.query.ListPendingPaymentsQuery;
import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanControllerPaginationTest {
    private static final UUID LOAN_ID = UUID.fromString("b0ebfe24-4094-452a-b6ec-40a2e368232e");
    private static final Principal LENDER = () -> UUID.randomUUID().toString();

    @SuppressWarnings("unchecked")
    private final QueryHandler<ListPendingPaymentsQuery, Page<PaymentSummary>> pendingPayments = mock(QueryHandler.class);

    private MockMvc mockMvc() {
        @SuppressWarnings("unchecked")
        CommandHandler<CreateLoanCommand, LoanCreated> createLoan = mock(CommandHandler.class);
        @SuppressWarnings("unchecked")
        QueryHandler<ListLenderLoansQuery, List<LoanSummary>> lenderLoans = mock(QueryHandler.class);
        @SuppressWarnings("unchecked")
        QueryHandler<ListPayerLoansQuery, List<LoanSummary>> payerLoans = mock(QueryHandler.class);
        return MockMvcBuilders
                .standaloneSetup(new LoanController(new CurrentAccountResolver(), createLoan, lenderLoans, payerLoans, pendingPayments))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void forwards_the_requested_page_and_exposes_the_navigation_metadata() throws Exception {
        when(pendingPayments.execute(any())).thenReturn(Page.of(List.of(payment()), new PageRequest(1, 5), 7));

        mockMvc().perform(get("/api/v1/loans/{loanId}/payments/pending", LOAN_ID)
                        .param("page", "1").param("size", "5").principal(LENDER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));

        ArgumentCaptor<ListPendingPaymentsQuery> query = ArgumentCaptor.forClass(ListPendingPaymentsQuery.class);
        verify(pendingPayments).execute(query.capture());
        assertEquals(new PageRequest(1, 5), query.getValue().page());
    }

    @Test
    void applies_the_default_page_when_no_parameter_is_sent() throws Exception {
        when(pendingPayments.execute(any())).thenReturn(Page.empty(new PageRequest(0, PageRequest.DEFAULT_SIZE), 0));

        mockMvc().perform(get("/api/v1/loans/{loanId}/payments/pending", LOAN_ID).principal(LENDER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(PageRequest.DEFAULT_SIZE));

        ArgumentCaptor<ListPendingPaymentsQuery> query = ArgumentCaptor.forClass(ListPendingPaymentsQuery.class);
        verify(pendingPayments).execute(query.capture());
        assertEquals(new PageRequest(0, PageRequest.DEFAULT_SIZE), query.getValue().page());
    }

    /** A size above the cap would let one request materialize the whole table. */
    @Test
    void rejects_a_page_outside_the_supported_bounds_without_reaching_the_use_case() throws Exception {
        MockMvc mockMvc = mockMvc();
        for (String[] parameters : new String[][] {{"size", "101"}, {"size", "0"}, {"page", "-1"}}) {
            mockMvc.perform(get("/api/v1/loans/{loanId}/payments/pending", LOAN_ID)
                            .param(parameters[0], parameters[1]).principal(LENDER))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("malformed_request"));
        }
        verifyNoInteractions(pendingPayments);
    }

    /** A non-numeric parameter is a client defect: it must not surface as a 500. */
    @Test
    void reports_a_non_numeric_page_parameter_as_a_client_error() throws Exception {
        mockMvc().perform(get("/api/v1/loans/{loanId}/payments/pending", LOAN_ID)
                        .param("size", "abc").principal(LENDER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));

        verifyNoInteractions(pendingPayments);
    }

    private static PaymentSummary payment() {
        return new PaymentSummary(new ReportedPaymentId(UUID.randomUUID()), new LoanId(LOAN_ID),
                ReportedPaymentStatus.PENDING_REVIEW, new Money(new BigDecimal("100.0000"), "COP"), null,
                LocalDate.of(2026, 8, 25));
    }
}
