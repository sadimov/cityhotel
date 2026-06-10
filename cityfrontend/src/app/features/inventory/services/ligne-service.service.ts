import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';

/**
 * Service HTTP — Bridge ServiceHotelier → LigneFacture (Tour 51bis + Tour 70).
 *
 * Ajoute une ligne `SERVICE` à la facture **existante** rattachée à une
 * réservation, sans en créer une nouvelle. Cible l'endpoint dédié
 * `POST /api/finance/factures/lignes-service` (cf. `FactureController` +
 * `FactureServiceImpl.addLigneService` côté backend) qui :
 *   1. Résout la facture cible via `factureId` (prioritaire) ou
 *      `reservationId` (sélectionne la facture non terminale la plus récente).
 *   2. Crée la `LigneFacture` SERVICE avec `datePrestation = LocalDate.now()`.
 *   3. Recalcule les montants + passe un DEBIT complémentaire compte client
 *      si la facture est déjà EMISE/PARTIELLEMENT_PAYEE.
 *
 * Avant Tour 70 : ce service appelait `POST /api/finance/factures` qui créait
 * une **nouvelle facture BROUILLON** à chaque ajout — invisible dans la modale
 * "Paiements" tant qu'elle n'était pas émise, et la résa se retrouvait avec
 * plusieurs factures distinctes.
 *
 * ⚠️ `hotelId` jamais transmis (JWT côté serveur — CLAUDE.md §6.1).
 */
export interface AjouterLigneServiceRequest {
  reservationId: number;
  serviceId: number;
  quantite: number;
  /** Override prix unitaire optionnel — sinon prix du service utilisé. */
  prixUnitaire?: number;
  libelle?: string;
  /** Note : pas envoyé — backend pose LocalDate.now() (Tour 51bis). */
  datePrestation?: string;
}

export interface AjouterLigneServiceResponse {
  factureId: number;
  ligneFactureId: number;
  montantTtc: number;
}

@Injectable({ providedIn: 'root' })
export class LigneServiceService {
  private readonly endpoint = `${environment.apiUrl}/api/finance/factures/lignes-service`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Ajoute une ligne service à la facture associée à une réservation.
   *
   * Le backend (`addLigneService`) résout la facture via `reservationId` et
   * y ajoute la ligne SERVICE — pas de nouvelle facture créée.
   */
  addLigneService(req: AjouterLigneServiceRequest): Observable<AjouterLigneServiceResponse> {
    const payload = {
      serviceId: req.serviceId,
      reservationId: req.reservationId,
      quantite: req.quantite,
      prixUnitaireOverride: req.prixUnitaire ?? null,
      description: req.libelle ?? null,
      tauxTva: null,
    };
    return this.http.post<AjouterLigneServiceResponse>(this.endpoint, payload);
  }
}
