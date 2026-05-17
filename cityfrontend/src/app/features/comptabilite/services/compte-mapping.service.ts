import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../shared/models/api.model';
import {
  CompteMappingDto,
  CompteMappingUpdateDto,
  TypeEvenementComptable,
} from '../models/compte-mapping.model';

/**
 * Service HTTP — Mapping comptable (B7).
 *
 * Endpoints back :
 *   GET /api/finance/compte-mapping
 *   PUT /api/finance/compte-mapping/{typeEvenement}   body={compteCode}
 *
 * Le back retourne la liste exhaustive des types (avec defauts codes pour
 * les mappings non personnalises — flag `defaut=true`).
 */
@Injectable({ providedIn: 'root' })
export class CompteMappingService {
  private readonly base = `${environment.apiUrl}/api/finance/compte-mapping`;

  constructor(private readonly http: HttpClient) {}

  // ⚠️ `ApiResponseBodyAdvice` wrappe TOUTES les réponses controller en
  // `{data, status, ...}`. On unwrap via `.pipe(map)` avec fallback
  // `r.data ?? r` pour compat ascendante.

  list(): Observable<CompteMappingDto[]> {
    return this.http
      .get<ApiResponse<CompteMappingDto[]>>(this.base)
      .pipe(map((r) => (r?.data ?? (r as unknown as CompteMappingDto[]))));
  }

  update(
    typeEvenement: TypeEvenementComptable,
    dto: CompteMappingUpdateDto,
  ): Observable<CompteMappingDto> {
    return this.http
      .put<ApiResponse<CompteMappingDto>>(`${this.base}/${typeEvenement}`, dto)
      .pipe(map((r) => (r?.data ?? (r as unknown as CompteMappingDto))));
  }
}
