import { describe, expect, test } from 'vitest';

import { formatMoney, loanStatusLabel } from './loanPresentation';

describe('loan presentation', () => {
  test('formats decimal strings without floating-point conversion', () => {
    expect(formatMoney({ amount: '123456789012345.6789', currency: 'cop' }))
      .toBe('123.456.789.012.345,6789 COP');
    expect(formatMoney({ amount: '1000.0000', currency: 'USD' })).toBe('1.000,00 USD');
  });

  test('translates known statuses and preserves unknown statuses visibly', () => {
    expect(loanStatusLabel('PENDING_ACCEPTANCE')).toBe('Pendiente de aceptación');
    expect(loanStatusLabel('UNDER_REVIEW')).toBe('under review');
  });
});
