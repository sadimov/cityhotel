import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import {
  NuiteeModificationDto,
  NuiteeMontantUpdate,
  NuiteeMontantsUpdateResultat,
} from '../models/nuitee-modification.model';

/**
 * Service HTTP — Modification de nuitées (Tour 45 Phase B).
 *
 * Spec API (Phase A — backend) :
 *   - `GET   /api/hebergement/nuitees/reservation/{reservationId}/provisoires`
 *     → `List<NuiteeModificationDto>`
 *   - `PATCH /api/hebergement/nuitees/montants`
 *     body `List<NuiteeMontantUpdate>` → `NuiteeMontantsUpdateResultat`
 *
 * Le backend rejette (codes i18n) :
 *  - `error.nuitee.facture.payee` (la facture rattachée est PAYEE)
 *  - `error.nuitee.statut.closed` (statut ligne `C` — verrouillée)
 *
 * ⚠️ `hotelId` n'est jamais transmis — extraction JWT côté serveur.
 */
@Injectable({ providedIn: 'root' })
export class NuiteesModifService {
  private readonly base = `${environment.apiUrl}/api/hebergement/nuitees`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Liste des nuitées d'une réservation avec les références finance
   * nécessaires pour modifier les montants.
   */
  getProvisoires(reservationId: number): Observable<NuiteeModificationDto[]> {
    return this.http
      .get<ApiResponse<NuiteeModificationDto[]>>(
        `${this.base}/reservation/${reservationId}/provisoires`,
      )
      .pipe(map((r) => r.data ?? []));
  }

  /**
   * Met à jour atomique-ment les montants des nuitées sélectionnées (ligne
   * facture + opération compte). Le serveur calcule l'impact total signé.
   */
  updateMontants(
    modifications: NuiteeMontantUpdate[],
  ): Observable<NuiteeMontantsUpdateResultat> {
    return this.http
      .patch<ApiResponse<NuiteeMontantsUpdateResultat>>(
        `${this.base}/montants`,
        modifications,
      )
      .pipe(map((r) => r.data as NuiteeMontantsUpdateResultat));
  }
}
