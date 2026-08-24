import type {
  AmortizationMethod,
  CapitalPrepaymentPolicy,
  CreateLoanInput,
  DayCountBasis,
  InterestCalculationMethod,
  NonBusinessDayAdjustment,
  PaymentFrequency,
  RatePeriod,
} from './loan.types';

export type CreateLoanFormValues = {
  payerEmail: string;
  principalAmount: string;
  currency: string;
  interestRatePercentage: string;
  ratePeriod: RatePeriod;
  interestCalculationMethod: InterestCalculationMethod;
  dayCountBasis: DayCountBasis;
  amortizationMethod: AmortizationMethod;
  capitalPrepaymentPolicy: CapitalPrepaymentPolicy;
  installmentCount: string;
  firstDueDate: string;
  frequency: PaymentFrequency;
  intervalDays: string;
  daysOfMonth: string;
  nonBusinessDayAdjustment: NonBusinessDayAdjustment;
};

export type CreateLoanFormErrors = Partial<Record<keyof CreateLoanFormValues, string>>;

function localIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function minimumDueDate(now = new Date()): string {
  return localIsoDate(now);
}

export function defaultCreateLoanForm(now = new Date()): CreateLoanFormValues {
  const firstDue = new Date(now);
  firstDue.setDate(firstDue.getDate() + 30);
  return {
    payerEmail: '',
    principalAmount: '',
    currency: 'COP',
    interestRatePercentage: '',
    ratePeriod: 'MONTHLY_EFFECTIVE',
    interestCalculationMethod: 'SIMPLE',
    dayCountBasis: 'THIRTY_360',
    amortizationMethod: 'FIXED_PAYMENT',
    capitalPrepaymentPolicy: 'REDUCE_PAYMENT',
    installmentCount: '12',
    firstDueDate: localIsoDate(firstDue),
    frequency: 'MONTHLY',
    intervalDays: '30',
    daysOfMonth: String(firstDue.getDate()),
    nonBusinessDayAdjustment: 'NEXT_BUSINESS_DAY',
  };
}

function parseDaysOfMonth(value: string): number[] | null {
  if (!value.trim()) return [];
  const parts = value.split(',').map((day) => day.trim());
  if (parts.some((day) => !/^\d{1,2}$/.test(day))) return null;
  const days = parts.map(Number);
  if (days.some((day) => day < 1 || day > 31)) return null;
  return [...new Set(days)];
}

function isCalendarDate(value: string): boolean {
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return false;
  const [, year, month, day] = match;
  const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
  return date.getUTCFullYear() === Number(year) &&
    date.getUTCMonth() === Number(month) - 1 &&
    date.getUTCDate() === Number(day);
}

export function validateCreateLoanForm(
  values: CreateLoanFormValues,
  today = minimumDueDate(),
): CreateLoanFormErrors {
  const errors: CreateLoanFormErrors = {};
  const email = values.payerEmail.trim();
  if (!email || email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errors.payerEmail = 'Ingresa un correo electrónico válido.';
  }

  if (!/^\d{1,15}(?:\.\d{1,4})?$/.test(values.principalAmount.trim()) ||
      !/[1-9]/.test(values.principalAmount)) {
    errors.principalAmount = 'Ingresa un monto positivo con máximo 4 decimales.';
  }
  if (!/^[A-Za-z]{3}$/.test(values.currency.trim())) {
    errors.currency = 'Usa un código de moneda de tres letras, por ejemplo COP.';
  }
  if (!/^\d{1,4}(?:\.\d{1,8})?$/.test(values.interestRatePercentage.trim())) {
    errors.interestRatePercentage = 'Ingresa un porcentaje entre 0 y 9999 con máximo 8 decimales.';
  }

  const installments = Number(values.installmentCount);
  if (!/^\d+$/.test(values.installmentCount) || installments < 1 || installments > 600) {
    errors.installmentCount = 'El número de cuotas debe estar entre 1 y 600.';
  }
  if (!isCalendarDate(values.firstDueDate) || values.firstDueDate < today) {
    errors.firstDueDate = 'Selecciona una fecha igual o posterior a hoy.';
  }

  if (values.frequency === 'EVERY_N_DAYS') {
    const interval = Number(values.intervalDays);
    if (!/^\d+$/.test(values.intervalDays) || interval < 1) {
      errors.intervalDays = 'Ingresa un intervalo mayor que cero.';
    }
  }
  if (values.frequency === 'MONTHLY') {
    const days = parseDaysOfMonth(values.daysOfMonth);
    if (!days || days.length === 0) {
      errors.daysOfMonth = 'Ingresa uno o más días entre 1 y 31, separados por comas.';
    }
  }

  return errors;
}

export function toCreateLoanInput(
  values: CreateLoanFormValues,
  timeZone: string,
): CreateLoanInput {
  const daysOfMonth = values.frequency === 'MONTHLY'
    ? (parseDaysOfMonth(values.daysOfMonth) ?? [])
    : [];
  return {
    payerEmail: values.payerEmail.trim().toLowerCase(),
    originalPrincipal: {
      amount: values.principalAmount.trim(),
      currency: values.currency.trim().toUpperCase(),
    },
    interestRatePercentage: values.interestRatePercentage.trim(),
    ratePeriod: values.ratePeriod,
    interestCalculationMethod: values.interestCalculationMethod,
    dayCountBasis: values.dayCountBasis,
    amortizationMethod: values.amortizationMethod,
    capitalPrepaymentPolicy: values.capitalPrepaymentPolicy,
    installmentCount: Number(values.installmentCount),
    firstDueDate: values.firstDueDate,
    timeZone,
    paymentSchedule: {
      frequency: values.frequency,
      intervalDays: values.frequency === 'EVERY_N_DAYS' ? Number(values.intervalDays) : null,
      daysOfMonth,
      nonBusinessDayAdjustment: values.nonBusinessDayAdjustment,
    },
  };
}
