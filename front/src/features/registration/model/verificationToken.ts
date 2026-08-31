import { consumeFragmentToken } from '../../../core/url/fragmentToken';

/** Captures the verification token from the fragment and clears it from the URL. */
export function consumeVerificationToken(): string | null {
  return consumeFragmentToken('verificationToken');
}
