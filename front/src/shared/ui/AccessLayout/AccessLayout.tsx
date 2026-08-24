import { IonContent, IonIcon, IonPage } from '@ionic/react';
import type { PropsWithChildren } from 'react';

import './AccessLayout.css';

type AccessLayoutProps = PropsWithChildren<{
  description: string;
  eyebrow: string;
  icon: string;
  title: string;
}>;

export function AccessLayout({
  children,
  description,
  eyebrow,
  icon,
  title,
}: AccessLayoutProps) {
  return (
    <IonPage>
      <IonContent className="access-content" fullscreen>
        <main className="access-shell">
          <section className="access-card" aria-labelledby="access-title">
            <div className="access-brand" aria-hidden="true">
              <IonIcon icon={icon} />
            </div>

            <div className="access-heading">
              <p className="access-eyebrow">{eyebrow}</p>
              <h1 id="access-title">{title}</h1>
              <p>{description}</p>
            </div>

            {children}
          </section>
        </main>
      </IonContent>
    </IonPage>
  );
}
