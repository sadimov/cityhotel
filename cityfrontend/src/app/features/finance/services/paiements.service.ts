import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { Page, PageRequest } from '../models/page.model';
import {
  AffectationCreateDto,
  PaiementCreateDto,
  PaiementDto,
} from '../models/paiement.model';

/**
 * Service HTTP — Paiements (module finance).
 *
 * Spec FROZEN — alignée sur `PaiementController` côté backend (audit
 * B1+B2+B3, 2026-05-08) :
 *   - GET    /api/finance/paiements/{id}                  → PaiementDto
 *   - GET    /api/finance/paiements                       → Page<PaiementDto>
 *   - POST   /api/finance/paiements                       → PaiementDto (201)
 *   - POST   /api/finance/paiements/{id}/affecter         → PaiementDto
 *   - POST   /api/finance/paiements/{id}/annuler          → PaiementDto
 *
 * Endpoints supprimés (inexistants côté back) : `/compte/{compteId}`,
 * filtres ad-hoc (search/dateDebut/dateFin/modePaiement/statut/factureId/
 * montantMin/montantMax). La pagination est paramétrée via `Pageable`
 * Spring Data (`page`, `size`, `sort`).
 *
 * ⚠️ `hotelId` n'est jamais transmis (JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class PaiementsService {
  private readonly base = `${environment.apiUrl}/api/finance/paiements`;

  constructor(private readonly http: HttpClient) {}

  findById(id: number): Observable<PaiementDto> {
    return this.http.get<PaiementDto>(`${this.base}/${id}`);
  }

  /**
   * Liste paginée des paiements (filtre tenant côté serveur).
   */
  page(req: PageRequest = {}): Observable<Page<PaiementDto>> {
    let params = new HttpParams();
    if (req.page != null) params = params.set('page', String(req.page));
    if (req.size != null) params = params.set('size', String(req.size));
    if (req.sort) {
      if (Array.isArray(req.sort)) {
        for (const s of req.sort) params = params.append('sort', s);
      } else {
        params = params.set('sort', req.sort);
      }
    }
    return this.http.get<Page<PaiementDto>>(this.base, { params });
  }

  create(dto: PaiementCreateDto): Observable<PaiementDto> {
    return this.http.post<PaiementDto>(this.base, dto);
  }

  /**
   * Ventile un paiement existant à plusieurs factures.
   */
  affecter(
    paiementId: number,
    affectations: AffectationCreateDto[],
  ): Observable<PaiementDto> {
    return this.http.post<PaiementDto>(
      `${this.base}/${paiementId}/affecter`,
      affectations,
    );
  }

  annuler(id: number): Observable<PaiementDto> {
    return this.http.post<PaiementDto>(`${this.base}/${id}/annuler`, {});
  }
}
