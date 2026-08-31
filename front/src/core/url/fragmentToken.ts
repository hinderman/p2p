const MAX_TOKEN_LENGTH = 512;

/**
 * Reads a one-time token from the URL fragment and removes it immediately.
 *
 * The backend puts these tokens in the fragment on purpose: a fragment is never
 * sent to the web server and never appears in a `Referer` header, so the token
 * cannot leak through access logs or a third-party link. Clearing it also keeps
 * it out of the address bar, browser history, and screenshots.
 */
export function consumeFragmentToken(parameter: string): string | null {
  const fragment = window.location.hash.startsWith('#')
    ? window.location.hash.slice(1)
    : window.location.hash;
  const parameters = new URLSearchParams(fragment);

  if (!parameters.has(parameter)) return null;

  const token = parameters.get(parameter)?.trim() ?? '';
  parameters.delete(parameter);

  const remainingFragment = parameters.toString();
  const sanitizedUrl = `${window.location.pathname}${window.location.search}${
    remainingFragment ? `#${remainingFragment}` : ''
  }`;
  window.history.replaceState(window.history.state, '', sanitizedUrl);

  return token.length > 0 && token.length <= MAX_TOKEN_LENGTH ? token : null;
}
