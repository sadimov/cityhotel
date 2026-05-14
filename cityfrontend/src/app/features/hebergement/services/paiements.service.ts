import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import {
  PaiementGlobalRequest,
  PaiementGlobalResultat,
  PaiementLignesRequest,
  PaiementLignesResultat,
  TransfererLignesRequest,
  TransfererLignesResultat,
} from '../models/paiement-lignes.model';

/**
 * Service HTTP — Paiement avancé par lignes (Tour 45 Phase B).
 *
 * Spec API (Phase A — backend) :
 *   - `POST /api/finance/factures/paiement-lignes`     → encaisser des lignes
 *   - `POST /api/finance/factures/transferer-lignes`   → transférer vers une
 *                                                       autre facture
 *
 * Codes d'erreur backend mappés côté UI (cf. composant calendar) :
 *  - `error.facture.transfert.payee`
 *  - `error.facture.transfert.factureSourceTerminated`
 *  - `error.facture.transfert.factureCibleTerminated`
 *
 * Bien que ces endpoints appartiennent au domaine finance, le service est
 * exposé depuis le module hebergement pour rester self-contained (la modale
 * paiements du calendrier en est le seul consommateur Tour 45). Une
 * éventuelle réutilisation depuis `features/finance` pourra déplacer le
 * fichier dans `shared/` plus tard.
 *
 * ⚠️ `hotelId` n'est jamais transmis — extraction JWT côté serveur.
 */
@Injectable({ providedIn: 'root' })
export class PaiementsService {
  private readonly base = `${environment.apiUrl}/api/finance/factures`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Encaisse un montant sur un ensemble de lignes de facture (mode partiel /
   * total / excédentaire — la logique de ventilation est côté serveur).
   */
  payerLignes(req: PaiementLignesRequest): Observable<PaiementLignesResultat> {
    return this.http
      .post<ApiResponse<PaiementLignesResultat>>(
        `${this.base}/paiement-lignes`,
        req,
      )
      .pipe(map((r) => r.data as PaiementLignesResultat));
  }

  /**
   * Transfère des lignes vers une facture cible (existante ou nouvelle si
   * `factureCibleId = -1`).
   */
  transfererLignes(
    req: TransfererLignesRequest,
  ): Observable<TransfererLignesResultat> {
    return this.http
      .post<ApiResponse<TransfererLignesResultat>>(
        `${this.base}/transferer-lignes`,
        req,
      )
      .pipe(map((r) => r.data as TransfererLignesResultat));
  }

  /**
   * Tour 46 — Encaissement global pour une réservation. Le backend défalque
   * automatiquement sur les lignes facture restantes et crédite l'excédent
   * éventuel au compte auxiliaire du client.
   */
  payerGlobal(req: PaiementGlobalRequest): Observable<PaiementGlobalResultat> {
    return this.http
      .post<ApiResponse<PaiementGlobalResultat>>(
        `${this.base}/paiement-global`,
        req,
      )
      .pipe(map((r) => r.data as PaiementGlobalResultat));
  }
}
