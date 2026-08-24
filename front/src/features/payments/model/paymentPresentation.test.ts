import { describe, expect, test } from 'vitest';

import { formatPaymentMoney, isIsoCalendarDate, moneyToScaledInteger } from './paymentPresentation';

describe('payment decimal handling', () => {
  test('formats exact monetary strings without floating point conversion', () => {
    expect(formatPaymentMoney({ amount: '999999999999999.9999', currency: 'COP' }))
      .toBe('999.999.999.999.999,9999 COP');
  });

  test('compares allocation amounts at the backend four-decimal precision', () => {
    expect(moneyToScaledInteger('100.125')).toBe(1_001_250n);
    expect(moneyToScaledInteger('0.0001')).toBe(1n);
    expect(moneyToScaledInteger('1.00001')).toBeNull();
  });

  test('rejects dates that match the shape but do not exist', () => {
    expect(isIsoCalendarDate('2026-02-28')).toBe(true);
    expect(isIsoCalendarDate('2026-02-30')).toBe(false);
  });
});
