import { describe, expect, test } from 'vitest';

import { describePasswordProblem, validateRegistration } from './registrationForm';

const validDraft = {
  email: 'carolina@example.com',
  firstName: 'Carolina',
  lastName: 'Restrepo',
  password: 'mesa verde caliente',
  passwordConfirmation: 'mesa verde caliente',
};

describe('describePasswordProblem', () => {
  test('accepts a long passphrase without demanding symbols or digits', () => {
    expect(describePasswordProblem('mesa verde caliente', 'carolina@example.com')).toBeUndefined();
  });

  test('rejects a password shorter than the minimum', () => {
    expect(describePasswordProblem('corta12345', 'carolina@example.com')).toMatch(/12 y 128/);
  });

  test('rejects a password built from too few distinct characters', () => {
    expect(describePasswordProblem('ababababababab', 'carolina@example.com')).toMatch(/distintos/);
  });

  test('rejects a well-known password even when it is long enough', () => {
    expect(describePasswordProblem('MiPassword2026', 'carolina@example.com')).toMatch(/común/);
  });

  test('rejects a password containing the email local part', () => {
    expect(describePasswordProblem('Carolina-2026-x', 'carolina@example.com')).toMatch(/correo/);
  });

  /** Mirrors the backend rule: a very short local part would ban unrelated passwords. */
  test('ignores a local part too short to be meaningful', () => {
    expect(describePasswordProblem('abc montaña serena', 'abc@example.com')).toBeUndefined();
  });
});

describe('validateRegistration', () => {
  test('accepts a complete draft', () => {
    expect(validateRegistration(validDraft)).toEqual({});
  });

  test('reports every missing field at once instead of one per submission', () => {
    const errors = validateRegistration({
      email: '',
      firstName: '',
      lastName: '',
      password: '',
      passwordConfirmation: '',
    });

    expect(Object.keys(errors).sort()).toEqual([
      'email',
      'firstName',
      'lastName',
      'password',
      'passwordConfirmation',
    ]);
  });

  test('rejects a malformed email', () => {
    expect(validateRegistration({ ...validDraft, email: 'no-arroba' }).email).toBeDefined();
  });

  test('rejects a confirmation that does not match', () => {
    const errors = validateRegistration({ ...validDraft, passwordConfirmation: 'otra cosa larga' });

    expect(errors.passwordConfirmation).toMatch(/no coinciden/);
  });

  /** The password check must use the typed email, not a trimmed-away one. */
  test('applies the email rule to the trimmed address', () => {
    const errors = validateRegistration({
      ...validDraft,
      email: '  carolina@example.com  ',
      password: 'carolina segura',
      passwordConfirmation: 'carolina segura',
    });

    expect(errors.password).toMatch(/correo/);
  });
});
