import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import {
  BonCommande,
  FiltresBonsCommande,
  LigneBonCommande,
  ReceptionMarchandise,
  StatutBonCommande,
} from '../models/bon-commande.model';

/**
 * Service HTTP — Bons de commande (module inventory).
 *
 * Spec FROZEN :
 *   - GET    /api/inventory/bons-commande                       — page + filtres
 *   - GET    /api/inventory/bons-commande/en-attente-reception  — vue pilotage
 *   - GET    /api/inventory/bons-commande/{id}                  — read
 *   - POST   /api/inventory/bons-commande                       — create
 *   - PUT    /api/inventory/bons-commande/{id}                  — update
 *   - POST   /api/inventory/bons-commande/{id}/lignes           — ajouter ligne
 *   - PUT    /api/inventory/bons-commande/{id}/lignes/{ligneId} — modifier ligne
 *   - DELETE /api/inventory/bons-commande/{id}/lignes/{ligneId} — supprimer ligne
 *   - PUT    /api/inventory/bons-commande/{id}/statut?statut=...— transition
 *   - POST   /api/inventory/bons-commande/{id}/reception        — réception
 *
 * ⚠️ `hotelId`, `userId` jamais transmis (JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class BonsCommandeService {
  private readonly base = `${environment.apiUrl}/api/inventory/bons-commande`;

  constructor(private readonly http: HttpClient) {}

  page(
    filtres: FiltresBonsCommande = {},
    page = 0,
    size = 10,
    sortBy = 'dateCreation',
    sortDir: 'asc' | 'desc' = 'desc',
  ): Observable<PageResponse<BonCommande>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.statut) params = params.set('statut', filtres.statut);
    if (filtres.fournisseurId != null) {
      params = params.set('fournisseurId', String(filtres.fournisseurId));
    }
    if (filtres.dateDebut) params = params.set('dateDebut', filtres.dateDebut);
    if (filtres.dateFin) params = params.set('dateFin', filtres.dateFin);

    return this.http
      .get<ApiResponse<PageResponse<BonCommande>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<BonCommande>));
  }

  findEnAttenteReception(): Observable<BonCommande[]> {
    return this.http
      .get<ApiResponse<BonCommande[]>>(`${this.base}/en-attente-reception`)
      .pipe(map((r) => r.data ?? []));
  }

  findById(id: number): Observable<BonCommande> {
    return this.http
      .get<ApiResponse<BonCommande>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as BonCommande));
  }

  create(dto: BonCommande): Observable<BonCommande> {
    return this.http
      .post<ApiResponse<BonCommande>>(this.base, dto)
      .pipe(map((r) => r.data as BonCommande));
  }

  update(id: number, dto: BonCommande): Observable<BonCommande> {
    return this.http
      .put<ApiResponse<BonCommande>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as BonCommande));
  }

  ajouterLigne(bonCommandeId: number, ligne: LigneBonCommande): Observable<BonCommande> {
    return this.http
      .post<ApiResponse<BonCommande>>(`${this.base}/${bonCommandeId}/lignes`, ligne)
      .pipe(map((r) => r.data as BonCommande));
  }

  modifierLigne(
    bonCommandeId: number,
    ligneId: number,
    ligne: LigneBonCommande,
  ): Observable<BonCommande> {
    return this.http
      .put<ApiResponse<BonCommande>>(
        `${this.base}/${bonCommandeId}/lignes/${ligneId}`,
        ligne,
      )
      .pipe(map((r) => r.data as BonCommande));
  }

  supprimerLigne(bonCommandeId: number, ligneId: number): Observable<BonCommande> {
    return this.http
      .delete<ApiResponse<BonCommande>>(
        `${this.base}/${bonCommandeId}/lignes/${ligneId}`,
      )
      .pipe(map((r) => r.data as BonCommande));
  }

  changerStatut(id: number, statut: StatutBonCommande): Observable<BonCommande> {
    const params = new HttpParams().set('statut', statut);
    return this.http
      .put<ApiResponse<BonCommande>>(`${this.base}/${id}/statut`, {}, { params })
      .pipe(map((r) => r.data as BonCommande));
  }

  /** Helper Tour 51bis : envoie un bon (brouillon -> envoye). */
  envoyer(id: number): Observable<BonCommande> {
    return this.changerStatut(id, 'envoye');
  }

  /** Helper Tour 51bis : confirme un bon (envoye -> confirme). */
  confirmer(id: number): Observable<BonCommande> {
    return this.changerStatut(id, 'confirme');
  }

  /** Helper Tour 51bis : annule un bon. */
  annuler(id: number): Observable<BonCommande> {
    return this.changerStatut(id, 'annule');
  }

  receptionner(id: number, dto: ReceptionMarchandise): Observable<BonCommande> {
    return this.http
      .post<ApiResponse<BonCommande>>(`${this.base}/${id}/reception`, dto)
      .pipe(map((r) => r.data as BonCommande));
  }
}
