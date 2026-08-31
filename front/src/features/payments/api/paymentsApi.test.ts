import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import { registerAuthenticatedSessionController } from '../../../core/http/authenticatedSessionController';
import {
  approvePayment,
  listPendingPayments,
  rejectPayment,
  reportPayment,
  reversePayment,
  uploadPaymentProof,
} from './paymentsApi';
import type { ApprovePaymentInput, ReportPaymentInput } from '../model/payment.types';

let unregisterController: (() => void) | undefined;

function response(payload: unknown): Response {
  return {
    ok: true,
    status: 200,
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

describe('payments API', () => {
  test('uploads a proof as multipart data and accepts only a SAFE response', async () => {
    const payload = {
      storedObjectId: 'd98d8980-5be6-4439-ab0d-a12da2a9da5b',
      originalName: 'comprobante.pdf', contentType: 'application/pdf', sizeBytes: 12,
      sha256: 'a'.repeat(64), scanStatus: 'SAFE',
    };
    const fetchMock = vi.fn().mockResolvedValue(response(payload));
    vi.stubGlobal('fetch', fetchMock);

    await expect(uploadPaymentProof(new File(['%PDF-proof'], 'comprobante.pdf', { type: 'application/pdf' })))
      .resolves.toEqual(payload);
    const [url, request] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/payment-proofs');
    expect(request.method).toBe('POST');
    expect(request.body).toBeInstanceOf(FormData);
    expect(new Headers(request.headers).has('content-type')).toBe(false);
  });

  test('reports a payment with its stable idempotency header and proofs', async () => {
    const input: ReportPaymentInput = {
      paymentType: 'INSTALLMENT',
      reportedAmount: { amount: '150000.2500', currency: 'COP' },
      reportedPaymentDate: '2026-08-24',
      externalReference: 'TRANSFER-42',
      proofs: [{ storedObjectId: 'd98d8980-5be6-4439-ab0d-a12da2a9da5b', sha256: 'a'.repeat(64) }],
    };
    const fetchMock = vi.fn().mockResolvedValue(response({ reportedPaymentId: 'payment-id', status: 'PENDING_REVIEW' }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(reportPayment('loan-id', input, 'idempotency-uuid')).resolves.toEqual({ reportedPaymentId: 'payment-id', status: 'PENDING_REVIEW' });
    const [url, request] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/loans/loan-id/payments');
    expect(request.method).toBe('POST');
    expect(new Headers(request.headers).get('idempotency-key')).toBe('idempotency-uuid');
    expect(new Headers(request.headers).get('authorization')).toBe('Bearer access-token');
    expect(JSON.parse(request.body as string)).toEqual(input);
  });

  test('requests one server page and normalizes BigDecimal values', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response({
      content: [{
        reportedPaymentId: 'payment-id', loanId: 'loan-id', status: 'PENDING_REVIEW',
        reportedAmount: { amount: 1200.5, currency: 'COP' }, validatedAmount: null,
        reportedPaymentDate: '2026-08-24',
      }],
      page: 1, size: 5, totalElements: 7, totalPages: 2, hasNext: false,
    }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await listPendingPayments('loan-id', { page: 1, size: 5 });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/loans/loan-id/payments/pending?page=1&size=5');
    expect(result.content).toEqual([expect.objectContaining({
      reportedAmount: { amount: '1200.5000', currency: 'COP' }, validatedAmount: null,
    })]);
    expect(result).toMatchObject({ page: 1, size: 5, totalElements: 7, totalPages: 2, hasNext: false });
  });

  test('rejects a page response without its pagination metadata', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ content: [] })));

    await expect(listPendingPayments('loan-id', { page: 0, size: 5 })).rejects.toThrow();
  });

  test('sends a complete approval allocation contract', async () => {
    const input: ApprovePaymentInput = {
      validatedAmount: { amount: '100.0000', currency: 'COP' },
      allocations: [{
        installmentId: '07046205-65a7-442c-99bd-9a4265b50344',
        type: 'INSTALLMENT_PRINCIPAL',
        amount: { amount: '100.0000', currency: 'COP' },
      }],
    };
    const fetchMock = vi.fn().mockResolvedValue(response({ reportedPaymentId: 'payment-id', status: 'APPROVED' }));
    vi.stubGlobal('fetch', fetchMock);

    await approvePayment('payment-id', input);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/payments/payment-id/approval');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body as string)).toEqual(input);
  });

  test.each([
    ['rejects', rejectPayment, 'rejection', 'REJECTED'],
    ['reverses', reversePayment, 'reversal', 'REVERSED'],
  ] as const)('%s a payment with a trimmed reason', async (_label, operation, path, status) => {
    const fetchMock = vi.fn().mockResolvedValue(response({ reportedPaymentId: 'payment-id', status }));
    vi.stubGlobal('fetch', fetchMock);

    await operation('payment-id', '  Motivo financiero  ');
    expect(fetchMock.mock.calls[0][0]).toBe(`/api/v1/payments/payment-id/${path}`);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body as string)).toEqual({ reason: 'Motivo financiero' });
  });
});
