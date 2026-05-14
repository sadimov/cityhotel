/**
 * Modèle Event SSE Calendrier — feature `hebergement`.
 *
 * Payload reçu sur `GET /api/hebergement/reservations/events` (SSE).
 *
 * Source backend : `common/sse/CalendarEventDto.java` (Tour 44 Phase 1).
 *
 * Tour 44 Phase 2 (2026-05-11) : consommé par `CalendarLiveService` pour
 * synchroniser le calendrier en temps réel (création / mise à jour / suppression
 * d'une réservation effectuées par un autre utilisateur du même hôtel).
 *
 * Le filtrage tenant est fait par le serveur (TenantContext) — le client
 * ne reçoit QUE les events de son propre hotelId.
 */

export type CalendarEventType = 'CREATED' | 'UPDATED' | 'DELETED';

export interface CalendarEventDto {
  type: CalendarEventType;
  reservationId: number;
  hotelId: number;
  /** ISO 8601 timestamp UTC. */
  timestamp: string;
}

/** Nom des canaux SSE émis par le backend. */
export const CALENDAR_EVENT_NAMES = {
  HELLO: 'hello',
  CREATED: 'reservation.created',
  UPDATED: 'reservation.updated',
  DELETED: 'reservation.deleted',
} as const;
