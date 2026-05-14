import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import {
  BalanceComptableDto,
  BilanDto,
  CompteResultatDto,
  GrandLivreDto,
  JournalEditionDto,
} from '../models/etats.model';

export interface BalanceFilter {
  exerciceId?: number;
  dateDebut?: string;
  dateFin?: string;
  classe?: number;
}

export interface GrandLivreFilter {
  compteCode?: string;
  exerciceId?: number;
  dateDebut?: string;
  dateFin?: string;
}

export interface JournalFilter {
  journalId: number;
  dateDebut?: string;
  dateFin?: string;
}

/**
 * Service HTTP — États de synthèse (B7).
 *
 * Endpoints back (`/api/finance/etats/...`) :
 *   GET balance ; export/xlsx ; export/pdf
 *   GET grand-livre ; export/xlsx ; export/pdf
 *   GET journal ; export/xlsx ; export/pdf
 *   GET bilan ; export/xlsx ; export/pdf
 *   GET compte-resultat ; export/xlsx ; export/pdf
 *
 * Les exports retournent un `Blob` (Content-Type: spreadsheetml.sheet
 * ou pdf) — le composant orchestrera le téléchargement via
 * `FileDownloadUtil.saveBlob`.
 */
@Injectable({ providedIn: 'root' })
export class EtatsService {
  private readonly base = `${environment.apiUrl}/api/finance/etats`;

  constructor(private readonly http: HttpClient) {}

  // ===== Balance =====

  private balanceParams(filter: BalanceFilter): HttpParams {
    let params = new HttpParams();
    if (filter.exerciceId != null) {
      params = params.set('exerciceId', String(filter.exerciceId));
    }
    if (filter.dateDebut) params = params.set('dateDebut', filter.dateDebut);
    if (filter.dateFin) params = params.set('dateFin', filter.dateFin);
    if (filter.classe != null) {
      params = params.set('classe', String(filter.classe));
    }
    return params;
  }

  getBalance(filter: BalanceFilter): Observable<BalanceComptableDto> {
    return this.http.get<BalanceComptableDto>(`${this.base}/balance`, {
      params: this.balanceParams(filter),
    });
  }

  exportBalanceXlsx(filter: BalanceFilter): Observable<Blob> {
    return this.http.get(`${this.base}/balance/export/xlsx`, {
      params: this.balanceParams(filter),
      responseType: 'blob',
    });
  }

  exportBalancePdf(filter: BalanceFilter): Observable<Blob> {
    return this.http.get(`${this.base}/balance/export/pdf`, {
      params: this.balanceParams(filter),
      responseType: 'blob',
    });
  }

  // ===== Grand livre =====

  private grandLivreParams(filter: GrandLivreFilter): HttpParams {
    let params = new HttpParams();
    if (filter.compteCode) params = params.set('compteCode', filter.compteCode);
    if (filter.exerciceId != null) {
      params = params.set('exerciceId', String(filter.exerciceId));
    }
    if (filter.dateDebut) params = params.set('dateDebut', filter.dateDebut);
    if (filter.dateFin) params = params.set('dateFin', filter.dateFin);
    return params;
  }

  getGrandLivre(filter: GrandLivreFilter): Observable<GrandLivreDto> {
    return this.http.get<GrandLivreDto>(`${this.base}/grand-livre`, {
      params: this.grandLivreParams(filter),
    });
  }

  exportGrandLivreXlsx(filter: GrandLivreFilter): Observable<Blob> {
    return this.http.get(`${this.base}/grand-livre/export/xlsx`, {
      params: this.grandLivreParams(filter),
      responseType: 'blob',
    });
  }

  exportGrandLivrePdf(filter: GrandLivreFilter): Observable<Blob> {
    return this.http.get(`${this.base}/grand-livre/export/pdf`, {
      params: this.grandLivreParams(filter),
      responseType: 'blob',
    });
  }

  // ===== Journal édition =====

  private journalParams(filter: JournalFilter): HttpParams {
    let params = new HttpParams().set('journalId', String(filter.journalId));
    if (filter.dateDebut) params = params.set('dateDebut', filter.dateDebut);
    if (filter.dateFin) params = params.set('dateFin', filter.dateFin);
    return params;
  }

  getJournal(filter: JournalFilter): Observable<JournalEditionDto> {
    return this.http.get<JournalEditionDto>(`${this.base}/journal`, {
      params: this.journalParams(filter),
    });
  }

  exportJournalXlsx(filter: JournalFilter): Observable<Blob> {
    return this.http.get(`${this.base}/journal/export/xlsx`, {
      params: this.journalParams(filter),
      responseType: 'blob',
    });
  }

  exportJournalPdf(filter: JournalFilter): Observable<Blob> {
    return this.http.get(`${this.base}/journal/export/pdf`, {
      params: this.journalParams(filter),
      responseType: 'blob',
    });
  }

  // ===== Bilan =====

  private bilanParams(exerciceId: number, dateArrete?: string): HttpParams {
    let params = new HttpParams().set('exerciceId', String(exerciceId));
    if (dateArrete) params = params.set('dateArrete', dateArrete);
    return params;
  }

  getBilan(exerciceId: number, dateArrete?: string): Observable<BilanDto> {
    return this.http.get<BilanDto>(`${this.base}/bilan`, {
      params: this.bilanParams(exerciceId, dateArrete),
    });
  }

  exportBilanXlsx(exerciceId: number, dateArrete?: string): Observable<Blob> {
    return this.http.get(`${this.base}/bilan/export/xlsx`, {
      params: this.bilanParams(exerciceId, dateArrete),
      responseType: 'blob',
    });
  }

  exportBilanPdf(exerciceId: number, dateArrete?: string): Observable<Blob> {
    return this.http.get(`${this.base}/bilan/export/pdf`, {
      params: this.bilanParams(exerciceId, dateArrete),
      responseType: 'blob',
    });
  }

  // ===== Compte de résultat =====

  private compteResultatParams(
    exerciceId: number,
    dateDebut?: string,
    dateFin?: string,
  ): HttpParams {
    let params = new HttpParams().set('exerciceId', String(exerciceId));
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin) params = params.set('dateFin', dateFin);
    return params;
  }

  getCompteResultat(
    exerciceId: number,
    dateDebut?: string,
    dateFin?: string,
  ): Observable<CompteResultatDto> {
    return this.http.get<CompteResultatDto>(`${this.base}/compte-resultat`, {
      params: this.compteResultatParams(exerciceId, dateDebut, dateFin),
    });
  }

  exportCompteResultatXlsx(
    exerciceId: number,
    dateDebut?: string,
    dateFin?: string,
  ): Observable<Blob> {
    return this.http.get(`${this.base}/compte-resultat/export/xlsx`, {
      params: this.compteResultatParams(exerciceId, dateDebut, dateFin),
      responseType: 'blob',
    });
  }

  exportCompteResultatPdf(
    exerciceId: number,
    dateDebut?: string,
    dateFin?: string,
  ): Observable<Blob> {
    return this.http.get(`${this.base}/compte-resultat/export/pdf`, {
      params: this.compteResultatParams(exerciceId, dateDebut, dateFin),
      responseType: 'blob',
    });
  }
}
