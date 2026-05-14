import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { Page, PageRequest } from '../../finance/models/page.model';
import {
  ContrePassationDto,
  EcritureComptableCreateDto,
  EcritureComptableDto,
} from '../models/ecriture.model';

/**
 * Service HTTP — Écritures comptables (B7).
 *
 * Endpoints back :
 *   GET   /api/finance/ecritures                            paginé
 *   GET   /api/finance/ecritures/{id}
 *   POST  /api/finance/ecritures                            body=EcritureComptableCreateDto
 *   POST  /api/finance/ecritures/{id}/contre-passer         body=ContrePassationDto
 *   GET   /api/finance/ecritures/journal/{journalId}?dateDebut=&dateFin=
 *   GET   /api/finance/ecritures/compte/{compteCode}?dateDebut=&dateFin=
 *   GET   /api/finance/ecritures/exercice/{exerciceId}
 */
@Injectable({ providedIn: 'root' })
export class EcrituresService {
  private readonly base = `${environment.apiUrl}/api/finance/ecritures`;

  constructor(private readonly http: HttpClient) {}

  findById(id: number): Observable<EcritureComptableDto> {
    return this.http.get<EcritureComptableDto>(`${this.base}/${id}`);
  }

  page(req: PageRequest = {}): Observable<Page<EcritureComptableDto>> {
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
    return this.http.get<Page<EcritureComptableDto>>(this.base, { params });
  }

  create(dto: EcritureComptableCreateDto): Observable<EcritureComptableDto> {
    return this.http.post<EcritureComptableDto>(this.base, dto);
  }

  contrePasser(
    id: number,
    dto: ContrePassationDto,
  ): Observable<EcritureComptableDto> {
    return this.http.post<EcritureComptableDto>(
      `${this.base}/${id}/contre-passer`,
      dto,
    );
  }

  findByJournal(
    journalId: number,
    dateDebut?: string,
    dateFin?: string,
  ): Observable<EcritureComptableDto[]> {
    let params = new HttpParams();
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin) params = params.set('dateFin', dateFin);
    return this.http.get<EcritureComptableDto[]>(
      `${this.base}/journal/${journalId}`,
      { params },
    );
  }

  findByCompte(
    compteCode: string,
    dateDebut?: string,
    dateFin?: string,
  ): Observable<EcritureComptableDto[]> {
    let params = new HttpParams();
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin) params = params.set('dateFin', dateFin);
    return this.http.get<EcritureComptableDto[]>(
      `${this.base}/compte/${compteCode}`,
      { params },
    );
  }

  findByExercice(exerciceId: number): Observable<EcritureComptableDto[]> {
    return this.http.get<EcritureComptableDto[]>(
      `${this.base}/exercice/${exerciceId}`,
    );
  }
}
