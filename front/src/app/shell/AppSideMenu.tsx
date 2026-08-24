import {
  IonButton,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonListHeader,
  IonMenu,
  IonMenuToggle,
  IonSpinner,
} from '@ionic/react';
import { logOutOutline, shieldCheckmarkOutline } from 'ionicons/icons';
import { useState } from 'react';
import { useLocation } from 'react-router-dom';

import {
  getNavigationItems,
  getRoleLabel,
  isNavigationItemActive,
} from '../navigation/appNavigation';
import { useAuth } from '../../features/auth';

export function AppSideMenu() {
  const { session, signOut } = useAuth();
  const location = useLocation();
  const [isSigningOut, setIsSigningOut] = useState(false);
  const roles = session?.roles ?? [];
  const navigationItems = getNavigationItems(roles);

  async function handleSignOut() {
    if (isSigningOut) return;
    setIsSigningOut(true);
    await signOut();
  }

  return (
    <IonMenu
      className="app-side-menu"
      contentId="authenticated-content"
      menuId="authenticated-menu"
      type="overlay"
    >
      <div className="app-menu-layout">
        <div className="app-menu-brand">
          <span className="app-menu-brand-mark" aria-hidden="true">
            <IonIcon icon={shieldCheckmarkOutline} />
          </span>
          <span>
            <strong>Panel financiero</strong>
            <small>Gestión de préstamos</small>
          </span>
        </div>

        <div className="app-menu-account">
          <span className="app-menu-avatar" aria-hidden="true">
            {roles.includes('LENDER') ? 'P' : 'U'}
          </span>
          <div>
            <strong>Cuenta activa</strong>
            <div className="app-menu-roles" aria-label="Roles de la cuenta">
              {roles.map((role) => (
                <span key={role}>{getRoleLabel(role)}</span>
              ))}
            </div>
          </div>
        </div>

        <IonList className="app-menu-navigation" lines="none">
          <IonListHeader>Navegación</IonListHeader>
          {navigationItems.map((item) => (
            <IonMenuToggle autoHide={false} key={item.id}>
              <IonItem
                className={isNavigationItemActive(location.pathname, item) ? 'selected' : ''}
                detail={false}
                routerDirection="none"
                routerLink={item.path}
              >
                <IonIcon aria-hidden="true" icon={item.icon} slot="start" />
                <IonLabel>{item.label}</IonLabel>
              </IonItem>
            </IonMenuToggle>
          ))}
        </IonList>

        <div className="app-menu-footer">
          <IonButton
            aria-label="Cerrar sesión"
            className="app-menu-sign-out"
            disabled={isSigningOut}
            expand="block"
            fill="clear"
            onClick={() => void handleSignOut()}
          >
            {isSigningOut ? (
              <IonSpinner aria-hidden="true" name="crescent" />
            ) : (
              <IonIcon aria-hidden="true" icon={logOutOutline} slot="start" />
            )}
            Cerrar sesión
          </IonButton>
        </div>
      </div>
    </IonMenu>
  );
}
