/**
 * Modèles — Night audit (déclenchement manuel + résultat).
 *
 * Spec API (Tour 47) :
 *   - `POST /api/hebergement/night-audit/run` (auth Bearer JWT)
 *     -> retourne `NightAuditResultDto` ci-dessous.
 *   - `GET  /api/hebergement/night-audit/notifications` (text/event-stream)
 *     -> push d'events `night-audit-alert` (T-3min) puis `night-audit-modal`
 *        (H = ouverture de la modale de lancement).
 *
 * Le `hotelId` est filtré côté backend (TenantContext) — le client ne reçoit
 * que les events de son propre hôtel.
 */

/** Résultat retourné par `POST /run`. */
export interface NightAuditResultDto {
  /** ID de l'hôtel sur lequel le night audit a tourné (info, non utilisé côté front). */
  hotelId: number;
  /** Date hôtelière du run, format `YYYY-MM-DD` (LocalDate sérialisé). */
  dateExecution: string;
  /** Nombre de réservations CONFIRMEE basculées en NO_SHOW. */
  nbReservationsMarkedNoShow: number;
  /** Nombre de nuitées manquantes générées pour les séjours ARRIVEE. */
  nbNuiteesManquantesGenerees: number;
  /** Horodatage technique de fin d'exécution (ISO 8601). */
  executedAt: string;
}

/**
 * Types d'event SSE émis par le backend night audit.
 *  - `night-audit-alert` : broadcast tenant, T-3min avant le run-cron.
 *  - `night-audit-modal` : push aux SUPERADMIN/ADMIN/NIGHTAUDIT à l'heure du
 *    run-cron, déclenche l'ouverture de la modale de lancement manuel.
 *  - `hello` : éventuel handshake — pas émis actuellement par ce canal, mais
 *    le client doit l'ignorer si jamais.
 */
export type NightAuditEventName =
  | 'night-audit-alert'
  | 'night-audit-modal'
  | 'hello';

/** Event SSE normalisé exposé par `NightAuditNotificationsService`. */
export interface NightAuditEvent {
  /** Nom de l'event SSE (champ `event:` de la frame). */
  eventName: NightAuditEventName;
  /** Type métier porté par le payload (`ALERT_3MIN`, `OPEN_LAUNCH_MODAL`, ...). */
  type: string;
  /** Message humain (fallback si l'i18n n'a pas la clé attendue). */
  message: string;
}

/** Types métier connus (constantes utilisées dans le notifier component). */
export const NIGHT_AUDIT_EVENT_TYPES = {
  ALERT_3MIN: 'ALERT_3MIN',
  OPEN_LAUNCH_MODAL: 'OPEN_LAUNCH_MODAL',
} as const;
