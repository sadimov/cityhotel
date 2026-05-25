import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import {
  ArticleMenu,
  FiltresArticlesMenus,
} from '../models/article-menu.model';

/**
 * Service HTTP — Articles du menu (module restaurant, catalogue).
 *
 * Spec :
 *   - GET    /api/restaurant/articles                       — page (search, filtres, sort)
 *   - GET    /api/restaurant/articles/disponibles           — liste articles disponibles (POS futur)
 *   - GET    /api/restaurant/articles/{id}                  — read
 *   - POST   /api/restaurant/articles                       — create
 *   - PUT    /api/restaurant/articles/{id}                  — update
 *   - DELETE /api/restaurant/articles/{id}                  — delete (soft)
 *   - PATCH  /api/restaurant/articles/{id}/statut           — change statut (ACTIF/RUPTURE/INACTIF)
 *
 * ⚠️ `hotelId` n'est JAMAIS transmis (JWT côté serveur).
 *
 * <h2>Note sur la rupture (alignement Tour audit PATCH/CORS)</h2>
 * <p>Le backend n'expose qu'un seul endpoint {@code PATCH /statut} avec
 * l'enum {@code StatutArticle = ACTIF | RUPTURE | INACTIF}. Les anciennes
 * méthodes du service envoyaient des PUT vers des routes inexistantes
 * (/disponibilite, /rupture) qui retournaient 404. Désormais :
 * <ul>
 *   <li>{@link #setDisponibilite}(id, true)  → {@code PATCH /statut {ACTIF}}</li>
 *   <li>{@link #setDisponibilite}(id, false) → {@code PATCH /statut {RUPTURE}}</li>
 *   <li>{@link #setRupture}(id, motif?)      → {@code PATCH /statut {RUPTURE}}</li>
 * </ul>
 * Le paramètre {@code motif} de {@link #setRupture} est conservé pour
 * compatibilité de signature mais <b>ignoré</b> par le backend (pas de champ
 * persistant côté entité {@code ArticleMenu}).</p>
 */
@Injectable({ providedIn: 'root' })
export class ArticlesMenusService {
  private readonly base = `${environment.apiUrl}/api/restaurant/articles`;

  constructor(private readonly http: HttpClient) {}

  page(
    filtres: FiltresArticlesMenus = {},
    page = 0,
    size = 10,
    sortBy = 'nomArticle',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<ArticleMenu>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.categorieId != null) {
      params = params.set('categorieId', String(filtres.categorieId));
    }
    if (filtres.disponible !== undefined) {
      params = params.set('disponible', String(filtres.disponible));
    }
    if (filtres.actif !== undefined) {
      params = params.set('actif', String(filtres.actif));
    }
    return this.http
      .get<ApiResponse<PageResponse<ArticleMenu>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<ArticleMenu>));
  }

  /**
   * Articles disponibles pour usage POS (catégorie filtrable, exclut les
   * articles non actifs ou en rupture). Réservé au Tour 24+ mais exposé
   * dès maintenant pour cohérence d'API.
   */
  findDisponibles(categorieId?: number): Observable<ArticleMenu[]> {
    let params = new HttpParams();
    if (categorieId != null) {
      params = params.set('categorieId', String(categorieId));
    }
    return this.http
      .get<ApiResponse<ArticleMenu[]>>(`${this.base}/disponibles`, { params })
      .pipe(map((r) => r.data ?? []));
  }

  findById(id: number): Observable<ArticleMenu> {
    return this.http
      .get<ApiResponse<ArticleMenu>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as ArticleMenu));
  }

  create(dto: ArticleMenu): Observable<ArticleMenu> {
    return this.http
      .post<ApiResponse<ArticleMenu>>(this.base, dto)
      .pipe(map((r) => r.data as ArticleMenu));
  }

  update(id: number, dto: ArticleMenu): Observable<ArticleMenu> {
    return this.http
      .put<ApiResponse<ArticleMenu>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as ArticleMenu));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }

  /**
   * Bascule rapide « disponible » <-> « non disponible ». Utilisé pour
   * marquer un plat indisponible le temps d'un service. Distinct du soft
   * delete (`actif`).
   *
   * Mappe sur l'enum backend {@code StatutArticle} :
   * {@code disponible=true} → {@code ACTIF}, {@code false} → {@code RUPTURE}.
   * Pour réactiver depuis {@code INACTIF}, utiliser plutôt le bouton
   * "réactiver" qui passe par l'endpoint dédié.
   */
  setDisponibilite(id: number, disponible: boolean): Observable<ArticleMenu> {
    const statut: 'ACTIF' | 'RUPTURE' = disponible ? 'ACTIF' : 'RUPTURE';
    return this.http
      .patch<ApiResponse<ArticleMenu>>(`${this.base}/${id}/statut`, { statut })
      .pipe(map((r) => r.data as ArticleMenu));
  }

  /**
   * Déclare une rupture (rupture stock ingrédients, plat momentanément
   * indisponible). Équivalent à {@code setDisponibilite(id, false)} —
   * le paramètre {@code motif} est conservé pour compatibilité mais
   * ignoré côté serveur (cf. JSDoc en-tête du service).
   */
  setRupture(id: number, _motif?: string): Observable<ArticleMenu> {
    return this.http
      .patch<ApiResponse<ArticleMenu>>(`${this.base}/${id}/statut`, { statut: 'RUPTURE' })
      .pipe(map((r) => r.data as ArticleMenu));
  }
}
