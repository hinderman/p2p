import { expect, test } from 'vitest';

import type { PaymentPage, PendingPayment } from './payment.types';
import { correctedPage, emptyPaymentPage, visibleRange } from './paymentPaging';

function payment(index: number): PendingPayment {
  return {
    reportedPaymentId: `0000000${index}-0000-4000-8000-00000000000${index}`,
    loanId: 'b0ebfe24-4094-452a-b6ec-40a2e368232e',
    status: 'PENDING_REVIEW',
    reportedAmount: { amount: '100.0000', currency: 'COP' },
    validatedAmount: null,
    reportedPaymentDate: '2026-08-25',
  };
}

function page(values: Partial<PaymentPage>): PaymentPage {
  return { ...emptyPaymentPage(5), ...values };
}

test('accepts a page the server filled', () => {
  expect(correctedPage(page({ content: [payment(1)], page: 1, totalElements: 6, totalPages: 2 }))).toBeNull();
});

test('steps back when the current page was emptied by an approval', () => {
  expect(correctedPage(page({ content: [], page: 2, totalElements: 6, totalPages: 2 }))).toBe(1);
});

test('returns to the first page when every payment was processed', () => {
  expect(correctedPage(page({ content: [], page: 3, totalElements: 0, totalPages: 0 }))).toBe(0);
});

test('never corrects the first page, so the empty state can render', () => {
  expect(correctedPage(page({ content: [], page: 0, totalElements: 0, totalPages: 0 }))).toBeNull();
});

test('reports the absolute position of the visible elements', () => {
  expect(visibleRange(page({ content: [payment(1), payment(2)], page: 2, size: 5, totalElements: 12 })))
    .toEqual({ first: 11, last: 12 });
  expect(visibleRange(page({ content: [], page: 0, totalElements: 0 }))).toEqual({ first: 0, last: 0 });
});
