import { beforeEach, describe, expect, test } from 'vitest';

import { completePaymentAttempt, resolvePaymentAttempt } from './paymentIdempotency';
import type { ReportPaymentInput } from './payment.types';

const input: ReportPaymentInput = {
  paymentType: 'INSTALLMENT',
  reportedAmount: { amount: '100.0000', currency: 'COP' },
  reportedPaymentDate: '2026-08-24',
  externalReference: 'TRANSFER-1',
  proofs: [{
    storedObjectId: 'd98d8980-5be6-4439-ab0d-a12da2a9da5b',
    sha256: 'a'.repeat(64),
  }],
};

beforeEach(() => window.sessionStorage.clear());

describe('payment idempotency attempts', () => {
  test('reuses the same UUID for an identical retry', () => {
    const first = resolvePaymentAttempt('account-1', 'loan-1', input);
    const retry = resolvePaymentAttempt('account-1', 'loan-1', structuredClone(input));

    expect(first.idempotencyKey).toMatch(/^[0-9a-f-]{36}$/i);
    expect(retry.idempotencyKey).toBe(first.idempotencyKey);
  });

  test('generates a different key when the financial submission changes', () => {
    const first = resolvePaymentAttempt('account-1', 'loan-1', input);
    const changed = resolvePaymentAttempt('account-1', 'loan-1', {
      ...input,
      reportedAmount: { ...input.reportedAmount, amount: '101.0000' },
    });

    expect(changed.idempotencyKey).not.toBe(first.idempotencyKey);
  });

  test('reuses the key for representations the backend considers equivalent', () => {
    const first = resolvePaymentAttempt('account-1', 'loan-1', input);
    const equivalent = resolvePaymentAttempt('account-1', 'loan-1', {
      ...input,
      reportedAmount: { amount: '100', currency: 'cop' },
      externalReference: ' TRANSFER-1 ',
      proofs: [...input.proofs].reverse(),
    });

    expect(equivalent.idempotencyKey).toBe(first.idempotencyKey);
  });

  test('isolates attempts by account and loan and clears only the completed key', () => {
    const first = resolvePaymentAttempt('account-1', 'loan-1', input);
    const otherAccount = resolvePaymentAttempt('account-2', 'loan-1', input);
    const otherLoan = resolvePaymentAttempt('account-1', 'loan-2', input);
    expect(new Set([first.idempotencyKey, otherAccount.idempotencyKey, otherLoan.idempotencyKey]).size).toBe(3);

    completePaymentAttempt('account-1', 'loan-1', 'another-key');
    expect(resolvePaymentAttempt('account-1', 'loan-1', input).idempotencyKey).toBe(first.idempotencyKey);
    completePaymentAttempt('account-1', 'loan-1', first.idempotencyKey);
    expect(resolvePaymentAttempt('account-1', 'loan-1', input).idempotencyKey).not.toBe(first.idempotencyKey);
  });
});
