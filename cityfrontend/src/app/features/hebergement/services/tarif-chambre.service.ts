import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { MontantCalculDto } from '../models/tarif-chambre.model';

/**
 * Service HTTP — Tarifs chambre / calcul de séjour.
 *
 * Spec API (Tour 44 Phase 1) :
 *   - `GET /api/hebergement/tarifs-chambre/calculer?typeChambreId=X&dateDebut=YYYY-MM-DD&dateFin=YYYY-MM-DD`
 *     → `MontantCalculDto`
 *
 * ⚠️ `hotelId` n'est jamais transmis — extraction JWT côté serveur.
 */
@Injectable({ providedIn: 'root' })
export class TarifChambreService {
  private readonly base = `${environment.apiUrl}/api/hebergement/tarifs-chambre`;

  constructor(private readonly http: HttpClient) {}

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
}
