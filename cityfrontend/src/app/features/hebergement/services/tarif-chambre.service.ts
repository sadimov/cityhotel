import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import {
  MontantCalculDto,
  TarifChambre,
  TarifChambreCreate,
} from '../models/tarif-chambre.model';

/**
 * Service HTTP — Tarifs chambre / calcul de séjour.
 *
 * Spec API (Tour 44 Phase 1 + CRUD tarifs 2026-05-25) :
 *   - `GET    /api/hebergement/tarifs-chambre/{id}`
 *   - `GET    /api/hebergement/tarifs-chambre/by-type/{typeId}`         → List<TarifChambreDto>
 *   - `GET    /api/hebergement/tarifs-chambre/calculer?...`             → MontantCalculDto
 *   - `POST   /api/hebergement/tarifs-chambre`                          → création
 *   - `PUT    /api/hebergement/tarifs-chambre/{id}`                     → mise à jour
 *   - `DELETE /api/hebergement/tarifs-chambre/{id}`                     → suppression physique
 *
 * ⚠️ `hotelId` n'est jamais transmis — extraction JWT côté serveur
 *    (CLAUDE.md racine §6.1).
 */
@Injectable({ providedIn: 'root' })
export class TarifChambreService {
  private readonly base = `${environment.apiUrl}/api/hebergement/tarifs-chambre`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Récupère un tarif par son identifiant.
   */
  findById(id: number): Observable<TarifChambre> {
    return this.http
      .get<ApiResponse<TarifChambre>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as TarifChambre));
  }

  /**
   * Liste des tarifs (saisonniers ou permanents) attachés à un type de chambre.
   * Pas de pagination côté backend — la liste est attendue raisonnablement
   * courte (quelques saisons par type).
   */
  findByType(typeId: number): Observable<TarifChambre[]> {
    return this.http
      .get<ApiResponse<TarifChambre[]>>(`${this.base}/by-type/${typeId}`)
      .pipe(map((r) => r.data ?? []));
  }

  /**
   * Calcule le montant total d'un séjour pour un type de chambre donné, sur
   * une plage `[dateDebut, dateFin[` (jour de départ exclu — convention métier
   * cohérente avec `ReservationChambre`).
   *
   * @param typeChambreId identifiant du type de chambre
   * @param dateDebut ISO `yyyy-MM-dd`
   * @param dateFin ISO `yyyy-MM-dd` (départ exclu)
   */
  getCalcul(
    typeChambreId: number,
    dateDebut: string,
    dateFin: string,
  ): Observable<MontantCalculDto> {
    const params = new HttpParams()
      .set('typeChambreId', String(typeChambreId))
      .set('dateDebut', dateDebut)
      .set('dateFin', dateFin);
    return this.http
      .get<ApiResponse<MontantCalculDto>>(`${this.base}/calculer`, { params })
      .pipe(map((r) => r.data as MontantCalculDto));
  }

  /**
   * Crée un nouveau tarif saisonnier.
   *
   * ⚠️ Le frontend N'ENVOIE PAS `prixWeekend` (cf. consigne user 2026-05-25).
   */
  create(dto: TarifChambreCreate): Observable<TarifChambre> {
    return this.http
      .post<ApiResponse<TarifChambre>>(this.base, dto)
      .pipe(map((r) => r.data as TarifChambre));
  }

  /**
   * Met à jour un tarif existant. Même contrat que `create`.
   */
  update(id: number, dto: TarifChambreCreate): Observable<TarifChambre> {
    return this.http
      .put<ApiResponse<TarifChambre>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as TarifChambre));
  }

  /**
   * Suppression physique d'un tarif. Confirmer côté UI (SweetAlert rouge).
   */
  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }
}
