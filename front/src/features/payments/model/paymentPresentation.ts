import { ApiError } from '../../../core/http/apiClient';
import type { PaymentMoney } from './payment.types';

export function formatPaymentMoney(money: PaymentMoney): string {
  const match = money.amount.match(/^(-?)(\d+)(?:\.(\d+))?$/);
  if (!match) return `${money.amount} ${money.currency.toUpperCase()}`;
  const [, sign, rawInteger, rawFraction = ''] = match;
  const integer = rawInteger.replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const fraction = rawFraction.replace(/0+$/, '');
  return `${sign}${integer}${fraction ? `,${fraction.padEnd(2, '0')}` : ',00'} ${money.currency.toUpperCase()}`;
}

export function paymentErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError) || error.status === 0) {
    return 'No fue posible conectar con el servidor. Puedes reintentar la misma operación de forma segura.';
  }
  if (error.status === 403) return 'Tu cuenta no tiene permiso para realizar esta operación.';
  if (error.status === 404) return 'El préstamo o pago ya no está disponible.';
  if (error.status === 409 && error.code === 'idempotency_conflict') {
    return 'La clave de seguridad ya estaba asociada a otro envío. Verifica el historial antes de volver a reportar.';
  }
  if (error.status === 422) return 'La operación no cumple una regla financiera del préstamo.';
  if (error.status >= 500) return 'El servicio no está disponible. Puedes reintentar sin duplicar el reporte.';
  return 'No fue posible completar la operación. Revisa los datos e inténtalo nuevamente.';
}

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Borrador',
  SUBMITTED: 'Enviado',
  PENDING_REVIEW: 'Pendiente por pagar',
  APPROVED: 'Pagado',
  REJECTED: 'Rechazado',
  REVERSED: 'Reversado',
};

const PAYMENT_STATUS_TONES: Record<string, string> = {
  DRAFT: 'neutral',
  SUBMITTED: 'pending',
  PENDING_REVIEW: 'pending',
  APPROVED: 'settled',
  REJECTED: 'rejected',
  REVERSED: 'rejected',
};

export function paymentStatusLabel(status: string): string {
  return PAYMENT_STATUS_LABELS[status] ?? status;
}

export function paymentStatusTone(status: string): string {
  return PAYMENT_STATUS_TONES[status] ?? 'neutral';
}

/**
 * Only a payment still awaiting the lender decision can be settled; the backend
 * rejects the operation for every other status.
 */
export function isPayablePayment(status: string): boolean {
  return status === 'PENDING_REVIEW' || status === 'SUBMITTED';
}

export function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

export function isPositiveMoney(value: string): boolean {
  return /^\d{1,15}(?:\.\d{1,4})?$/.test(value.trim()) && /[1-9]/.test(value);
}

export function moneyToScaledInteger(value: string): bigint | null {
  const match = value.trim().match(/^(\d{1,15})(?:\.(\d{1,4}))?$/);
  if (!match) return null;
  return BigInt(match[1]) * 10_000n + BigInt((match[2] ?? '').padEnd(4, '0'));
}

export function isIsoCalendarDate(value: string): boolean {
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return false;
  const [, year, month, day] = match;
  const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
  return date.getUTCFullYear() === Number(year) &&
    date.getUTCMonth() === Number(month) - 1 &&
    date.getUTCDate() === Number(day);
}
