import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../shared/models/api.model';
import { Page, PageRequest } from '../../finance/models/page.model';
import {
  DeclarationTvaCreateDto,
  DeclarationTvaDto,
  TauxTvaConfigDto,
  TauxTvaConfigUpdateDto,
  TypeServiceTva,
} from '../models/tva.model';

/**
 * Service HTTP — TVA (config + déclarations, B7).
 *
 * Endpoints back :
 *   GET   /api/finance/tva/config
 *   GET   /api/finance/tva/config/{typeService}
 *   PUT   /api/finance/tva/config/{typeService}      body={taux, actif?, libelle?}
 *
 *   POST  /api/finance/tva/declarations              body={dateDebut, dateFin}
 *   GET   /api/finance/tva/declarations              paginé
 *   GET   /api/finance/tva/declarations/{id}
 *   GET   /api/finance/tva/declarations/periode?dateDebut=&dateFin=
 *   POST  /api/finance/tva/declarations/{id}/valider
 */
@Injectable({ providedIn: 'root' })
export class TvaService {
  private readonly baseConfig = `${environment.apiUrl}/api/finance/tva/config`;
  private readonly baseDeclarations = `${environment.apiUrl}/api/finance/tva/declarations`;

  constructor(private readonly http: HttpClient) {}

  // ⚠️ `ApiResponseBodyAdvice` wrappe TOUTES les réponses controller en
  // `{data, status, ...}`. On unwrap systématiquement via
  // `.pipe(map(r => r.data ?? r))` avec fallback pour compat ascendante.

  // ===== Configuration TVA =====

  listConfig(): Observable<TauxTvaConfigDto[]> {
    return this.http
      .get<ApiResponse<TauxTvaConfigDto[]>>(this.baseConfig)
      .pipe(map((r) => (r?.data ?? (r as unknown as TauxTvaConfigDto[]))));
  }

  getConfig(typeService: TypeServiceTva): Observable<TauxTvaConfigDto> {
    return this.http
      .get<ApiResponse<TauxTvaConfigDto>>(`${this.baseConfig}/${typeService}`)
      .pipe(map((r) => (r?.data ?? (r as unknown as TauxTvaConfigDto))));
  }

  updateConfig(
    typeService: TypeServiceTva,
    dto: TauxTvaConfigUpdateDto,
  ): Observable<TauxTvaConfigDto> {
    return this.http
      .put<ApiResponse<TauxTvaConfigDto>>(
        `${this.baseConfig}/${typeService}`,
        dto,
      )
      .pipe(map((r) => (r?.data ?? (r as unknown as TauxTvaConfigDto))));
  }

  // ===== Déclarations TVA =====

  pageDeclarations(req: PageRequest = {}): Observable<Page<DeclarationTvaDto>> {
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
    return this.http
      .get<ApiResponse<Page<DeclarationTvaDto>>>(this.baseDeclarations, {
        params,
      })
      .pipe(map((r) => (r?.data ?? (r as unknown as Page<DeclarationTvaDto>))));
  }

  findDeclaration(id: number): Observable<DeclarationTvaDto> {
    return this.http
      .get<ApiResponse<DeclarationTvaDto>>(`${this.baseDeclarations}/${id}`)
      .pipe(map((r) => (r?.data ?? (r as unknown as DeclarationTvaDto))));
  }

  findDeclarationByPeriode(
    dateDebut: string,
    dateFin: string,
  ): Observable<DeclarationTvaDto> {
    const params = new HttpParams()
      .set('dateDebut', dateDebut)
      .set('dateFin', dateFin);
    return this.http
      .get<ApiResponse<DeclarationTvaDto>>(
        `${this.baseDeclarations}/periode`,
        { params },
      )
      .pipe(map((r) => (r?.data ?? (r as unknown as DeclarationTvaDto))));
  }

  createDeclaration(
    dto: DeclarationTvaCreateDto,
  ): Observable<DeclarationTvaDto> {
    return this.http
      .post<ApiResponse<DeclarationTvaDto>>(this.baseDeclarations, dto)
      .pipe(map((r) => (r?.data ?? (r as unknown as DeclarationTvaDto))));
  }

  validerDeclaration(id: number): Observable<DeclarationTvaDto> {
    return this.http
      .post<ApiResponse<DeclarationTvaDto>>(
        `${this.baseDeclarations}/${id}/valider`,
        {},
      )
      .pipe(map((r) => (r?.data ?? (r as unknown as DeclarationTvaDto))));
  }
}
