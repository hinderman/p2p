export type HttpFeedbackError = {
  id: number;
  status: number;
  code: string;
};

export type HttpFeedbackSnapshot = {
  pendingRequests: number;
  error: HttpFeedbackError | null;
};

type Listener = () => void;

const listeners = new Set<Listener>();
let nextErrorId = 1;
let snapshot: HttpFeedbackSnapshot = {
  pendingRequests: 0,
  error: null,
};

function updateSnapshot(nextSnapshot: HttpFeedbackSnapshot) {
  snapshot = nextSnapshot;
  listeners.forEach((listener) => listener());
}

export function subscribeToHttpFeedback(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getHttpFeedbackSnapshot(): HttpFeedbackSnapshot {
  return snapshot;
}

export function beginHttpRequest(): () => void {
  let finished = false;
  updateSnapshot({ ...snapshot, pendingRequests: snapshot.pendingRequests + 1 });

  return () => {
    if (finished) return;
    finished = true;
    updateSnapshot({
      ...snapshot,
      pendingRequests: Math.max(0, snapshot.pendingRequests - 1),
    });
  };
}

export function publishHttpError(error: Omit<HttpFeedbackError, 'id'>): void {
  updateSnapshot({
    ...snapshot,
    error: { ...error, id: nextErrorId++ },
  });
}

export function clearHttpError(): void {
  if (!snapshot.error) return;
  updateSnapshot({ ...snapshot, error: null });
}
