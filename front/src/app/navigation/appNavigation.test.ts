import { describe, expect, test } from 'vitest';

import { getNavigationItems } from './appNavigation';

function itemIds(roles: readonly string[]) {
  return getNavigationItems(roles).map((item) => item.id);
}

describe('role-aware application navigation', () => {
  test('shows the common and lender options to lenders', () => {
    expect(itemIds(['LENDER'])).toEqual(['home', 'lender-workspace']);
  });

  test('shows the common and payer options to payers', () => {
    expect(itemIds(['PAYER'])).toEqual(['home', 'payer-workspace']);
  });

  test('combines options without duplicates for users with multiple roles', () => {
    expect(itemIds(['PAYER', 'LENDER', 'PAYER'])).toEqual([
      'home',
      'lender-workspace',
      'payer-workspace',
    ]);
  });

  test('does not expose role-specific options for unknown roles', () => {
    expect(itemIds(['AUDITOR'])).toEqual(['home']);
  });
});
