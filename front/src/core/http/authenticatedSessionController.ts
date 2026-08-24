export type AuthenticatedSessionController = {
  getAccessToken: () => string | null;
  refreshAccessToken: () => Promise<string | null>;
  invalidateSession: () => void;
};

let activeController: AuthenticatedSessionController | null = null;
let refreshInFlight: Promise<string | null> | null = null;

export function registerAuthenticatedSessionController(
  controller: AuthenticatedSessionController,
): () => void {
  activeController = controller;
  refreshInFlight = null;

  return () => {
    if (activeController === controller) {
      activeController = null;
      refreshInFlight = null;
    }
  };
}

export function getCurrentAccessToken(): string | null {
  return activeController?.getAccessToken() ?? null;
}

export function refreshAccessTokenOnce(): Promise<string | null> {
  if (refreshInFlight) return refreshInFlight;
  if (!activeController) return Promise.resolve(null);

  const controller = activeController;
  const trackedRefresh = controller.refreshAccessToken().finally(() => {
    if (refreshInFlight === trackedRefresh) refreshInFlight = null;
  });
  refreshInFlight = trackedRefresh;
  return trackedRefresh;
}

export function invalidateAuthenticatedSession(): void {
  activeController?.invalidateSession();
}
