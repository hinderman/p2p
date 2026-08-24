import { IonIcon } from '@ionic/react';
import { arrowForwardOutline, shieldCheckmarkOutline } from 'ionicons/icons';

import { AppPage } from '../../../shared/ui/AppPage';
import './HomePage.css';

export function HomePage() {
  return (
    <AppPage title="Inicio">
      <section className="home-page">
        <span className="home-page-icon" aria-hidden="true">
          <IonIcon icon={shieldCheckmarkOutline} />
        </span>
        <p className="home-page-eyebrow">Sesión protegida</p>
        <h1>Bienvenido a tu panel</h1>
        <p className="home-page-description">
          Usa la navegación para acceder a las opciones disponibles para tu cuenta.
        </p>
        <div className="home-page-hint">
          <IonIcon aria-hidden="true" icon={arrowForwardOutline} />
          <span>Las operaciones serán validadas siempre por el backend.</span>
        </div>
      </section>
    </AppPage>
  );
}
