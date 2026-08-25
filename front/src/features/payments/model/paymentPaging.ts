import type { PaymentPage } from './payment.types';

export const PAGE_SIZE_OPTIONS = [5, 10, 25] as const;

export const DEFAULT_PAGE_SIZE = 5;

export function emptyPaymentPage(size: number): PaymentPage {
  return { content: [], page: 0, size, totalElements: 0, totalPages: 0, hasNext: false };
}

/**
 * Resolves the page to request after a server response, or `null` when the one
 * received is fine.
 *
 * <p>Approving the last payment of the last page empties it while the client is
 * still standing on that index. The correction always moves backwards, so it
 * cannot loop.
 */
export function correctedPage(page: PaymentPage): number | null {
  if (page.content.length > 0 || page.page === 0) return null;
  return Math.max(0, Math.min(page.page - 1, page.totalPages - 1));
}

/** 1-based positions of the visible elements inside the whole collection. */
export function visibleRange(page: PaymentPage): { first: number; last: number } {
  if (page.content.length === 0) return { first: 0, last: 0 };
  const first = page.page * page.size + 1;
  return { first, last: first + page.content.length - 1 };
}
