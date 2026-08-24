import { beforeEach, describe, expect, test } from 'vitest';

import { consumeInvitationToken } from './invitationToken';

describe('consumeInvitationToken', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/onboarding/payer');
  });

  test('decodes the token and removes it from the address bar', () => {
    window.history.replaceState(
      {},
      '',
      '/onboarding/payer#invitationToken=token%2Bseguro&source=email',
    );

    expect(consumeInvitationToken()).toBe('token+seguro');
    expect(window.location.pathname).toBe('/onboarding/payer');
    expect(window.location.hash).toBe('#source=email');
  });

  test('returns null when the link has no invitation token', () => {
    window.history.replaceState({}, '', '/onboarding/payer#source=email');

    expect(consumeInvitationToken()).toBeNull();
    expect(window.location.hash).toBe('#source=email');
  });
});
