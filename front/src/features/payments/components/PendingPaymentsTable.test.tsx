import { fireEvent, render, screen } from '@testing-library/react';
import { expect, test, vi } from 'vitest';

import type { PaymentPage, PendingPayment } from '../model/payment.types';
import { PendingPaymentsTable } from './PendingPaymentsTable';

function payment(index: number, status = 'PENDING_REVIEW'): PendingPayment {
  return {
    reportedPaymentId: `0000000${index}-0000-4000-8000-00000000000${index}`,
    loanId: 'b0ebfe24-4094-452a-b6ec-40a2e368232e',
    status,
    reportedAmount: { amount: `${index}00.0000`, currency: 'COP' },
    validatedAmount: null,
    reportedPaymentDate: `2026-01-0${index}`,
  };
}

function page(values: Partial<PaymentPage> = {}): PaymentPage {
  return {
    content: [payment(1), payment(2)],
    page: 0,
    size: 5,
    totalElements: 7,
    totalPages: 2,
    hasNext: true,
    ...values,
  };
}

const noOperation = () => undefined;

function renderTable(overrides: Partial<Parameters<typeof PendingPaymentsTable>[0]> = {}) {
  return render(
    <PendingPaymentsTable
      isRefreshing={false}
      page={page()}
      onPageChange={noOperation}
      onPageSizeChange={noOperation}
      onPay={noOperation}
      {...overrides}
    />,
  );
}

test('renders only the rows the server returned and its absolute range', () => {
  renderTable();

  expect(screen.getAllByRole('row')).toHaveLength(3); // header + 2 payments of 7
  expect(screen.getByRole('status')).toHaveTextContent('Mostrando 1–2 de 7 pagos');
  expect(screen.getByText('Página 1 de 2')).toBeInTheDocument();
});

test('requests the next index instead of slicing locally', () => {
  const onPageChange = vi.fn();
  renderTable({ onPageChange, page: page({ page: 1, hasNext: true }) });

  fireEvent.click(screen.getByLabelText('Página siguiente'));

  expect(onPageChange).toHaveBeenCalledWith(2);
});

test('trusts the server flag to disable forward navigation', () => {
  const onPageChange = vi.fn();
  renderTable({ onPageChange, page: page({ page: 1, hasNext: false, content: [payment(1)] }) });

  // Ionic exposes `disabled` as a custom-element property, not as an attribute.
  expect(control('Página siguiente').disabled).toBe(true);
  expect(control('Página anterior').disabled).toBe(false);
});

function control(label: string): HTMLElement & { disabled?: boolean } {
  return screen.getByLabelText(label) as HTMLElement & { disabled?: boolean };
}

test('offers the payment action only for a payment still pending', () => {
  renderTable({ page: page({ content: [payment(1), payment(2, 'APPROVED')] }) });

  expect(screen.getAllByText('Pagar')).toHaveLength(1);
  expect(screen.getByText('Pendiente por pagar')).toBeInTheDocument();
  expect(screen.getByText('Pagado')).toBeInTheDocument();
});

test('reports the selected payment to its parent', () => {
  const onPay = vi.fn();
  const pending = payment(1);
  renderTable({ onPay, page: page({ content: [pending] }) });

  fireEvent.click(screen.getByText('Pagar'));

  expect(onPay).toHaveBeenCalledWith(pending);
});
