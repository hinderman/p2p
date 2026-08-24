import { IonApp, setupIonicReact } from '@ionic/react';

import { AuthProvider } from '../features/auth';
import { GlobalHttpFeedback } from './feedback/GlobalHttpFeedback';
import { AppRouter } from './router/AppRouter';
import './styles/ionic.css';

setupIonicReact();

export function App() {
  return (
    <IonApp>
      <AuthProvider>
        <AppRouter />
        <GlobalHttpFeedback />
      </AuthProvider>
    </IonApp>
  );
}
