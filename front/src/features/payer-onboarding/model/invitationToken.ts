import { consumeFragmentToken } from '../../../core/url/fragmentToken';

/** Captures the invitation token from the fragment and clears it from the URL. */
export function consumeInvitationToken(): string | null {
  return consumeFragmentToken('invitationToken');
}
