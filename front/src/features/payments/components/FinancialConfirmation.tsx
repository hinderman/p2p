import { IonButton, IonContent, IonHeader, IonIcon, IonModal, IonSpinner, IonTitle, IonToolbar } from '@ionic/react';
import { alertCircleOutline } from 'ionicons/icons';
import type { PropsWithChildren } from 'react';

type FinancialConfirmationProps = PropsWithChildren<{
  confirmLabel: string;
  danger?: boolean;
  isOpen: boolean;
  isProcessing: boolean;
  title: string;
  onCancel: () => void;
  onConfirm: () => void;
}>;

export function FinancialConfirmation({
  children,
  confirmLabel,
  danger = false,
  isOpen,
  isProcessing,
  onCancel,
  onConfirm,
  title,
}: FinancialConfirmationProps) {
  return (
    <IonModal className="financial-confirmation" canDismiss={!isProcessing} isOpen={isOpen} onDidDismiss={onCancel}>
      <IonHeader>
        <IonToolbar><IonTitle>{title}</IonTitle></IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="financial-confirmation-body">
          <IonIcon aria-hidden="true" icon={alertCircleOutline} />
          <div className="financial-confirmation-copy">{children}</div>
          <div className="financial-confirmation-actions">
            <IonButton disabled={isProcessing} fill="clear" onClick={onCancel}>Cancelar</IonButton>
            <IonButton color={danger ? 'danger' : 'primary'} disabled={isProcessing} onClick={onConfirm}>
              {isProcessing && <IonSpinner aria-hidden="true" name="crescent" />}
              {isProcessing ? 'Procesando' : confirmLabel}
            </IonButton>
          </div>
        </div>
      </IonContent>
    </IonModal>
  );
}
