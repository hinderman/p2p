import { apiClient, ApiError } from '../../../core/http/apiClient';
import type { CreatedLoan, CreateLoanInput, InstallmentDetail, LoanDetail, LoanScope, LoanSummary, Money } from '../model/loan.types';

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

function parseInstallment(value: unknown): InstallmentDetail | null {
  if (!isRecord(value) || typeof value.installmentId !== 'string' || typeof value.number !== 'number' ||
      !Number.isInteger(value.number) || typeof value.dueDate !== 'string') return null;
  const moneyFields = ['agreedPrincipal', 'agreedInterest', 'agreedFee', 'agreedTotal', 'paidPrincipal',
    'paidInterest', 'paidFee', 'paidTotal', 'outstandingPrincipal', 'outstandingInterest',
    'outstandingFee', 'outstandingTotal'] as const;
  const parsed = Object.fromEntries(moneyFields.map((field) => [field, parseMoney(value[field])]));
  if (moneyFields.some((field) => parsed[field] === null)) return null;
  return { installmentId: value.installmentId, number: value.number, dueDate: value.dueDate,
    ...parsed } as InstallmentDetail;
}

function parseLoanDetail(value: unknown): LoanDetail {
  const summary = parseLoanSummary(value);
  if (!summary || !isRecord(value) || !isRecord(value.terms)) {
    throw new ApiError(200, 'invalid_response', 'The server returned an invalid loan detail');
  }
  const terms = value.terms;
  if (typeof terms.versionNumber !== 'number' || typeof terms.interestRatePercentage !== 'string' ||
      typeof terms.ratePeriod !== 'string' || typeof terms.interestCalculationMethod !== 'string' ||
      typeof terms.dayCountBasis !== 'string' || typeof terms.amortizationMethod !== 'string' ||
      typeof terms.capitalPrepaymentPolicy !== 'string' || typeof terms.installmentCount !== 'number' ||
      typeof terms.firstDueDate !== 'string' || typeof terms.timeZone !== 'string') {
    throw new ApiError(200, 'invalid_response', 'The server returned invalid loan terms');
  }
  let paymentPlan: LoanDetail['paymentPlan'] = null;
  if (value.paymentPlan !== null) {
    const plan = value.paymentPlan;
    if (!isRecord(plan) || typeof plan.paymentPlanId !== 'string' || typeof plan.versionNumber !== 'number' ||
        typeof plan.reason !== 'string' || typeof plan.status !== 'string' || !Array.isArray(plan.installments)) {
      throw new ApiError(200, 'invalid_response', 'The server returned an invalid payment plan');
    }
    const installments = plan.installments.map(parseInstallment);
    if (installments.some((installment) => installment === null)) {
      throw new ApiError(200, 'invalid_response', 'The server returned invalid installments');
    }
    paymentPlan = { paymentPlanId: plan.paymentPlanId, versionNumber: plan.versionNumber,
      reason: plan.reason, status: plan.status, installments: installments as InstallmentDetail[] };
  }
  return { ...summary, terms: terms as LoanDetail['terms'], paymentPlan };
}

export async function listLoans(scope: LoanScope, signal?: AbortSignal): Promise<LoanSummary[]> {
  const response = await apiClient<unknown>(`/api/v1/loans/${scope}`, {
    globalError: false,
    globalLoading: false,
    signal,
  });
  return parseLoanList(response);
}

export async function getLoanDetail(loanId: string, signal?: AbortSignal): Promise<LoanDetail> {
  const response = await apiClient<unknown>(`/api/v1/loans/${encodeURIComponent(loanId)}`, {
    globalError: false,
    globalLoading: false,
    signal,
  });
  return parseLoanDetail(response);
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
