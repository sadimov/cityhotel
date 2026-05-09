import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { DisponibiliteChambreDto } from '../models/disponibilite.model';
import {
  CreerReservationRequest,
  FiltresReservations,
  ModifierReservationRequest,
  Reservation,
} from '../models/reservation.model';

/**
 * Service HTTP — Réservations.
 *
 * Spec API FROZEN (Tour audit hebergement B1+B2, 2026-05-07) :
 *   - `GET    /api/hebergement/reservations`                         — page + filtres
 *   - `GET    /api/hebergement/reservations/{id}`                    — read
 *   - `POST   /api/hebergement/reservations`                         — create
 *   - `PUT    /api/hebergement/reservations/{id}`                    — update
 *   - `POST   /api/hebergement/reservations/{id}/check-in`           — transition
 *   - `POST   /api/hebergement/reservations/{id}/check-out`          — transition
 *   - `POST   /api/hebergement/reservations/{id}/cancel`             — transition
 *   - `POST   /api/hebergement/reservations/rechercher-disponibilite`
 *   - `GET    /api/hebergement/reservations/arrivees-today`
 *   - `GET    /api/hebergement/reservations/departs-today`
 *   - `GET    /api/hebergement/reservations/en-cours`
 *   - `GET    /api/hebergement/reservations/check-ins-retard`
 *   - `GET    /api/hebergement/reservations/rechercher?terme=...`
 *
 * ⚠️ `hotelId`, `userId` jamais transmis — JWT côté serveur.
 */
@Injectable({ providedIn: 'root' })
export class ReservationsService {
  private readonly base = `${environment.apiUrl}/api/hebergement/reservations`;

  constructor(private readonly http: HttpClient) {}

  findById(reservationId: number): Observable<Reservation> {
    return this.http
      .get<ApiResponse<Reservation>>(`${this.base}/${reservationId}`)
      .pipe(map((r) => r.data as Reservation));
  }

  /**
   * Liste paginée + filtres (filtre tenant côté serveur).
   */
  page(
    filtres: FiltresReservations = {},
    page = 0,
    size = 10,
    sortBy = 'dateCreation', // TODO[B1B2] backend may only accept `createdAt` — to revisit once backend tour B1+B2 lands
    sortDir: 'asc' | 'desc' = 'desc',
  ): Observable<PageResponse<Reservation>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sort', `${sortBy},${sortDir}`);
    if (filtres.dateArriveeDebut) params = params.set('dateArriveeDebut', filtres.dateArriveeDebut);
    if (filtres.dateArriveeFin) params = params.set('dateArriveeFin', filtres.dateArriveeFin);
    if (filtres.statut) params = params.set('statut', filtres.statut);
    if (filtres.clientId != null) params = params.set('clientId', String(filtres.clientId));
    if (filtres.societeId != null) params = params.set('societeId', String(filtres.societeId));
    if (filtres.typeId != null) params = params.set('typeId', String(filtres.typeId));
    if (filtres.montantMin != null) params = params.set('montantMin', String(filtres.montantMin));
    if (filtres.montantMax != null) params = params.set('montantMax', String(filtres.montantMax));
    if (filtres.motifSejour) params = params.set('motifSejour', filtres.motifSejour);
    return this.http
      .get<ApiResponse<PageResponse<Reservation>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<Reservation>));
  }

  /** Recherche full-text serveur (numéro, nom client...). */
  rechercher(
    terme: string,
    page = 0,
    size = 10,
  ): Observable<PageResponse<Reservation>> {
    const params = new HttpParams()
      .set('terme', terme)
      .set('page', String(page))
      .set('size', String(size));
    return this.http
      .get<ApiResponse<PageResponse<Reservation>>>(`${this.base}/rechercher`, { params })
      .pipe(map((r) => r.data as PageResponse<Reservation>));
  }

  create(dto: CreerReservationRequest): Observable<Reservation> {
    return this.http
      .post<ApiResponse<Reservation>>(this.base, dto)
      .pipe(map((r) => r.data as Reservation));
  }

  update(
    reservationId: number,
    dto: ModifierReservationRequest,
  ): Observable<Reservation> {
    return this.http
      .put<ApiResponse<Reservation>>(`${this.base}/${reservationId}`, dto)
      .pipe(map((r) => r.data as Reservation));
  }

  // ────────────────────────────────────────────────────────────────────────
  // Actions de cycle de vie (POST — transitions d'état)
  // ────────────────────────────────────────────────────────────────────────

  checkIn(reservationId: number): Observable<Reservation> {
    return this.http
      .post<ApiResponse<Reservation>>(`${this.base}/${reservationId}/check-in`, {})
      .pipe(map((r) => r.data as Reservation));
  }

  checkOut(reservationId: number): Observable<Reservation> {
    return this.http
      .post<ApiResponse<Reservation>>(`${this.base}/${reservationId}/check-out`, {})
      .pipe(map((r) => r.data as Reservation));
  }

  /**
   * Annule la réservation. La méthode TS conserve son nom `annuler` (cohérence
   * avec les composants existants) ; seule l'URL backend bascule sur
   * `/cancel` (anglais, conforme à la spec FROZEN).
   */
  annuler(reservationId: number, motif: string): Observable<Reservation> {
    return this.http
      .post<ApiResponse<Reservation>>(
        `${this.base}/${reservationId}/cancel`,
        { motif },
      )
      .pipe(map((r) => r.data as Reservation));
  }

  // ────────────────────────────────────────────────────────────────────────
  // Vues de pilotage (réception / night audit)
  // ────────────────────────────────────────────────────────────────────────

  arriveesToday(): Observable<Reservation[]> {
    return this.http
      .get<ApiResponse<Reservation[]>>(`${this.base}/arrivees-today`)
      .pipe(map((r) => r.data ?? []));
  }

  departsToday(): Observable<Reservation[]> {
    return this.http
      .get<ApiResponse<Reservation[]>>(`${this.base}/departs-today`)
      .pipe(map((r) => r.data ?? []));
  }

  enCours(): Observable<Reservation[]> {
    return this.http
      .get<ApiResponse<Reservation[]>>(`${this.base}/en-cours`)
      .pipe(map((r) => r.data ?? []));
  }

  checkInsEnRetard(): Observable<Reservation[]> {
    return this.http
      .get<ApiResponse<Reservation[]>>(`${this.base}/check-ins-retard`)
      .pipe(map((r) => r.data ?? []));
  }

  /**
   * Recherche de disponibilité — utilisé par le formulaire de création.
   */
  rechercherDisponibilite(
    request: import('../models/reservation.model').RechercheDisponibiliteRequest,
  ): Observable<DisponibiliteChambreDto> {
    return this.http
      .post<ApiResponse<DisponibiliteChambreDto>>(
        `${this.base}/rechercher-disponibilite`,
        request,
      )
      .pipe(map((r) => r.data as DisponibiliteChambreDto));
  }

  // ────────────────────────────────────────────────────────────────────────
  // NOTE : `facturerNuitees(id)` retiré (Tour audit B1+B2, 2026-05-07).
  // Cette transition appartient au module `finance` (POST /reservations/{id}
  // /facturer-nuitees), hors scope hebergement V1. Sera réintroduit lors
  // du Tour finance avec le flux complet "facture + lignes" associé.
  // ────────────────────────────────────────────────────────────────────────
}
