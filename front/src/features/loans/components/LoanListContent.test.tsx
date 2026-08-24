import { fireEvent, render, screen } from '@testing-library/react';
import { expect, test, vi } from 'vitest';

import { ApiError } from '../../../core/http/apiClient';
import { LoanListContent } from './LoanListContent';

const noOperation = () => undefined;

test('renders a dedicated loading state', () => {
  render(
    <LoanListContent
      error={null}
      isLoading
      loans={[]}
      onRetry={noOperation}
    />,
  );

  expect(screen.getByRole('status', { name: 'Cargando préstamos' })).toBeInTheDocument();
});

test('renders the lender empty state and its creation action', () => {
  const onCreate = vi.fn();
  render(
    <LoanListContent
      error={null}
      isLoading={false}
      loans={[]}
      onCreate={onCreate}
      onRetry={noOperation}
    />,
  );

  expect(screen.getByText('Aún no hay préstamos')).toBeInTheDocument();
  fireEvent.click(screen.getByText('Crear préstamo'));
  expect(onCreate).toHaveBeenCalledOnce();
});

test('renders a contextual error and retries the request', () => {
  const onRetry = vi.fn();
  render(
    <LoanListContent
      error={new ApiError(0, 'network_error', 'Offline')}
      isLoading={false}
      loans={[]}
      onRetry={onRetry}
    />,
  );

  expect(screen.getByRole('alert')).toHaveTextContent('No fue posible conectar');
  fireEvent.click(screen.getByText('Reintentar'));
  expect(onRetry).toHaveBeenCalledOnce();
});
