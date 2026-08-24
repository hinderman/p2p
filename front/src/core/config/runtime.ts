function withoutTrailingSlash(value: string): string {
  return value.replace(/\/+$/, '');
}

export const runtimeConfig = {
  apiBaseUrl: withoutTrailingSlash(
    import.meta.env.VITE_API_BASE_URL?.trim() ?? '',
  ),
};
