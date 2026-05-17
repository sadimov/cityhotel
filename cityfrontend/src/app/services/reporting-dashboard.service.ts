import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export interface CARecapDto {
  from: string;
  to: string;
  nbFactures: number;
  caEmisHt: number;
  caEmisTva: number;
  caEmisTtc: number;
  caPayeTtc: number;
  nbPaiements: number;
  montantEncaisse: number;
  devise: string;
}

export interface OccupationDto {
  date: string;
  totalChambres: number;
  chambresOccupees: number;
  tauxOccupation: number;
}

export interface DashboardDirectionDto {
  date: string;
  occupation: OccupationDto;
  caJour: CARecapDto;
  caSemaine: CARecapDto;
  nbAlertesStock: number;
  nbTachesEnCours: number;
  nbCheckInJour: number;
  nbCheckOutJour: number;
}

/**
 * Service HTTP — dashboard direction (R-DIR-001, Tour 41).
 * Fournit l'agrégat journalier nécessaire aux KPI du dashboard accueil :
 * CA jour, alertes stock, check-in/out du jour.
 */
@Injectable({ providedIn: 'root' })
export class ReportingDashboardService {
  private readonly base = `${environment.apiUrl}/api/reports/direction`;

  constructor(private readonly http: HttpClient) {}

  getDashboard(date: Date = new Date()): Observable<DashboardDirectionDto> {
    const iso = date.toISOString().slice(0, 10);
    const params = new HttpParams().set('date', iso);
    return this.http.get<DashboardDirectionDto>(`${this.base}/dashboard`, { params });
  }
}
