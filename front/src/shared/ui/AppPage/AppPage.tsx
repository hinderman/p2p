import {
  IonButtons,
  IonContent,
  IonHeader,
  IonMenuButton,
  IonPage,
  IonTitle,
  IonToolbar,
} from '@ionic/react';
import type { PropsWithChildren } from 'react';

import './AppPage.css';

type AppPageProps = PropsWithChildren<{
  title: string;
}>;

export function AppPage({ children, title }: AppPageProps) {
  return (
    <IonPage>
      <IonHeader className="app-header" translucent>
        <IonToolbar>
          <IonButtons slot="start">
            <IonMenuButton aria-label="Abrir menú" menu="authenticated-menu" />
          </IonButtons>
          <IonTitle>{title}</IonTitle>
        </IonToolbar>
      </IonHeader>

      <IonContent className="app-page-content" fullscreen>
        <IonHeader collapse="condense">
          <IonToolbar>
            <IonTitle size="large">{title}</IonTitle>
          </IonToolbar>
        </IonHeader>
        <div className="app-page-body">{children}</div>
      </IonContent>
    </IonPage>
  );
}
