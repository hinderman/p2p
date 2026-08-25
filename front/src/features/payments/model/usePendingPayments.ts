import { useCallback, useEffect, useState } from 'react';

import { ApiError } from '../../../core/http/apiClient';
import { listPendingPayments } from '../api/paymentsApi';
import type { PaymentPage, PaymentPageRequest } from './payment.types';
import { DEFAULT_PAGE_SIZE, correctedPage, emptyPaymentPage } from './paymentPaging';
import { isUuid } from './paymentPresentation';

type PendingPaymentsState = {
  status: 'loading' | 'ready' | 'error';
  page: PaymentPage;
  error: unknown;
};

/**
 * Holds only the page coordinates; the page itself always comes from the server.
 * Changing the index or the size triggers a new request instead of re-slicing a
 * list the client would otherwise have to keep in memory.
 */
export function usePendingPayments(loanId: string) {
  const [request, setRequest] = useState<PaymentPageRequest>({ page: 0, size: DEFAULT_PAGE_SIZE });
  const [reloadVersion, setReloadVersion] = useState(0);
  const [state, setState] = useState<PendingPaymentsState>({
    status: 'loading',
    page: emptyPaymentPage(DEFAULT_PAGE_SIZE),
    error: null,
  });

  useEffect(() => {
    if (!isUuid(loanId)) return;
    const controller = new AbortController();
    setState((current) => ({ ...current, status: 'loading', error: null }));
    void listPendingPayments(loanId, request, controller.signal)
      .then((page) => {
        const correction = correctedPage(page);
        // Stay in the loading state: the effect re-runs for the corrected index.
        if (correction !== null) {
          setRequest((current) => ({ ...current, page: correction }));
          return;
        }
        setState({ status: 'ready', page, error: null });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiError && error.code === 'request_aborted') return;
        setState((current) => ({ ...current, status: 'error', error }));
      });
    return () => controller.abort();
  }, [loanId, request, reloadVersion]);

  return {
    error: state.error,
    page: state.page,
    payments: state.page.content,
    status: state.status,
    reload: useCallback(() => setReloadVersion((version) => version + 1), []),
    goToPage: useCallback((page: number) => setRequest((current) => ({ ...current, page })), []),
    changePageSize: useCallback((size: number) => setRequest({ page: 0, size }), []),
  };
}
