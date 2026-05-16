import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';

export interface NotificationItem {
  type: string;
  titleKey: string;
  message: string;
  detail: string;
  icon: string;
  severity: 'info' | 'warning' | 'danger' | 'success';
  link: string;
  timestamp: string;
}

interface ApiResponse<T> {
  success?: boolean;
  data?: T;
  error?: string;
}

/**
 * Service HTTP pour les notifications dynamiques de l'utilisateur connecté.
 *
 * Appelle `GET /api/notifications` (backend Spring) qui agrège des événements
 * transverses (arrivées du jour, départs, stock critique, check-ins en retard).
 * Aucune persistance "lu/non-lu" en V1 — le compteur reflète l'état temps réel.
 */
@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly base = `${environment.apiUrl}/api/notifications`;

  constructor(private readonly http: HttpClient) {}

  /** Liste complète des notifications actives pour l'utilisateur courant. */
  list(): Observable<NotificationItem[]> {
    return this.http
      .get<ApiResponse<NotificationItem[]>>(this.base)
      .pipe(
        map((r) => r.data ?? []),
        catchError(() => of<NotificationItem[]>([])),
      );
  }

  /** Compteur de notifications non-lues (V1 = nombre total d'événements actifs). */
  unreadCount(): Observable<number> {
    return this.http
      .get<ApiResponse<{ count: number }>>(`${this.base}/unread-count`)
      .pipe(
        map((r) => r.data?.count ?? 0),
        catchError(() => of(0)),
      );
  }
}
