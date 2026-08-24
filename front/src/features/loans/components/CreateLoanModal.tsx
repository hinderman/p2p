import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonModal,
  IonSelect,
  IonSelectOption,
  IonSpinner,
  IonTitle,
  IonToolbar,
} from '@ionic/react';
import { alertCircleOutline, closeOutline, informationCircleOutline } from 'ionicons/icons';
import { useState, type FormEvent } from 'react';

import { ApiError } from '../../../core/http/apiClient';
import { createLoan } from '../api/loansApi';
import {
  defaultCreateLoanForm,
  minimumDueDate,
  toCreateLoanInput,
  validateCreateLoanForm,
  type CreateLoanFormErrors,
  type CreateLoanFormValues,
} from '../model/createLoanForm';
import type { CreatedLoan } from '../model/loan.types';

type CreateLoanModalProps = {
  isOpen: boolean;
  onCreated: (loan: CreatedLoan) => void;
  onDismiss: () => void;
};

function resolvedTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

function creationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError) || error.status === 0) {
    return 'No fue posible conectar con el servidor. Revisa tu conexión e inténtalo nuevamente.';
  }
  if (error.status === 403) return 'Tu cuenta no tiene permiso para crear préstamos.';
  if (error.status === 422) return 'Las condiciones no cumplen una regla financiera. Revísalas e inténtalo nuevamente.';
  if (error.status >= 500) return 'El servicio no está disponible en este momento.';
  return 'No fue posible crear el préstamo. Revisa la información ingresada.';
}

function backendFieldErrors(error: ApiError): CreateLoanFormErrors {
  const mapping: Record<string, keyof CreateLoanFormValues> = {
    payerEmail: 'payerEmail',
    'originalPrincipal.amount': 'principalAmount',
    'originalPrincipal.currency': 'currency',
    interestRatePercentage: 'interestRatePercentage',
    installmentCount: 'installmentCount',
    firstDueDate: 'firstDueDate',
    'paymentSchedule.intervalDays': 'intervalDays',
    'paymentSchedule.daysOfMonth': 'daysOfMonth',
  };
  return Object.keys(error.violations).reduce<CreateLoanFormErrors>((errors, field) => {
    const formField = mapping[field];
    if (formField) errors[formField] = 'Revisa este valor.';
    return errors;
  }, {});
}

