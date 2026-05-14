import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import {
  LigneFactureRecapDto,
  RecapPaiementsReservationDto,
} from '../models/paiements-recap.model';

/**
 * Service HTTP — Récap factures + paiements d'une réservation.
 *
 * Spec API (Tour 44 Phase 1 + Tour 45 fix dette technique) :
 *   - `GET /api/hebergement/reservations/{id}/paiements-recap`
 *     → `RecapPaiementsReservationDto` (agrégats factures + paiements)
 *   - `GET /api/finance/factures/lignes-by-reservation/{id}`
 *     → `LigneFactureRecapDto[]` (vraies lignes facture pour la modale paiements)
 *
 * Utilisé par la modale "Paiements" du calendrier (menu contextuel).
 *
 * ⚠️ `hotelId` n'est jamais transmis — extraction JWT côté serveur.
 */
@Injectable({ providedIn: 'root' })
export class PaiementsRecapService {
  private readonly base = `${environment.apiUrl}/api/hebergement/reservations`;
  private readonly factureBase = `${environment.apiUrl}/api/finance/factures`;

  constructor(private readonly http: HttpClient) {}

  getRecapForReservation(
    reservationId: number,
  ): Observable<RecapPaiementsReservationDto> {
    return this.http
      .get<ApiResponse<RecapPaiementsReservationDto>>(
        `${this.base}/${reservationId}/paiements-recap`,
      )
      .pipe(map((r) => r.data as RecapPaiementsReservationDto));
  }

  /**
   * Tour 45 (fix dette technique) — récupère les vraies lignes facture liées
   * à une réservation. Remplace le proxy `factureId → ligneFactureId` côté
   * front. Le `POST /paiement-lignes` utilise désormais le vrai
   * `ligneFactureId` de chaque ligne.
   */
  getLignesByReservation(
    reservationId: number,
  ): Observable<LigneFactureRecapDto[]> {
    return this.http
      .get<ApiResponse<LigneFactureRecapDto[]>>(
        `${this.factureBase}/lignes-by-reservation/${reservationId}`,
      )
      .pipe(map((r) => r.data as LigneFactureRecapDto[]));
  }
}
