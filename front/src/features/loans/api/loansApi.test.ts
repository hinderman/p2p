import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import { registerAuthenticatedSessionController } from '../../../core/http/authenticatedSessionController';
import { createLoan, getLoanDetail, listLoans } from './loansApi';
import type { CreateLoanInput } from '../model/loan.types';

let unregisterController: (() => void) | undefined;

function jsonResponse(status: number, payload: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: 'OK',
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(payload),
  } as unknown as Response;
}

beforeEach(() => {
  unregisterController = registerAuthenticatedSessionController({
    getAccessToken: () => 'access-token',
    refreshAccessToken: vi.fn(),
    invalidateSession: vi.fn(),
  });
});

afterEach(() => {
  unregisterController?.();
  unregisterController = undefined;
  vi.unstubAllGlobals();
});

describe('loans API', () => {
  test.each(['lender', 'payer'] as const)('lists %s loans using its backend endpoint', async (scope) => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, [{
      loanId: 'loan-id',
      counterpartyPersonId: 'person-id',
      status: 'ACTIVE',
      originalPrincipal: { amount: 1_000_000.25, currency: 'COP' },
      outstandingBalance: { amount: '750000.1250', currency: 'COP' },
      createdAt: '2026-08-24T12:00:00Z',
    }]));
    vi.stubGlobal('fetch', fetchMock);

    await expect(listLoans(scope)).resolves.toEqual([expect.objectContaining({
      originalPrincipal: { amount: '1000000.2500', currency: 'COP' },
      outstandingBalance: { amount: '750000.1250', currency: 'COP' },
    })]);
    expect(fetchMock.mock.calls[0][0]).toBe(`/api/v1/loans/${scope}`);
    const request = fetchMock.mock.calls[0][1] as RequestInit;
    expect(new Headers(request.headers).get('authorization')).toBe('Bearer access-token');
  });

  test('rejects a monetary number that cannot be represented safely at backend precision', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, [{
      loanId: 'loan-id',
      counterpartyPersonId: 'person-id',
      status: 'ACTIVE',
      originalPrincipal: { amount: 999_999_999_999_999.9, currency: 'COP' },
      outstandingBalance: { amount: 0, currency: 'COP' },
      createdAt: '2026-08-24T12:00:00Z',
    }])));

    await expect(listLoans('lender')).rejects.toMatchObject({ code: 'invalid_response' });
  });

  test('creates a loan with the exact backend request contract', async () => {
    const input: CreateLoanInput = {
      payerEmail: 'payer@example.com',
      originalPrincipal: { amount: '1000000.2500', currency: 'COP' },
      interestRatePercentage: '2.50000000',
      ratePeriod: 'MONTHLY_EFFECTIVE',
      interestCalculationMethod: 'SIMPLE',
      dayCountBasis: 'THIRTY_360',
      amortizationMethod: 'FIXED_PAYMENT',
      capitalPrepaymentPolicy: 'REDUCE_PAYMENT',
      installmentCount: 12,
      firstDueDate: '2026-09-24',
      timeZone: 'America/Bogota',
      paymentSchedule: {
        frequency: 'MONTHLY',
        intervalDays: null,
        daysOfMonth: [15, 30],
        nonBusinessDayAdjustment: 'NEXT_BUSINESS_DAY',
      },
    };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, {
      loanId: 'new-loan-id',
      status: 'PENDING_ACCEPTANCE',
    }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(createLoan(input)).resolves.toEqual({
      loanId: 'new-loan-id',
      status: 'PENDING_ACCEPTANCE',
    });
    const [url, request] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/loans');
    expect(request.method).toBe('POST');
    expect(JSON.parse(request.body as string)).toEqual(input);
  });

  test('gets the contractual detail with exact installment balances', async () => {
    const money = (amount: string) => ({ amount, currency: 'COP' });
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {
      loanId: 'loan-id', counterpartyPersonId: 'person-id', status: 'ACTIVE', createdAt: '2026-08-24T12:00:00Z',
      originalPrincipal: money('1000.0000'), outstandingBalance: money('900.0000'),
      terms: { versionNumber: 1, interestRatePercentage: '2.00000000', ratePeriod: 'MONTHLY_EFFECTIVE',
        interestCalculationMethod: 'SIMPLE', dayCountBasis: 'THIRTY_360', amortizationMethod: 'FIXED_PAYMENT',
        capitalPrepaymentPolicy: 'REDUCE_PAYMENT', installmentCount: 1, firstDueDate: '2026-09-24', timeZone: 'America/Bogota' },
      paymentPlan: { paymentPlanId: 'plan-id', versionNumber: 1, reason: 'ORIGINAL', status: 'CURRENT', installments: [{
        installmentId: 'installment-id', number: 1, dueDate: '2026-09-24', agreedPrincipal: money('1000.0000'),
        agreedInterest: money('20.0000'), agreedFee: money('0.0000'), agreedTotal: money('1020.0000'),
        paidPrincipal: money('100.0000'), paidInterest: money('20.0000'), paidFee: money('0.0000'),
        paidTotal: money('120.0000'), outstandingPrincipal: money('900.0000'), outstandingInterest: money('0.0000'),
        outstandingFee: money('0.0000'), outstandingTotal: money('900.0000'),
      }] },
    }));
    vi.stubGlobal('fetch', fetchMock);

    const detail = await getLoanDetail('loan/id');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/loans/loan%2Fid');
    expect(detail.paymentPlan?.installments[0].outstandingTotal).toEqual(money('900.0000'));
  });
});
