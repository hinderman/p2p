package com.project.backend.api.dto.response;

import java.math.BigDecimal;

public record MoneyResponse(BigDecimal amount, String currency) {
}
