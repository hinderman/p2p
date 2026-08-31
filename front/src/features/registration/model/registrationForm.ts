export type RegistrationFieldErrors = {
  email?: string;
  firstName?: string;
  lastName?: string;
  password?: string;
  passwordConfirmation?: string;
};

export type RegistrationDraft = {
  email: string;
  firstName: string;
  lastName: string;
  password: string;
  passwordConfirmation: string;
};

export const MINIMUM_PASSWORD_LENGTH = 12;
export const MAXIMUM_PASSWORD_LENGTH = 128;
const MINIMUM_DISTINCT_CHARACTERS = 5;
const SIGNIFICANT_LOCAL_PART_LENGTH = 4;

/**
 * Mirrors `PasswordPolicy` in the backend domain so the person sees the unmet
 * rule, in their own language, beside the field — instead of submitting and
 * waiting for a translated-away server message. The server stays the authority:
 * this only saves a round trip.
 */
const WELL_KNOWN_BASES = [
  'password',
  'passw0rd',
  'contrasena',
  'contraseña',
  'qwerty',
  'asdfgh',
  'iloveyou',
  'letmein',
  'welcome',
  'admin123',
  'administrator',
  '12345678',
];

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function describePasswordProblem(password: string, email: string): string | undefined {
  if (!password) return 'Ingresa una contraseña.';
  if (password.length < MINIMUM_PASSWORD_LENGTH || password.length > MAXIMUM_PASSWORD_LENGTH) {
    return `La contraseña debe tener entre ${MINIMUM_PASSWORD_LENGTH} y ${MAXIMUM_PASSWORD_LENGTH} caracteres.`;
  }
  if (password.trim().length === 0) return 'La contraseña no puede ser solo espacios.';
  if (new Set(password).size < MINIMUM_DISTINCT_CHARACTERS) {
    return `La contraseña debe usar al menos ${MINIMUM_DISTINCT_CHARACTERS} caracteres distintos.`;
  }

  const normalized = password.toLowerCase();
  if (WELL_KNOWN_BASES.some((base) => normalized.includes(base))) {
    return 'Esa contraseña es demasiado común. Elige una más difícil de adivinar.';
  }

  const localPart = email.trim().toLowerCase().split('@')[0] ?? '';
  if (localPart.length >= SIGNIFICANT_LOCAL_PART_LENGTH && normalized.includes(localPart)) {
    return 'La contraseña no puede contener tu correo.';
  }

  return undefined;
}

export function validateRegistration(draft: RegistrationDraft): RegistrationFieldErrors {
  const errors: RegistrationFieldErrors = {};
  const email = draft.email.trim();

  if (!email) {
    errors.email = 'Ingresa tu correo electrónico.';
  } else if (email.length > 254 || !EMAIL_PATTERN.test(email)) {
    errors.email = 'Ingresa un correo electrónico válido.';
  }

  if (!draft.firstName.trim()) {
    errors.firstName = 'Ingresa tu nombre.';
  } else if (draft.firstName.trim().length > 120) {
    errors.firstName = 'El nombre es demasiado largo.';
  }

  if (!draft.lastName.trim()) {
    errors.lastName = 'Ingresa tu apellido.';
  } else if (draft.lastName.trim().length > 120) {
    errors.lastName = 'El apellido es demasiado largo.';
  }

  const passwordProblem = describePasswordProblem(draft.password, email);
  if (passwordProblem) errors.password = passwordProblem;

  if (!draft.passwordConfirmation) {
    errors.passwordConfirmation = 'Confirma tu contraseña.';
  } else if (draft.password !== draft.passwordConfirmation) {
    errors.passwordConfirmation = 'Las contraseñas no coinciden.';
  }

  return errors;
}
