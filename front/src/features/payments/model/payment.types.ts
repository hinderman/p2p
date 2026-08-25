export type PaymentMoney = {
  amount: string;
  currency: string;
};

export type PaymentType = 'INSTALLMENT' | 'CAPITAL_PREPAYMENT';
export type PaymentAllocationType = 'INTEREST' | 'INSTALLMENT_PRINCIPAL' | 'FEE' | 'DIRECT_PRINCIPAL';

export type PaymentProof = {
  storedObjectId: string;
  sha256: string;
};

export type ReportPaymentInput = {
  paymentType: PaymentType;
  reportedAmount: PaymentMoney;
  reportedPaymentDate: string;
  externalReference: string | null;
  proofs: PaymentProof[];
};

export type PaymentResult = {
  reportedPaymentId: string;
  status: string;
};

export type PendingPayment = {
  reportedPaymentId: string;
  loanId: string;
  status: string;
  reportedAmount: PaymentMoney;
  validatedAmount: PaymentMoney | null;
  reportedPaymentDate: string;
};

/**
 * Server-resolved page. The client never slices the collection: it only shows
 * what the backend returned and asks for the next index.
 */
export type PaymentPage = {
  content: PendingPayment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type PaymentPageRequest = {
  page: number;
  size: number;
};

export type PaymentAllocation = {
  installmentId: string | null;
  type: PaymentAllocationType;
  amount: PaymentMoney;
};

export type ApprovePaymentInput = {
  validatedAmount: PaymentMoney;
  allocations: PaymentAllocation[];
};
