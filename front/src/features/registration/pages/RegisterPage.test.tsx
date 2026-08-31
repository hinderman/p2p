import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, test, vi } from 'vitest';

import { RegisterPage } from './RegisterPage';

/**
 * Ionic inputs are lazily upgraded custom elements, so jsdom cannot deliver a
 * value into them: a synthetic `ionInput` never reaches the React handler. What
 * this file can prove is the wiring around them — that the form validates before
 * it calls anything. The rules themselves are covered in `registrationForm.test`
 * and the filled-in flow is exercised against the running backend.
 */
function renderPage() {
  return render(
    <MemoryRouter>
      <RegisterPage />
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

test('renders the lender sign-up form', () => {
  renderPage();

  expect(screen.getByText('Regístrate como prestamista')).toBeInTheDocument();
  expect(screen.getByLabelText('Correo electrónico')).toBeInTheDocument();
  expect(screen.getByLabelText('Confirmar contraseña')).toBeInTheDocument();
});

test('marks every empty field and sends no request', () => {
  const fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
  const view = renderPage();

  fireEvent.submit(view.container.querySelector('form') as HTMLFormElement);

  expect(view.container.querySelectorAll('ion-input.ion-invalid')).toHaveLength(5);
  expect(fetchMock).not.toHaveBeenCalled();
});
