export type Money = {
  amount: string;
  currency: string;
};

export type LoanSummary = {
  loanId: string;
  counterpartyPersonId: string;
  status: string;
  originalPrincipal: Money;
  outstandingBalance: Money;
  createdAt: string;
};

export type LoanScope = 'lender' | 'payer';

export type InstallmentDetail = {
  installmentId: string;
  number: number;
  dueDate: string;
  agreedPrincipal: Money;
  agreedInterest: Money;
  agreedFee: Money;
  agreedTotal: Money;
  paidPrincipal: Money;
  paidInterest: Money;
  paidFee: Money;
  paidTotal: Money;
  outstandingPrincipal: Money;
  outstandingInterest: Money;
  outstandingFee: Money;
  outstandingTotal: Money;
};

export type LoanDetail = LoanSummary & {
  terms: {
    versionNumber: number;
    interestRatePercentage: string;
    ratePeriod: RatePeriod;
    interestCalculationMethod: InterestCalculationMethod;
    dayCountBasis: DayCountBasis;
    amortizationMethod: AmortizationMethod;
    capitalPrepaymentPolicy: CapitalPrepaymentPolicy;
    installmentCount: number;
    firstDueDate: string;
    timeZone: string;
  };
  paymentPlan: {
    paymentPlanId: string;
    versionNumber: number;
    reason: string;
    status: string;
    installments: InstallmentDetail[];
  } | null;
};

export type RatePeriod =
  | 'DAILY'
  | 'MONTHLY_NOMINAL'
  | 'MONTHLY_EFFECTIVE'
  | 'ANNUAL_NOMINAL'
  | 'ANNUAL_EFFECTIVE';

export type InterestCalculationMethod = 'SIMPLE' | 'COMPOUND';
export type DayCountBasis = 'THIRTY_360' | 'ACTUAL_360' | 'ACTUAL_365';
export type AmortizationMethod =
  | 'FIXED_PAYMENT'
  | 'FIXED_PRINCIPAL'
  | 'INTEREST_AT_MATURITY';
export type CapitalPrepaymentPolicy = 'SHORTEN_TERM' | 'REDUCE_PAYMENT' | 'NO_RECALCULATION';
export type PaymentFrequency = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'EVERY_N_DAYS';
export type NonBusinessDayAdjustment =
  | 'NEXT_BUSINESS_DAY'
  | 'PREVIOUS_BUSINESS_DAY'
  | 'NO_ADJUSTMENT';

export type CreateLoanInput = {
  payerEmail: string;
  originalPrincipal: Money;
  interestRatePercentage: string;
  ratePeriod: RatePeriod;
  interestCalculationMethod: InterestCalculationMethod;
  dayCountBasis: DayCountBasis;
  amortizationMethod: AmortizationMethod;
  capitalPrepaymentPolicy: CapitalPrepaymentPolicy;
  installmentCount: number;
  firstDueDate: string;
  timeZone: string;
  paymentSchedule: {
    frequency: PaymentFrequency;
    intervalDays: number | null;
    daysOfMonth: number[];
    nonBusinessDayAdjustment: NonBusinessDayAdjustment;
  };
};

export type CreatedLoan = {
  loanId: string;
  status: string;
};
