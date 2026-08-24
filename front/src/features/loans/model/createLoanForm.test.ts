import { describe, expect, test } from 'vitest';

import {
  defaultCreateLoanForm,
  toCreateLoanInput,
  validateCreateLoanForm,
} from './createLoanForm';

function validForm() {
  return {
    ...defaultCreateLoanForm(new Date(2026, 7, 24)),
    payerEmail: ' payer@example.com ',
    principalAmount: '1000000.2500',
    interestRatePercentage: '2.50000000',
    firstDueDate: '2026-09-24',
    daysOfMonth: '15, 30, 15',
  };
}

describe('create loan form contract', () => {
  test('validates and maps a monthly schedule without converting financial decimals to numbers', () => {
    const form = validForm();
    expect(validateCreateLoanForm(form, '2026-08-24')).toEqual({});

    expect(toCreateLoanInput(form, 'America/Bogota')).toMatchObject({
      payerEmail: 'payer@example.com',
      originalPrincipal: { amount: '1000000.2500', currency: 'COP' },
      interestRatePercentage: '2.50000000',
      installmentCount: 12,
      timeZone: 'America/Bogota',
      paymentSchedule: {
        frequency: 'MONTHLY',
        intervalDays: null,
        daysOfMonth: [15, 30],
      },
    });
  });

  test('maps every-N-days without sending monthly payment days', () => {
    const form = {
      ...validForm(),
      frequency: 'EVERY_N_DAYS' as const,
      intervalDays: '10',
    };

    expect(validateCreateLoanForm(form, '2026-08-24')).toEqual({});
    expect(toCreateLoanInput(form, 'UTC').paymentSchedule).toMatchObject({
      frequency: 'EVERY_N_DAYS',
      intervalDays: 10,
      daysOfMonth: [],
    });
  });

  test('rejects malformed financial and schedule values before calling the backend', () => {
    const errors = validateCreateLoanForm({
      ...validForm(),
      payerEmail: 'invalid',
      principalAmount: '0.0000',
      currency: 'PESO',
      interestRatePercentage: '-1',
      installmentCount: '601',
      firstDueDate: '2026-08-23',
      daysOfMonth: '0, 32',
    }, '2026-08-24');

    expect(errors).toEqual(expect.objectContaining({
      payerEmail: expect.any(String),
      principalAmount: expect.any(String),
      currency: expect.any(String),
      interestRatePercentage: expect.any(String),
      installmentCount: expect.any(String),
      firstDueDate: expect.any(String),
      daysOfMonth: expect.any(String),
    }));
  });
});
