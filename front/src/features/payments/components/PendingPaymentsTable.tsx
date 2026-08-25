import { IonButton, IonIcon, IonSelect, IonSelectOption } from '@ionic/react';
import { chevronBackOutline, chevronForwardOutline } from 'ionicons/icons';

import type { PaymentPage, PendingPayment } from '../model/payment.types';
import { PAGE_SIZE_OPTIONS, visibleRange } from '../model/paymentPaging';
import {
  formatPaymentMoney,
  isPayablePayment,
  paymentStatusLabel,
  paymentStatusTone,
} from '../model/paymentPresentation';

type PendingPaymentsTableProps = {
  isRefreshing: boolean;
  page: PaymentPage;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onPay: (payment: PendingPayment) => void;
};

/** Presentational: it renders the page the server returned and requests another index. */
export function PendingPaymentsTable({
  isRefreshing,
  page,
  onPageChange,
  onPageSizeChange,
  onPay,
}: PendingPaymentsTableProps) {
  const range = visibleRange(page);
  const pageNumber = page.page + 1;
  const pageCount = Math.max(page.totalPages, 1);

  return (
    <section
      aria-busy={isRefreshing}
      className={isRefreshing ? 'pending-payment-table-panel refreshing' : 'pending-payment-table-panel'}
    >
      <div className="pending-payment-table-scroll">
        <table className="pending-payment-table">
          <caption>Pagos reportados pendientes de este préstamo</caption>
          <thead>
            <tr>
              <th scope="col">Pago</th>
              <th scope="col">Fecha reportada</th>
              <th className="numeric" scope="col">Monto reportado</th>
              <th className="numeric" scope="col">Monto validado</th>
              <th scope="col">Estado</th>
              <th className="actions" scope="col">Acción</th>
            </tr>
          </thead>
          <tbody>
            {page.content.map((payment) => (
              <tr key={payment.reportedPaymentId}>
                <td data-label="Pago">
                  <code>#{payment.reportedPaymentId.slice(0, 8)}</code>
                </td>
                <td data-label="Fecha reportada">{payment.reportedPaymentDate}</td>
                <td className="numeric" data-label="Monto reportado">
                  <strong>{formatPaymentMoney(payment.reportedAmount)}</strong>
                </td>
                <td className="numeric" data-label="Monto validado">
                  {payment.validatedAmount ? formatPaymentMoney(payment.validatedAmount) : '—'}
                </td>
                <td data-label="Estado">
                  <span className={`payment-status tone-${paymentStatusTone(payment.status)}`}>
                    {paymentStatusLabel(payment.status)}
                  </span>
                </td>
                <td className="actions" data-label="Acción">
                  {isPayablePayment(payment.status) && (
                    <IonButton
                      aria-label={`Pagar el pago #${payment.reportedPaymentId.slice(0, 8)}`}
                      size="small"
                      onClick={() => onPay(payment)}
                    >
                      Pagar
                    </IonButton>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <nav aria-label="Paginación de pagos pendientes" className="pending-payment-pagination">
        <p role="status">
          Mostrando {range.first}–{range.last} de {page.totalElements}{' '}
          {page.totalElements === 1 ? 'pago' : 'pagos'}
        </p>
        <div className="pending-payment-page-size">
          <IonSelect
            aria-label="Pagos por página"
            disabled={isRefreshing}
            interface="popover"
            label="Por página"
            labelPlacement="start"
            value={page.size}
            onIonChange={(event) => onPageSizeChange(Number(event.detail.value))}
          >
            {PAGE_SIZE_OPTIONS.map((option) => (
              <IonSelectOption key={option} value={option}>{option}</IonSelectOption>
            ))}
          </IonSelect>
        </div>
        <div className="pending-payment-page-controls">
          <IonButton
            aria-label="Página anterior"
            disabled={isRefreshing || page.page === 0}
            fill="clear"
            size="small"
            onClick={() => onPageChange(page.page - 1)}
          >
            <IonIcon aria-hidden="true" icon={chevronBackOutline} slot="icon-only" />
          </IonButton>
          <span>Página {pageNumber} de {pageCount}</span>
          <IonButton
            aria-label="Página siguiente"
            disabled={isRefreshing || !page.hasNext}
            fill="clear"
            size="small"
            onClick={() => onPageChange(page.page + 1)}
          >
            <IonIcon aria-hidden="true" icon={chevronForwardOutline} slot="icon-only" />
          </IonButton>
        </div>
      </nav>
    </section>
  );
}
