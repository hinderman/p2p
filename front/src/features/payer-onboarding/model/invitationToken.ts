const TOKEN_PARAMETER = 'invitationToken';
const MAX_TOKEN_LENGTH = 512;

/**
 * Captura el token desde el fragmento y lo elimina inmediatamente de la URL.
 * El fragmento nunca viaja al servidor web ni queda disponible para referrers.
 */
export function consumeInvitationToken(): string | null {
  const fragment = window.location.hash.startsWith('#')
    ? window.location.hash.slice(1)
    : window.location.hash;
  const parameters = new URLSearchParams(fragment);

  if (!parameters.has(TOKEN_PARAMETER)) return null;

  const token = parameters.get(TOKEN_PARAMETER)?.trim() ?? '';
  parameters.delete(TOKEN_PARAMETER);

  const remainingFragment = parameters.toString();
  const sanitizedUrl = `${window.location.pathname}${window.location.search}${
    remainingFragment ? `#${remainingFragment}` : ''
  }`;
  window.history.replaceState(window.history.state, '', sanitizedUrl);

  return token.length > 0 && token.length <= MAX_TOKEN_LENGTH ? token : null;
}
