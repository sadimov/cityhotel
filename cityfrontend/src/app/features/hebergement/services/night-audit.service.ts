import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { NightAuditResultDto } from '../models/night-audit.model';

/**
 * Service HTTP — Night audit (déclenchement manuel).
 *
 * Spec API (Tour 47) :
 *   - `POST /api/hebergement/night-audit/run` (auth Bearer JWT)
 *     Rôles backend : SUPERADMIN / ADMIN / NIGHTAUDIT.
 *     Retourne `NightAuditResultDto`.
 *
 * Le canal SSE de notifications (`/notifications`) est consommé par
 * `NightAuditNotificationsService` (core/) — ce service-ci ne porte que la
 * partie HTTP transactionnelle (déclenchement du run).
 */
@Injectable({ providedIn: 'root' })
export class NightAuditService {
  private readonly base = `${environment.apiUrl}/api/hebergement/night-audit`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Lance le night audit pour le tenant courant. Réservé aux rôles
   * SUPERADMIN / ADMIN / NIGHTAUDIT (vérifié côté backend).
   */
  run(): Observable<NightAuditResultDto> {
    return this.http
      .post<ApiResponse<NightAuditResultDto>>(`${this.base}/run`, {})
      .pipe(map((r) => r.data as NightAuditResultDto));
  }
}
