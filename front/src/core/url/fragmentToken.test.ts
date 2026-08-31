import { beforeEach, describe, expect, test } from 'vitest';

import { consumeFragmentToken } from './fragmentToken';

describe('consumeFragmentToken', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/registro/verificacion');
  });

  test('decodes the token and removes it from the address bar', () => {
    window.history.replaceState(
      {},
      '',
      '/registro/verificacion#verificationToken=token%2Bseguro&source=email',
    );

    expect(consumeFragmentToken('verificationToken')).toBe('token+seguro');
    expect(window.location.hash).toBe('#source=email');
  });

  test('returns null when the fragment carries no such token', () => {
    window.history.replaceState({}, '', '/registro/verificacion#source=email');

    expect(consumeFragmentToken('verificationToken')).toBeNull();
    expect(window.location.hash).toBe('#source=email');
  });

  test('rejects a token longer than any the backend issues', () => {
    window.history.replaceState({}, '', `/registro/verificacion#verificationToken=${'a'.repeat(513)}`);

    expect(consumeFragmentToken('verificationToken')).toBeNull();
  });

  /** Two links must not read each other's token. */
  test('ignores a token stored under a different parameter', () => {
    window.history.replaceState({}, '', '/registro/verificacion#invitationToken=other-token');

    expect(consumeFragmentToken('verificationToken')).toBeNull();
    expect(window.location.hash).toBe('#invitationToken=other-token');
  });
});
