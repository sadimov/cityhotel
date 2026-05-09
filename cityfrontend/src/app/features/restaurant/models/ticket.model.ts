/**
 * Tickets d'impression — module restaurant POS.
 *
 * Distinction métier (cf. `prompt_restaurant_pos.txt`, §5) :
 *  - Ticket caisse : reçu client (montant, mode paiement, lignes, total).
 *  - Ticket cuisine : bon de fabrication envoyé en cuisine (plat, qté,
 *    notes — pas de prix).
 *
 * Le backend retourne un `TicketDto` contenant un PDF en base64 + métadonnées.
 * Le composant POS choisit ensuite l'action (impression directe via
 * `window.print` sur l'iframe, ou téléchargement via `Blob`).
 */

export enum TypeTicket {
  CAISSE = 'CAISSE',
  CUISINE = 'CUISINE',
}

export interface TicketDto {
  ticketId: number;
  commandeId: number;
  typeTicket: TypeTicket;
  /** PDF encodé en base64 (sans préfixe `data:`). */
  pdfBase64: string;
  /** Nom de fichier suggéré (ex. `ticket-caisse-CMD-2026-MR-000123.pdf`). */
  filename: string;
  /** ISO date-time. */
  dateImpression: string;
  /** Numéro de réimpression (1 = original). */
  reimpressionNumero?: number;
}
