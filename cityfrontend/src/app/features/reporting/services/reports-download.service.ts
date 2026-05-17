import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';

/**
 * Service de téléchargement de rapports (XLSX / PDF).
 *
 * Passe par `HttpClient` — l'intercepteur JWT (`AuthInterceptor`) ajoute
 * automatiquement le `Authorization: Bearer ...`. Un `<a href>` direct vers
 * l'URL ne le ferait pas et retomberait en 401.
 *
 * Construit l'URL via `environment.apiUrl` qui inclut déjà le contexte
 * `/citybackend` côté backend Spring Boot.
 */
@Injectable({ providedIn: 'root' })
export class ReportsDownloadService {
  constructor(private readonly http: HttpClient) {}

  /**
   * Télécharge un rapport (PDF ou XLSX) et déclenche un download navigateur.
   * Le nom de fichier est dérivé de l'URL si non fourni explicitement.
   */
  download(path: string, suggestedFilename?: string): Observable<void> {
    const url = `${environment.apiUrl}${path}`;
    return this.http
      .get(url, { responseType: 'blob', observe: 'response' })
      .pipe(
        map((response: HttpResponse<Blob>) => {
          const blob = response.body;
          if (!blob) return;
          const filename = suggestedFilename
            || this.extractFilename(response.headers.get('content-disposition'))
            || this.fallbackFilename(path);
          this.triggerBrowserDownload(blob, filename);
        }),
      );
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
