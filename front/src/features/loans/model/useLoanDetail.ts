import { useCallback, useEffect, useState } from 'react';

import { ApiError } from '../../../core/http/apiClient';
import { getLoanDetail } from '../api/loansApi';
import type { LoanDetail } from './loan.types';

export function useLoanDetail(loanId: string) {
  const [version, setVersion] = useState(0);
  const [detail, setDetail] = useState<LoanDetail | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  useEffect(() => {
    const controller = new AbortController();
    setStatus('loading');
    setError(null);
    void getLoanDetail(loanId, controller.signal).then((value) => {
      setDetail(value); setStatus('ready');
    }).catch((requestError: unknown) => {
      if (controller.signal.aborted || (requestError instanceof ApiError && requestError.code === 'request_aborted')) return;
      setError(requestError); setStatus('error');
    });
    return () => controller.abort();
  }, [loanId, version]);
  return { detail, error, status, reload: useCallback(() => setVersion((current) => current + 1), []) };
}