export function CreateLoanModal({ isOpen, onCreated, onDismiss }: CreateLoanModalProps) {
  const [values, setValues] = useState(defaultCreateLoanForm);
  const [errors, setErrors] = useState<CreateLoanFormErrors>({});
  const [requestError, setRequestError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const timeZone = resolvedTimeZone();

  function update<K extends keyof CreateLoanFormValues>(field: K, value: CreateLoanFormValues[K]) {
    setValues((current) => ({ ...current, [field]: value }));
    if (errors[field]) setErrors((current) => ({ ...current, [field]: undefined }));
  }

  function close() {
    if (isSubmitting) return;
    setValues(defaultCreateLoanForm());
    setErrors({});
    setRequestError(null);
    onDismiss();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSubmitting) return;
    const validationErrors = validateCreateLoanForm(values);
    setErrors(validationErrors);
    setRequestError(null);
    if (Object.keys(validationErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      const createdLoan = await createLoan(toCreateLoanInput(values, timeZone));
      setValues(defaultCreateLoanForm());
      onCreated(createdLoan);
      onDismiss();
    } catch (error) {
      if (error instanceof ApiError && error.code === 'validation_failed') {
        setErrors(backendFieldErrors(error));
      }
      setRequestError(creationErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  const inputClass = (field: keyof CreateLoanFormValues) => errors[field] ? 'ion-invalid ion-touched' : '';

  return (
    <IonModal className="create-loan-modal" canDismiss={!isSubmitting} isOpen={isOpen} onDidDismiss={close}>
      <IonHeader>
        <IonToolbar>
          <IonTitle>Crear préstamo</IonTitle>
          <IonButtons slot="end">
            <IonButton aria-label="Cerrar formulario" disabled={isSubmitting} onClick={close}>
              <IonIcon aria-hidden="true" icon={closeOutline} slot="icon-only" />
            </IonButton>
          </IonButtons>
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <form className="create-loan-form" noValidate onSubmit={handleSubmit}>
          <section>
            <div className="loan-form-section-heading">
              <span>1</span><div><h2>Datos principales</h2><p>Persona invitada y capital del préstamo.</p></div>
            </div>
            <div className="loan-form-grid">
              <IonInput
                className={inputClass('payerEmail')}
                disabled={isSubmitting}
                errorText={errors.payerEmail}
                fill="outline"
                label="Correo del pagador"
                labelPlacement="stacked"
                maxlength={254}
                placeholder="persona@correo.com"
                type="email"
                value={values.payerEmail}
                onIonInput={(event) => update('payerEmail', event.detail.value ?? '')}
              />
              <div className="loan-form-money">
                <IonInput
                  className={inputClass('principalAmount')}
                  disabled={isSubmitting}
                  errorText={errors.principalAmount}
                  fill="outline"
                  inputmode="decimal"
                  label="Monto"
                  labelPlacement="stacked"
                  placeholder="1000000.00"
                  value={values.principalAmount}
                  onIonInput={(event) => update('principalAmount', event.detail.value ?? '')}
                />
                <IonInput
                  className={inputClass('currency')}
                  disabled={isSubmitting}
                  errorText={errors.currency}
                  fill="outline"
                  label="Moneda"
                  labelPlacement="stacked"
                  maxlength={3}
                  value={values.currency}
                  onIonInput={(event) => update('currency', event.detail.value ?? '')}
                />
              </div>
            </div>
          </section>

          <section>
            <div className="loan-form-section-heading">
              <span>2</span><div><h2>Interés y amortización</h2><p>Condiciones utilizadas por el backend para calcular el plan.</p></div>
            </div>
            <div className="loan-form-grid three-columns">
              <IonInput
                className={inputClass('interestRatePercentage')}
                disabled={isSubmitting}
                errorText={errors.interestRatePercentage}
                fill="outline"
                helperText="Ejemplo: 2.5 equivale a 2,5 %."
                inputmode="decimal"
                label="Tasa (%)"
                labelPlacement="stacked"
                value={values.interestRatePercentage}
                onIonInput={(event) => update('interestRatePercentage', event.detail.value ?? '')}
              />
              <IonSelect fill="outline" label="Periodo de la tasa" labelPlacement="stacked" value={values.ratePeriod} onIonChange={(event) => update('ratePeriod', event.detail.value)}>
                <IonSelectOption value="DAILY">Diaria</IonSelectOption>
                <IonSelectOption value="MONTHLY_NOMINAL">Mensual nominal</IonSelectOption>
                <IonSelectOption value="MONTHLY_EFFECTIVE">Mensual efectiva</IonSelectOption>
                <IonSelectOption value="ANNUAL_NOMINAL">Anual nominal</IonSelectOption>
                <IonSelectOption value="ANNUAL_EFFECTIVE">Anual efectiva</IonSelectOption>
              </IonSelect>
              <IonSelect fill="outline" label="Cálculo de interés" labelPlacement="stacked" value={values.interestCalculationMethod} onIonChange={(event) => update('interestCalculationMethod', event.detail.value)}>
                <IonSelectOption value="SIMPLE">Simple</IonSelectOption>
                <IonSelectOption value="COMPOUND">Compuesto</IonSelectOption>
              </IonSelect>
              <IonSelect fill="outline" label="Base de días" labelPlacement="stacked" value={values.dayCountBasis} onIonChange={(event) => update('dayCountBasis', event.detail.value)}>
                <IonSelectOption value="THIRTY_360">30 / 360</IonSelectOption>
                <IonSelectOption value="ACTUAL_360">Días reales / 360</IonSelectOption>
                <IonSelectOption value="ACTUAL_365">Días reales / 365</IonSelectOption>
              </IonSelect>
              <IonSelect fill="outline" label="Amortización" labelPlacement="stacked" value={values.amortizationMethod} onIonChange={(event) => update('amortizationMethod', event.detail.value)}>
                <IonSelectOption value="FIXED_PAYMENT">Cuota fija</IonSelectOption>
                <IonSelectOption value="FIXED_PRINCIPAL">Capital fijo</IonSelectOption>
                <IonSelectOption value="INTEREST_AT_MATURITY">Interés al vencimiento</IonSelectOption>
              </IonSelect>
              <IonSelect fill="outline" label="Abono a capital" labelPlacement="stacked" value={values.capitalPrepaymentPolicy} onIonChange={(event) => update('capitalPrepaymentPolicy', event.detail.value)}>
                <IonSelectOption value="REDUCE_PAYMENT">Reducir cuota</IonSelectOption>
                <IonSelectOption value="SHORTEN_TERM">Reducir plazo</IonSelectOption>
                <IonSelectOption value="NO_RECALCULATION">No recalcular</IonSelectOption>
              </IonSelect>
            </div>
          </section>

          <section>
            <div className="loan-form-section-heading">
              <span>3</span><div><h2>Calendario de pagos</h2><p>Fechas y frecuencia de las cuotas.</p></div>
            </div>
            <div className="loan-form-grid three-columns">
              <IonInput
                className={inputClass('installmentCount')}
                disabled={isSubmitting}
                errorText={errors.installmentCount}
                fill="outline"
                inputmode="numeric"
                label="Número de cuotas"
                labelPlacement="stacked"
                min="1"
                max="600"
                type="number"
                value={values.installmentCount}
                onIonInput={(event) => update('installmentCount', event.detail.value ?? '')}
              />
              <IonInput
                className={inputClass('firstDueDate')}
                disabled={isSubmitting}
                errorText={errors.firstDueDate}
                fill="outline"
                label="Primer vencimiento"
                labelPlacement="stacked"
                min={minimumDueDate()}
                type="date"
                value={values.firstDueDate}
                onIonInput={(event) => update('firstDueDate', event.detail.value ?? '')}
              />
              <IonSelect fill="outline" label="Frecuencia" labelPlacement="stacked" value={values.frequency} onIonChange={(event) => update('frequency', event.detail.value)}>
                <IonSelectOption value="WEEKLY">Semanal</IonSelectOption>
                <IonSelectOption value="BIWEEKLY">Cada dos semanas</IonSelectOption>
                <IonSelectOption value="MONTHLY">Mensual</IonSelectOption>
                <IonSelectOption value="EVERY_N_DAYS">Cada N días</IonSelectOption>
              </IonSelect>
              {values.frequency === 'MONTHLY' && (
                <IonInput
                  className={inputClass('daysOfMonth')}
                  disabled={isSubmitting}
                  errorText={errors.daysOfMonth}
                  fill="outline"
                  helperText="Puedes usar varios: 1, 15."
                  inputmode="text"
                  label="Días del mes"
                  labelPlacement="stacked"
                  value={values.daysOfMonth}
                  onIonInput={(event) => update('daysOfMonth', event.detail.value ?? '')}
                />
              )}
              {values.frequency === 'EVERY_N_DAYS' && (
                <IonInput
                  className={inputClass('intervalDays')}
                  disabled={isSubmitting}
                  errorText={errors.intervalDays}
                  fill="outline"
                  inputmode="numeric"
                  label="Intervalo en días"
                  labelPlacement="stacked"
                  min="1"
                  type="number"
                  value={values.intervalDays}
                  onIonInput={(event) => update('intervalDays', event.detail.value ?? '')}
                />
              )}
              <IonSelect fill="outline" label="Si vence en día no hábil" labelPlacement="stacked" value={values.nonBusinessDayAdjustment} onIonChange={(event) => update('nonBusinessDayAdjustment', event.detail.value)}>
                <IonSelectOption value="NEXT_BUSINESS_DAY">Mover al siguiente hábil</IonSelectOption>
                <IonSelectOption value="PREVIOUS_BUSINESS_DAY">Mover al hábil anterior</IonSelectOption>
                <IonSelectOption value="NO_ADJUSTMENT">No ajustar</IonSelectOption>
              </IonSelect>
            </div>
            <div className="loan-time-zone">
              <IonIcon aria-hidden="true" icon={informationCircleOutline} />
              <span>El calendario se generará con la zona horaria <strong>{timeZone}</strong>.</span>
            </div>
          </section>

          {requestError && (
            <div className="loan-form-error" role="alert">
              <IonIcon aria-hidden="true" icon={alertCircleOutline} />
              <span>{requestError}</span>
            </div>
          )}

          <div className="loan-form-actions">
            <IonButton disabled={isSubmitting} fill="clear" type="button" onClick={close}>Cancelar</IonButton>
            <IonButton disabled={isSubmitting} type="submit">
              {isSubmitting && <IonSpinner aria-hidden="true" name="crescent" />}
              {isSubmitting ? 'Creando' : 'Crear y enviar invitación'}
            </IonButton>
          </div>
        </form>
      </IonContent>
    </IonModal>
  );
}
