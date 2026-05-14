import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { FolioDto } from '../models/folio.model';

/**
 * Service HTTP — Folio client (Tour 46).
 *
 * Spec API :
 *   - `GET /api/finance/comptes/client/{clientId}/folio?dateDebut=YYYY-MM-DD&dateFin=YYYY-MM-DD`
 *     → `FolioDto` (solde ouverture / clôture + liste opérations).
 *
 * Le folio est utilisé par la section "Liste Folio" de la modale paiements
 * (calendrier). La période demandée correspond aux dates de séjour de la
 * réservation pour laquelle on affiche la modale.
 *
 * Bien que cet endpoint appartienne au domaine finance, le service est
 * exposé depuis le module hebergement pour rester self-contained (la modale
 * paiements du calendrier en est le seul consommateur Tour 46) — même
 * justification que pour `PaiementsService` (cf. doc Tour 45 Phase B).
 *
 * ⚠️ `hotelId` n'est jamais transmis — extraction JWT côté serveur.
 */
@Injectable({ providedIn: 'root' })
export class FolioService {
  private readonly base = `${environment.apiUrl}/api/finance/comptes/client`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Récupère le folio d'un client sur une période donnée.
   *
   * @param clientId  identifiant client (compte auxiliaire).
   * @param dateDebut ISO `yyyy-MM-dd` (inclus).
   * @param dateFin   ISO `yyyy-MM-dd` (inclus).
   */
  getFolioForReservation(
    clientId: number,
    dateDebut: string,
    dateFin: string,
  ): Observable<FolioDto> {
    const params = new HttpParams()
      .set('dateDebut', dateDebut)
      .set('dateFin', dateFin);
    return this.http
      .get<ApiResponse<FolioDto>>(`${this.base}/${clientId}/folio`, { params })
      .pipe(map((r) => r.data as FolioDto));
  }
}
