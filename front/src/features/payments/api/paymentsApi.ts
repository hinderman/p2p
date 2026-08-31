import { apiClient, ApiError } from '../../../core/http/apiClient';
import type {
  ApprovePaymentInput,
  PaymentMoney,
  PaymentPage,
  PaymentPageRequest,
  PaymentResult,
  PendingPayment,
  ReportPaymentInput,
  StoredPaymentProof,
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

function parseStoredPaymentProof(value: unknown): StoredPaymentProof {
  if (
    !isRecord(value) ||
    typeof value.storedObjectId !== 'string' ||
    typeof value.originalName !== 'string' ||
    typeof value.contentType !== 'string' ||
    typeof value.sizeBytes !== 'number' ||
    typeof value.sha256 !== 'string' ||
    value.scanStatus !== 'SAFE'
  ) throw new ApiError(201, 'invalid_response', 'The server returned an invalid payment proof');
  return value as StoredPaymentProof;
}

export async function uploadPaymentProof(file: File): Promise<StoredPaymentProof> {
  const body = new FormData();
  body.append('file', file, file.name);
  return parseStoredPaymentProof(await apiClient<unknown>('/api/v1/payment-proofs', {
    body,
    globalError: false,
    globalLoading: false,
    method: 'POST',
  }));
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

function parseCount(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 ? value : null;
}

function parsePendingPaymentPage(value: unknown): PaymentPage {
  const invalid = new ApiError(200, 'invalid_response', 'Invalid pending payment page');
  if (!isRecord(value) || !Array.isArray(value.content)) throw invalid;
  const content = value.content.map(parsePendingPayment);
  const page = parseCount(value.page);
  const size = parseCount(value.size);
  const totalElements = parseCount(value.totalElements);
  const totalPages = parseCount(value.totalPages);
  if (
    page === null || size === null || totalElements === null || totalPages === null ||
    typeof value.hasNext !== 'boolean' || content.some((payment) => payment === null)
  ) throw invalid;
  return { content: content as PendingPayment[], page, size, totalElements, totalPages, hasNext: value.hasNext };
}

/** The slice is resolved by the backend; this only asks for an index and a size. */
export async function listPendingPayments(
  loanId: string,
  pageRequest: PaymentPageRequest,
  signal?: AbortSignal,
): Promise<PaymentPage> {
  const query = new URLSearchParams({ page: String(pageRequest.page), size: String(pageRequest.size) });
  const response = await apiClient<unknown>(`/api/v1/loans/${loanId}/payments/pending?${query.toString()}`, {
    globalError: false,
    globalLoading: false,
    signal,
  });
  return parsePendingPaymentPage(response);
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
