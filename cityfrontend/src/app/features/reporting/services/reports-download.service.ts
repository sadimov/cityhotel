import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, from, of, throwError } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';

/**
 * Erreur enrichie remontée par {@link ReportsDownloadService} quand le backend
 * renvoie un statut HTTP ≠ 2xx. Permet au composant d'afficher le message
 * réel (clé i18n ou texte SYSCOHADA) plutôt qu'un message générique.
 */
export interface ReportDownloadError {
  status: number;
  message: string;
  raw?: unknown;
}

/**
 * Service de téléchargement de rapports (XLSX / PDF).
 *
 * Passe par {@link HttpClient} — l'intercepteur `AuthInterceptor` ajoute
 * automatiquement le `Authorization: Bearer ...`. Un `<a href>` direct vers
 * l'URL ne le ferait pas et retomberait en 401.
 *
 * Construit l'URL via {@link environment.apiUrl} qui inclut déjà le contexte
 * `/citybackend` côté backend Spring Boot.
 *
 * <h3>Désérialisation des erreurs backend</h3>
 * Quand le backend retourne une erreur (400/401/500…), la response a un
 * statut ≠ 2xx mais le body reste un Blob (car `responseType: 'blob'`).
 * On le lit en `FileReader.readAsText` puis on tente un `JSON.parse` pour
 * extraire le message i18n (`{message, error, status}`) du
 * `GlobalExceptionHandler` Spring.
 */
@Injectable({ providedIn: 'root' })
export class ReportsDownloadService {
  constructor(private readonly http: HttpClient) {}

  /**
   * Télécharge un rapport (PDF ou XLSX) et déclenche un download navigateur.
   *
   * @param path  path absolu API (ex. `/api/reports/occupation/export.pdf`)
   * @param queryParams query string optionnelle (sera appendue à l'URL)
   * @param suggestedFilename nom de fichier proposé au navigateur
   */
  download(
    path: string,
    suggestedFilename?: string,
    queryParams?: Record<string, string>,
  ): Observable<void> {
    const qs = queryParams && Object.keys(queryParams).length > 0
      ? '?' + new URLSearchParams(queryParams).toString()
      : '';
    const url = `${environment.apiUrl}${path}${qs}`;
    return this.http
      .get(url, { responseType: 'blob', observe: 'response' })
      .pipe(
        map((response: HttpResponse<Blob>) => {
          const blob = response.body;
          if (!blob || blob.size === 0) {
            throw {
              status: response.status,
              message: 'Empty body',
            } as ReportDownloadError;
          }
          const filename = suggestedFilename
            || this.extractFilename(response.headers.get('content-disposition'))
            || this.fallbackFilename(path);
          this.triggerBrowserDownload(blob, filename);
        }),
        catchError((err: unknown) => this.parseHttpError(err)),
      );
  }

  /**
   * Quand `responseType: 'blob'`, Angular met même le body d'erreur dans un
   * Blob. On le lit pour extraire le vrai message.
   */
  private parseHttpError(err: unknown): Observable<never> {
    if (!(err instanceof HttpErrorResponse)) {
      return throwError(() => ({
        status: 0,
        message: 'Erreur réseau ou inconnue',
        raw: err,
      } as ReportDownloadError));
    }
    const errBody = err.error;
    // Pas un Blob → on a déjà le message
    if (!(errBody instanceof Blob)) {
      return throwError(() => ({
        status: err.status,
        message: this.messageFromObject(errBody) ?? err.statusText,
        raw: errBody,
      } as ReportDownloadError));
    }
    // Blob → lire le texte (Promise) puis tenter JSON.parse
    return from(errBody.text()).pipe(
      switchMap((text) => {
        let parsed: unknown = null;
        try { parsed = JSON.parse(text); } catch { /* texte brut */ }
        return throwError(() => ({
          status: err.status,
          message:
            this.messageFromObject(parsed)
            ?? (text && text.length < 500 ? text : err.statusText),
          raw: parsed ?? text,
        } as ReportDownloadError));
      }),
    );
  }

  private messageFromObject(obj: unknown): string | null {
    if (!obj || typeof obj !== 'object') return null;
    const anyObj = obj as Record<string, unknown>;
    if (typeof anyObj['message'] === 'string') return anyObj['message'] as string;
    if (typeof anyObj['error'] === 'string') return anyObj['error'] as string;
    return null;
  }

  private extractFilename(contentDisposition: string | null): string | null {
    if (!contentDisposition) return null;
    const match = contentDisposition.match(/filename="?([^"]+)"?/i);
    return match ? match[1] : null;
  }

  private fallbackFilename(path: string): string {
    const segments = path.split('/').filter(Boolean);
    const last = segments[segments.length - 1] ?? 'report';
    return last.includes('.') ? last : `${last}.bin`;
  }

  private triggerBrowserDownload(blob: Blob, filename: string): void {
    const objectUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = objectUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(objectUrl);
  }
}
