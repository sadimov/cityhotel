import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import {
  Commande,
  CreerCommandeRequest,
  EncaissementCommandeRequest,
  FiltresCommandes,
  ReportChambreRequest,
} from '../models/commande.model';

/**
 * Service HTTP — Commandes restaurant (POS, cuisine, suivi).
 *
 * Spec API (alignée prompt_restaurant_pos.txt + conventions cityfrontend) :
 *   - `GET    /api/restaurant/commandes`                          — page + filtres
 *   - `GET    /api/restaurant/commandes/{id}`                     — read
 *   - `POST   /api/restaurant/commandes`                          — create (BROUILLON)
 *   - `POST   /api/restaurant/commandes/{id}/valider`             — envoie en cuisine
 *   - `POST   /api/restaurant/commandes/{id}/encaisser`           — facture + paiement comptant
 *   - `POST   /api/restaurant/commandes/{id}/reporter-chambre`    — folio réservation
 *   - `POST   /api/restaurant/commandes/{id}/annuler`             — annule la commande
 *
 * ⚠️ `hotelId` jamais transmis (JWT côté serveur — CLAUDE.md §6.1).
 */
@Injectable({ providedIn: 'root' })
export class CommandesService {
  private readonly base = `${environment.apiUrl}/api/restaurant/commandes`;

  constructor(private readonly http: HttpClient) {}

  page(
    filtres: FiltresCommandes = {},
    page = 0,
    size = 10,
    sortBy = 'dateCreation',
    sortDir: 'asc' | 'desc' = 'desc',
  ): Observable<PageResponse<Commande>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.statut) {
      params = params.set('statut', filtres.statut);
    }
    if (filtres.clientId != null) {
      params = params.set('clientId', String(filtres.clientId));
    }
    if (filtres.reservationId != null) {
      params = params.set('reservationId', String(filtres.reservationId));
    }
    if (filtres.dateDebut) {
      params = params.set('dateDebut', filtres.dateDebut);
    }
    if (filtres.dateFin) {
      params = params.set('dateFin', filtres.dateFin);
    }
    return this.http
      .get<ApiResponse<PageResponse<Commande>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<Commande>));
  }

  findById(commandeId: number): Observable<Commande> {
    return this.http
      .get<ApiResponse<Commande>>(`${this.base}/${commandeId}`)
      .pipe(map((r) => r.data as Commande));
  }

  create(dto: CreerCommandeRequest): Observable<Commande> {
    return this.http
      .post<ApiResponse<Commande>>(this.base, dto)
      .pipe(map((r) => r.data as Commande));
  }

  /**
   * Valide la commande BROUILLON (envoie en cuisine, déclenche les tickets
   * cuisine côté backend).
   */
  valider(commandeId: number): Observable<Commande> {
    return this.http
      .post<ApiResponse<Commande>>(`${this.base}/${commandeId}/valider`, {})
      .pipe(map((r) => r.data as Commande));
  }

  /**
   * Encaisse la commande comptant. Le backend orchestre :
   *  1. création de la facture client + lignes
   *  2. émission de la facture
   *  3. création du paiement avec `modePaiement`
   *  4. affectation du paiement à la facture
   *  5. mise à jour `statut = ENCAISSEE`, `factureId` renseigné.
   */
  encaisserComptant(
    commandeId: number,
    dto: EncaissementCommandeRequest,
  ): Observable<Commande> {
    // Strip champs locaux non sÃ©rialisÃ©s (cf. EncaissementCommandeRequest).
    const payload = {
      modePaiement: dto.modePaiement,
      montant: dto.montant,
      referencePaiement: dto.referencePaiement,
    };
    return this.http
      .post<ApiResponse<Commande>>(
        `${this.base}/${commandeId}/encaisser`,
        payload,
      )
      .pipe(map((r) => r.data as Commande));
  }

  /**
   * Porte la commande au folio d'une réservation (facturation différée).
   * Le backend crée les lignes folio et passe la commande en
   * `statut = REPORTEE_CHAMBRE`.
   */
  reporterSurChambre(
    commandeId: number,
    dto: ReportChambreRequest,
  ): Observable<Commande> {
    return this.http
      .post<ApiResponse<Commande>>(
        `${this.base}/${commandeId}/reporter-chambre`,
        dto,
      )
      .pipe(map((r) => r.data as Commande));
  }

  annuler(commandeId: number, motif?: string): Observable<Commande> {
    return this.http
      .post<ApiResponse<Commande>>(`${this.base}/${commandeId}/annuler`, {
        motif: motif ?? null,
      })
      .pipe(map((r) => r.data as Commande));
  }
}
