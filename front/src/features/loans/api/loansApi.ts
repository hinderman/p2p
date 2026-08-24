import { apiClient, ApiError } from '../../../core/http/apiClient';
import type { CreatedLoan, CreateLoanInput, LoanScope, LoanSummary, Money } from '../model/loan.types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function normalizeAmount(value: unknown): string | null {
  if (typeof value === 'string' && /^-?\d+(?:\.\d+)?$/.test(value)) return value;
  if (typeof value !== 'number' || !Number.isFinite(value)) return null;
  const scaledAmount = value * 10_000;
  const roundedAmount = Math.round(scaledAmount);
  if (!Number.isSafeInteger(roundedAmount) || Math.abs(scaledAmount - roundedAmount) > 0.000_001) {
    return null;
  }
  return (roundedAmount / 10_000).toFixed(4);
}

function parseMoney(value: unknown): Money | null {
  if (!isRecord(value) || typeof value.currency !== 'string') return null;
  const amount = normalizeAmount(value.amount);
  return amount === null ? null : { amount, currency: value.currency };
}

function parseLoanSummary(value: unknown): LoanSummary | null {
  if (
    !isRecord(value) ||
    typeof value.loanId !== 'string' ||
    typeof value.counterpartyPersonId !== 'string' ||
    typeof value.status !== 'string' ||
    typeof value.createdAt !== 'string'
  ) return null;
  const originalPrincipal = parseMoney(value.originalPrincipal);
  const outstandingBalance = parseMoney(value.outstandingBalance);
  if (!originalPrincipal || !outstandingBalance) return null;
  return {
    loanId: value.loanId,
    counterpartyPersonId: value.counterpartyPersonId,
    status: value.status,
    originalPrincipal,
    outstandingBalance,
    createdAt: value.createdAt,
  };
}

function parseLoanList(value: unknown): LoanSummary[] {
  if (!Array.isArray(value)) {
    throw new ApiError(200, 'invalid_response', 'The server returned an invalid loan list');
  }
  const loans = value.map(parseLoanSummary);
  if (loans.some((loan) => loan === null)) {
    throw new ApiError(200, 'invalid_response', 'The server returned an invalid loan list');
  }
  return loans as LoanSummary[];
}

function parseCreatedLoan(value: unknown): CreatedLoan {
  if (!isRecord(value) || typeof value.loanId !== 'string' || typeof value.status !== 'string') {
    throw new ApiError(201, 'invalid_response', 'The server returned an invalid created loan');
  }
  return { loanId: value.loanId, status: value.status };
}

export async function listLoans(scope: LoanScope, signal?: AbortSignal): Promise<LoanSummary[]> {
  const response = await apiClient<unknown>(`/api/v1/loans/${scope}`, {
    globalError: false,
    globalLoading: false,
    signal,
  });
  return parseLoanList(response);
}

export async function createLoan(input: CreateLoanInput): Promise<CreatedLoan> {
  const response = await apiClient<unknown>('/api/v1/loans', {
    globalError: false,
    globalLoading: false,
    method: 'POST',
    json: input,
  });
  return parseCreatedLoan(response);
}
