import { IonIcon } from '@ionic/react';
import type { CSSProperties } from 'react';
import { NavLink } from 'react-router-dom';

import { useAuth } from '../../features/auth';
import { getNavigationItems } from '../navigation/appNavigation';

export function MobileNavigation() {
  const { session } = useAuth();
  const roles = session?.roles ?? [];
  const navigationItems = getNavigationItems(roles);
  const style = {
    gridTemplateColumns: `repeat(${navigationItems.length}, minmax(0, 1fr))`,
  } satisfies CSSProperties;

  return (
    <nav className="mobile-navigation" aria-label="Navegación principal" style={style}>
      {navigationItems.map((item) => (
        <NavLink
          className={({ isActive }) => (isActive ? 'mobile-navigation-item active' : 'mobile-navigation-item')}
          end={item.path === '/app'}
          key={item.id}
          to={item.path}
        >
          <IonIcon aria-hidden="true" icon={item.icon} />
          <span>{item.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
