import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { Nuitee } from '../models/nuitee.model';

/**
 * Service HTTP — Nuitées (read-only, spec FROZEN Tour audit B1+B2).
 *
 * Spec API FROZEN (Tour audit hebergement B1+B2, 2026-05-07) :
 *   - `GET    /api/hebergement/nuitees/reservation/{id}`         — toutes les nuitées d'une réservation
 *   - `GET    /api/hebergement/nuitees/chambre/{id}` (paginé)    — nuitées d'une chambre sur période
 *
 * Les nuitées sont créées automatiquement par le backend lors de la confirmation
 * d'une réservation et basculent en `CONSOMMEE` puis `FACTUREE` au fil du
 * séjour (cf. règle métier Night Audit, CLAUDE.md racine §6.4).
 *
 * NOTE — `facturerNuiteesReservation` retiré (Tour audit B1+B2). La transition
 * vers facture appartient au module `finance` (POST /reservations/{id}/facturer-nuitees),
 * hors scope hebergement V1 — sera réintroduit au Tour finance.
 */
@Injectable({ providedIn: 'root' })
export class NuiteesService {
  private readonly base = `${environment.apiUrl}/api/hebergement/nuitees`;

  constructor(private readonly http: HttpClient) {}

  /** Toutes les nuitées d'une réservation. */
  findByReservation(reservationId: number): Observable<Nuitee[]> {
    return this.http
      .get<ApiResponse<Nuitee[]>>(`${this.base}/reservation/${reservationId}`)
      .pipe(map((r) => r.data ?? []));
  }

  /** Nuitées d'une chambre sur une période (paginé). */
  findByChambreEtPeriode(
    chambreId: number,
    dateDebut: string,
    dateFin: string,
    page = 0,
    size = 50,
  ): Observable<PageResponse<Nuitee>> {
    const params = new HttpParams()
      .set('dateDebut', dateDebut)
      .set('dateFin', dateFin)
      .set('page', String(page))
      .set('size', String(size));
    return this.http
      .get<ApiResponse<PageResponse<Nuitee>>>(`${this.base}/chambre/${chambreId}`, { params })
      .pipe(map((r) => r.data as PageResponse<Nuitee>));
  }
}
