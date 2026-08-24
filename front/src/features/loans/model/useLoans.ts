import { useCallback, useEffect, useState } from 'react';

import { ApiError } from '../../../core/http/apiClient';
import { listLoans } from '../api/loansApi';
import type { LoanScope, LoanSummary } from './loan.types';

type LoanListState =
  | { status: 'loading'; loans: LoanSummary[]; error: null }
  | { status: 'ready'; loans: LoanSummary[]; error: null }
  | { status: 'error'; loans: LoanSummary[]; error: unknown };

export function useLoans(scope: LoanScope) {
  const [reloadVersion, setReloadVersion] = useState(0);
  const [state, setState] = useState<LoanListState>({
    status: 'loading',
    loans: [],
    error: null,
  });

  useEffect(() => {
    const controller = new AbortController();
    setState((current) => ({ status: 'loading', loans: current.loans, error: null }));

    void listLoans(scope, controller.signal)
      .then((loans) => setState({ status: 'ready', loans, error: null }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiError && error.code === 'request_aborted') return;
        setState((current) => ({ status: 'error', loans: current.loans, error }));
      });

    return () => controller.abort();
  }, [reloadVersion, scope]);

  const reload = useCallback(() => setReloadVersion((version) => version + 1), []);
  return { ...state, reload };
}
