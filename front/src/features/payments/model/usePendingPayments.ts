import { useCallback, useEffect, useState } from 'react';

import { ApiError } from '../../../core/http/apiClient';
import { listPendingPayments } from '../api/paymentsApi';
import type { PendingPayment } from './payment.types';
import { isUuid } from './paymentPresentation';

export function usePendingPayments(loanId: string) {
  const [reloadVersion, setReloadVersion] = useState(0);
  const [state, setState] = useState<{
    status: 'loading' | 'ready' | 'error';
    payments: PendingPayment[];
    error: unknown;
  }>({ status: 'loading', payments: [], error: null });

  useEffect(() => {
    if (!isUuid(loanId)) return;
    const controller = new AbortController();
    setState((current) => ({ ...current, status: 'loading', error: null }));
    void listPendingPayments(loanId, controller.signal)
      .then((payments) => setState({ status: 'ready', payments, error: null }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiError && error.code === 'request_aborted') return;
        setState((current) => ({ ...current, status: 'error', error }));
      });
    return () => controller.abort();
  }, [loanId, reloadVersion]);

  return {
    ...state,
    reload: useCallback(() => setReloadVersion((version) => version + 1), []),
  };
}
