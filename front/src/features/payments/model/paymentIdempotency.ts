import type { ReportPaymentInput } from './payment.types';

type StoredPaymentAttempt = {
  idempotencyKey: string;
  payload: string;
};

const STORAGE_PREFIX = 'hybrid-client.payment-attempt.v1';

function storageKey(accountId: string, loanId: string): string {
  return `${STORAGE_PREFIX}.${accountId}.${loanId}`;
}

function newIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((value) => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function readAttempt(key: string): StoredPaymentAttempt | null {
  try {
    const value = JSON.parse(window.sessionStorage.getItem(key) ?? 'null') as unknown;
    if (
      typeof value === 'object' && value !== null &&
      'idempotencyKey' in value && typeof value.idempotencyKey === 'string' &&
      'payload' in value && typeof value.payload === 'string'
    ) return value as StoredPaymentAttempt;
  } catch {
    // A corrupt attempt is replaced with a new key below.
  }
  return null;
}

function canonicalAmount(amount: string): string {
  const [integer, fraction = ''] = amount.split('.');
  return `${integer.replace(/^0+(?=\d)/, '')}.${fraction.padEnd(4, '0')}`;
}

function canonicalPayload(input: ReportPaymentInput): string {
  return JSON.stringify({
    ...input,
    reportedAmount: {
      amount: canonicalAmount(input.reportedAmount.amount),
      currency: input.reportedAmount.currency.toUpperCase(),
    },
    externalReference: input.externalReference?.trim() || null,
    proofs: [...input.proofs]
      .map((proof) => ({
        storedObjectId: proof.storedObjectId.toLowerCase(),
        sha256: proof.sha256.toLowerCase(),
      }))
      .sort((first, second) => first.storedObjectId.localeCompare(second.storedObjectId)),
  });
}

export function resolvePaymentAttempt(
  accountId: string,
  loanId: string,
  input: ReportPaymentInput,
): StoredPaymentAttempt {
  const key = storageKey(accountId, loanId);
  const payload = canonicalPayload(input);
  const stored = readAttempt(key);
  if (stored?.payload === payload) return stored;

  const attempt = { idempotencyKey: newIdempotencyKey(), payload };
  window.sessionStorage.setItem(key, JSON.stringify(attempt));
  return attempt;
}

export function completePaymentAttempt(accountId: string, loanId: string, idempotencyKey: string): void {
  const key = storageKey(accountId, loanId);
  const stored = readAttempt(key);
  if (stored?.idempotencyKey === idempotencyKey) window.sessionStorage.removeItem(key);
}
