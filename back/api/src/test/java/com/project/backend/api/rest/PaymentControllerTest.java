package com.project.backend.api.rest;

import com.project.backend.api.exception.ApiExceptionHandler;
import com.project.backend.application.command.UploadPaymentProofCommand;
import com.project.backend.application.dto.StoredPaymentProof;
import com.project.backend.application.port.in.ApplicationMediator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentControllerTest {

    @Test
    void uploads_a_payment_proof_through_the_application_mediator() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        ApplicationMediator mediator = mock(ApplicationMediator.class);
        when(mediator.send(any(UploadPaymentProofCommand.class))).thenReturn(new StoredPaymentProof(
                objectId, "comprobante.pdf", "application/pdf", 10, "a".repeat(64)));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new PaymentController(new CurrentAccountResolver(), mediator))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        var file = new MockMultipartFile("file", "comprobante.pdf", "application/pdf",
                "%PDF-test".getBytes(StandardCharsets.US_ASCII));

        mockMvc.perform(multipart("/api/v1/payment-proofs").file(file).principal(() -> accountId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storedObjectId").value(objectId.toString()))
                .andExpect(jsonPath("$.sha256").value("a".repeat(64)))
                .andExpect(jsonPath("$.scanStatus").value("SAFE"));
        verify(mediator).send(any(UploadPaymentProofCommand.class));
    }
}
