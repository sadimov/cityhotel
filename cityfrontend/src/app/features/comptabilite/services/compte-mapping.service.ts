import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
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

  list(): Observable<CompteMappingDto[]> {
    return this.http.get<CompteMappingDto[]>(this.base);
  }

  update(
    typeEvenement: TypeEvenementComptable,
    dto: CompteMappingUpdateDto,
  ): Observable<CompteMappingDto> {
    return this.http.put<CompteMappingDto>(
      `${this.base}/${typeEvenement}`,
      dto,
    );
  }
}
