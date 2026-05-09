import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { TicketDto } from '../models/ticket.model';

/**
 * Service HTTP — Tickets d'impression POS.
 *
 * Le backend retourne le PDF en base64 (champ `pdfBase64`). Le composant
 * appelant peut ensuite :
 *  - ouvrir une iframe avec `data:application/pdf;base64,...` pour
 *    déclencher `window.print()`.
 *  - convertir en Blob pour `download` HTML5.
 *
 * Spec API :
 *   - `POST /api/restaurant/commandes/{id}/tickets/caisse`     — édite un ticket caisse
 *   - `POST /api/restaurant/commandes/{id}/tickets/cuisine`    — édite un ticket cuisine
 *   - `POST /api/restaurant/commandes/{id}/tickets/{ticketId}/reimprimer`
 */
@Injectable({ providedIn: 'root' })
export class TicketsService {
  private readonly base = `${environment.apiUrl}/api/restaurant/commandes`;

  constructor(private readonly http: HttpClient) {}

  imprimerCaisse(commandeId: number): Observable<TicketDto> {
    return this.http
      .post<ApiResponse<TicketDto>>(
        `${this.base}/${commandeId}/tickets/caisse`,
        {},
      )
      .pipe(map((r) => r.data as TicketDto));
  }

  imprimerCuisine(commandeId: number): Observable<TicketDto> {
    return this.http
      .post<ApiResponse<TicketDto>>(
        `${this.base}/${commandeId}/tickets/cuisine`,
        {},
      )
      .pipe(map((r) => r.data as TicketDto));
  }

  reimprimer(commandeId: number, ticketId: number): Observable<TicketDto> {
    return this.http
      .post<ApiResponse<TicketDto>>(
        `${this.base}/${commandeId}/tickets/${ticketId}/reimprimer`,
        {},
      )
      .pipe(map((r) => r.data as TicketDto));
  }
}
