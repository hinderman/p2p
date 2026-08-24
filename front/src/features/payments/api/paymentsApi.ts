import { apiClient, ApiError } from '../../../core/http/apiClient';
import type {
  ApprovePaymentInput,
  PaymentMoney,
  PaymentResult,
  PendingPayment,
  ReportPaymentInput,
} from '../model/payment.types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function normalizeAmount(value: unknown): string | null {
  if (typeof value === 'string' && /^-?\d+(?:\.\d+)?$/.test(value)) return value;
  if (typeof value !== 'number' || !Number.isFinite(value)) return null;
  const scaled = value * 10_000;
  const rounded = Math.round(scaled);
  if (!Number.isSafeInteger(rounded) || Math.abs(scaled - rounded) > 0.000_001) return null;
  return (rounded / 10_000).toFixed(4);
}

function parseMoney(value: unknown): PaymentMoney | null {
  if (!isRecord(value) || typeof value.currency !== 'string') return null;
  const amount = normalizeAmount(value.amount);
  return amount === null ? null : { amount, currency: value.currency };
}

function parsePaymentResult(value: unknown): PaymentResult {
  if (!isRecord(value) || typeof value.reportedPaymentId !== 'string' || typeof value.status !== 'string') {
    throw new ApiError(200, 'invalid_response', 'The server returned an invalid payment result');
  }
  return { reportedPaymentId: value.reportedPaymentId, status: value.status };
}

function parsePendingPayment(value: unknown): PendingPayment | null {
  if (
    !isRecord(value) ||
    typeof value.reportedPaymentId !== 'string' ||
    typeof value.loanId !== 'string' ||
    typeof value.status !== 'string' ||
    typeof value.reportedPaymentDate !== 'string'
  ) return null;
  const reportedAmount = parseMoney(value.reportedAmount);
  const validatedAmount = value.validatedAmount === null ? null : parseMoney(value.validatedAmount);
  if (!reportedAmount || (value.validatedAmount !== null && !validatedAmount)) return null;
  return {
    reportedPaymentId: value.reportedPaymentId,
    loanId: value.loanId,
    status: value.status,
    reportedAmount,
    validatedAmount,
    reportedPaymentDate: value.reportedPaymentDate,
  };
}

export async function reportPayment(
  loanId: string,
  input: ReportPaymentInput,
  idempotencyKey: string,
): Promise<PaymentResult> {
  const response = await apiClient<unknown>(`/api/v1/loans/${loanId}/payments`, {
    globalError: false,
    globalLoading: false,
    headers: { 'Idempotency-Key': idempotencyKey },
    method: 'POST',
    json: input,
  });
  return parsePaymentResult(response);
}

export async function listPendingPayments(loanId: string, signal?: AbortSignal): Promise<PendingPayment[]> {
  const response = await apiClient<unknown>(`/api/v1/loans/${loanId}/payments/pending`, {
    globalError: false,
    globalLoading: false,
    signal,
  });
  if (!Array.isArray(response)) throw new ApiError(200, 'invalid_response', 'Invalid pending payment list');
  const payments = response.map(parsePendingPayment);
  if (payments.some((payment) => payment === null)) {
    throw new ApiError(200, 'invalid_response', 'Invalid pending payment list');
  }
  return payments as PendingPayment[];
}

export async function approvePayment(paymentId: string, input: ApprovePaymentInput): Promise<PaymentResult> {
  return parsePaymentResult(await apiClient<unknown>(`/api/v1/payments/${paymentId}/approval`, {
    globalError: false,
    globalLoading: false,
    method: 'POST',
    json: input,
  }));
}

export async function rejectPayment(paymentId: string, reason: string): Promise<PaymentResult> {
  return parsePaymentResult(await apiClient<unknown>(`/api/v1/payments/${paymentId}/rejection`, {
    globalError: false,
    globalLoading: false,
    method: 'POST',
    json: { reason: reason.trim() },
  }));
}

export async function reversePayment(paymentId: string, reason: string): Promise<PaymentResult> {
  return parsePaymentResult(await apiClient<unknown>(`/api/v1/payments/${paymentId}/reversal`, {
    globalError: false,
    globalLoading: false,
    method: 'POST',
    json: { reason: reason.trim() },
  }));
}
