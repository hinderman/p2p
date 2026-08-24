import { ApiError } from '../../../core/http/apiClient';
import type { Money } from './loan.types';

const STATUS_LABELS: Readonly<Record<string, string>> = {
  DRAFT: 'Borrador',
  PENDING_ACCEPTANCE: 'Pendiente de aceptación',
  ACTIVE: 'Activo',
  COMPLETED: 'Finalizado',
  DEFAULTED: 'En mora',
  CANCELLED: 'Cancelado',
};

export function loanStatusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status.replaceAll('_', ' ').toLocaleLowerCase('es');
}

export function loanStatusClass(status: string): string {
  return `status-${status.toLocaleLowerCase().replaceAll('_', '-')}`;
}

export function formatMoney(money: Money): string {
  const match = money.amount.match(/^(-?)(\d+)(?:\.(\d+))?$/);
  if (!match) return `${money.amount} ${money.currency.toUpperCase()}`;

  const [, sign, rawInteger, rawFraction = ''] = match;
  const integer = rawInteger.replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const fraction = rawFraction.replace(/0+$/, '');
  const decimals = fraction ? `,${fraction.padEnd(2, '0')}` : ',00';
  return `${sign}${integer}${decimals} ${money.currency.toUpperCase()}`;
}

export function formatLoanDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('es-CO', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(date);
}

export function loanRequestErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError) || error.status === 0) {
    return 'No fue posible conectar con el servidor. Revisa tu conexión e inténtalo nuevamente.';
  }
  if (error.status === 403) {
    return 'Tu cuenta no tiene permiso para consultar estos préstamos.';
  }
  if (error.status >= 500) {
    return 'El servicio de préstamos no está disponible en este momento.';
  }
  if (error.code === 'invalid_response') {
    return 'El servidor respondió con información que no pudimos interpretar.';
  }
  return 'No fue posible consultar los préstamos. Inténtalo nuevamente.';
}
