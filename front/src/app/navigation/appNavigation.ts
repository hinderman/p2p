import { cardOutline, homeOutline, walletOutline } from 'ionicons/icons';

export type KnownUserRole = 'LENDER' | 'PAYER';

export type AppNavigationItem = {
  id: 'home' | 'lender-workspace' | 'payer-workspace';
  icon: string;
  label: string;
  path: string;
  roles?: readonly KnownUserRole[];
};

const APP_NAVIGATION: readonly AppNavigationItem[] = [
  {
    id: 'home',
    icon: homeOutline,
    label: 'Inicio',
    path: '/app',
  },
  {
    id: 'lender-workspace',
    icon: walletOutline,
    label: 'Préstamos otorgados',
    path: '/app/lender',
    roles: ['LENDER'],
  },
  {
    id: 'payer-workspace',
    icon: cardOutline,
    label: 'Mis préstamos',
    path: '/app/payer',
    roles: ['PAYER'],
  },
] as const;

export function getNavigationItems(roles: readonly string[]): AppNavigationItem[] {
  const grantedRoles = new Set(roles);
  return APP_NAVIGATION.filter(
    (item) => !item.roles || item.roles.some((role) => grantedRoles.has(role)),
  );
}

export function getRoleLabel(role: string): string {
  if (role === 'LENDER') return 'Prestamista';
  if (role === 'PAYER') return 'Pagador';
  return role;
}

export function isNavigationItemActive(pathname: string, item: AppNavigationItem): boolean {
  return item.path === '/app'
    ? pathname === item.path
    : pathname === item.path || pathname.startsWith(`${item.path}/`);
}
