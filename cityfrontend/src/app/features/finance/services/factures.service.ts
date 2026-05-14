import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { FactureCreateDto, FactureDto } from '../models/facture.model';
import { Page, PageRequest } from '../models/page.model';

/**
 * Service HTTP — Factures (module finance).
 *
 * Spec FROZEN — alignée sur `FactureController` côté backend (audit
 * B1+B2+B3, 2026-05-08) :
 *   - GET    /api/finance/factures/{id}                          → FactureDto
 *   - GET    /api/finance/factures                               → Page<FactureDto>
 *   - POST   /api/finance/factures                               → FactureDto (201)
 *   - POST   /api/finance/factures/{id}/emettre                  → FactureDto
 *   - POST   /api/finance/factures/{id}/annuler                  → FactureDto
 *   - POST   /api/finance/factures/from-reservation/{resaId}     → FactureDto (201)
 *
 * Le backend retourne directement les DTO / `Page<T>` Spring Data — pas
 * d'enveloppe `ApiResponse<T>` côté finance (contrairement aux autres
 * features non encore alignées).
 *
 * Endpoints supprimés (n'existent pas côté back) : `/echues`, `/periode`,
 * `/numero/{n}`, `/compte/{id}`, `/statut/{s}`, `PUT /{id}`,
 * `PUT /{id}/annuler` (passé en POST), `PUT /{id}/recalculer`,
 * `POST /{id}/avoir`.
 *
 * ⚠️ `hotelId` n'est jamais transmis (JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class FacturesService {
  private readonly base = `${environment.apiUrl}/api/finance/factures`;

  constructor(private readonly http: HttpClient) {}

  findById(id: number): Observable<FactureDto> {
    return this.http.get<FactureDto>(`${this.base}/${id}`);
  }

  /**
   * Liste paginée des factures (filtre tenant côté serveur).
   *
   * `sort` : `champ,asc` ou `champ,desc` (Spring Data). Multi-tri possible
   * en passant un tableau.
   */
  page(req: PageRequest = {}): Observable<Page<FactureDto>> {
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
    return this.http.get<Page<FactureDto>>(this.base, { params });
  }

  create(dto: FactureCreateDto): Observable<FactureDto> {
    return this.http.post<FactureDto>(this.base, dto);
  }

  emettre(id: number): Observable<FactureDto> {
    return this.http.post<FactureDto>(`${this.base}/${id}/emettre`, {});
  }

  annuler(id: number): Observable<FactureDto> {
    return this.http.post<FactureDto>(`${this.base}/${id}/annuler`, {});
  }

  /**
   * Génère une facture à partir d'une réservation existante : les nuitées
   * sont automatiquement transformées en lignes de facture côté backend.
   */
  fromReservation(reservationId: number): Observable<FactureDto> {
    return this.http.post<FactureDto>(
      `${this.base}/from-reservation/${reservationId}`,
      {},
    );
  }

  /**
   * Télécharge la facture au format PDF (B7, 2026-05-08).
   *
   * Endpoint back : GET /api/finance/factures/{id}/pdf (Content-Type: pdf).
   * Le composant appelant orchestre le download navigateur via
   * `FileDownloadUtil.saveBlob`.
   */
  downloadPdf(factureId: number): Observable<Blob> {
    return this.http.get(`${this.base}/${factureId}/pdf`, {
      responseType: 'blob',
    });
  }
}
