import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { DashboardMenage, StatistiquesMenage } from '../models/statistiques-menage.model';

/**
 * Service HTTP — Dashboard et statistiques du module ménage.
 *
 * Spec (cf. `MENAGE/endpoints_module_menage.txt` §Dashboard Principal) :
 *   - GET /api/menage/dashboard                            — agrégat synthèse
 *   - GET /api/menage/statistiques                         — stats du jour
 *   - GET /api/menage/statistiques/periode?dateDebut&dateFin
 *   - GET /api/menage/statistiques/personnel/{personnelId}
 *   - GET /api/menage/kpi                                  — indicateurs
 *
 * Note : ce tour ne consomme que `dashboard` + `statistiques`. Les
 * graphiques (Chart.js) et les vues détaillées par personnel sont
 * différés au tour suivant.
 */
@Injectable({ providedIn: 'root' })
export class DashboardMenageService {
  private readonly base = `${environment.apiUrl}/api/menage`;

  constructor(private readonly http: HttpClient) {}

  getDashboard(): Observable<DashboardMenage> {
    return this.http
      .get<ApiResponse<DashboardMenage>>(`${this.base}/dashboard`)
      .pipe(map((r) => r.data ?? {}));
  }

  getStatistiques(): Observable<StatistiquesMenage> {
    return this.http
      .get<ApiResponse<StatistiquesMenage>>(`${this.base}/statistiques`)
      .pipe(map((r) => r.data ?? {}));
  }

  getStatistiquesPeriode(
    dateDebut: string,
    dateFin: string,
  ): Observable<StatistiquesMenage> {
    const params = new HttpParams()
      .set('dateDebut', dateDebut)
      .set('dateFin', dateFin);
    return this.http
      .get<ApiResponse<StatistiquesMenage>>(`${this.base}/statistiques/periode`, { params })
      .pipe(map((r) => r.data ?? {}));
  }
}
