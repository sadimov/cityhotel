import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of, shareReplay } from 'rxjs';

import { environment } from '../../environments/environment';
import {
  ApiResponse,
  DonneesReferentielles,
} from '../features/clients/models/client.model';

/**
 * Service de lecture du référentiel global (nationalités, types d'identification, …).
 *
 * Endpoint backend : `GET /api/donnees-referentielles?categorie=<cat>`.
 *
 * Chaque catégorie est mise en cache mémoire au premier appel (`shareReplay`).
 * Le référentiel est seedé par Liquibase et n'évolue pas au runtime — un cache
 * de session est donc sûr et évite de retirer 250 lignes à chaque ouverture
 * d'un formulaire.
 */
@Injectable({ providedIn: 'root' })
export class DonneesReferentiellesService {
  private readonly base = `${environment.apiUrl}/api/donnees-referentielles`;

  /** Cache par catégorie (vidé seulement au refresh / logout de la SPA). */
  private cache = new Map<string, Observable<DonneesReferentielles[]>>();

  constructor(private readonly http: HttpClient) {}

  /**
   * Liste les entrées actives d'une catégorie, triées pour l'affichage.
   * Cache mémoire partagé entre tous les consommateurs.
   */
  findByCategorie(categorie: string): Observable<DonneesReferentielles[]> {
    const cached = this.cache.get(categorie);
    if (cached) {
      return cached;
    }
    const params = new HttpParams().set('categorie', categorie);
    const stream$ = this.http
      .get<ApiResponse<DonneesReferentielles[]> | DonneesReferentielles[]>(this.base, { params })
      .pipe(
        // Le backend wrappe via ApiResponseBodyAdvice (`{success, data: [...]}`),
        // mais on supporte aussi la réponse brute par sécurité.
        (source) =>
          new Observable<DonneesReferentielles[]>((sub) => {
            const inner = source.subscribe({
              next: (r) => {
                const arr = Array.isArray(r) ? r : (r?.data ?? []);
                sub.next(arr);
                sub.complete();
              },
              error: (err) => sub.error(err),
            });
            return () => inner.unsubscribe();
          }),
        shareReplay(1),
      );
    this.cache.set(categorie, stream$);
    return stream$;
  }

  /** Convenience : nationalités (catégorie 'nationalite'). */
  nationalites(): Observable<DonneesReferentielles[]> {
    return this.findByCategorie('nationalite');
  }

  /** Vide le cache (utile après logout pour ne pas mélanger les sessions). */
  clearCache(): void {
    this.cache.clear();
  }

  /** Resolver synchrone vide pour les composants qui veulent un fallback. */
  empty(): Observable<DonneesReferentielles[]> {
    return of([]);
  }
}
