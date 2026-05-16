import { Component, OnDestroy, OnInit } from '@angular/core';
import { Observable, Subject, combineLatest } from 'rxjs';
import { debounceTime, distinctUntilChanged, map, take, takeUntil } from 'rxjs/operators';

import { ServiceHotelier } from '../../../../inventory/models/service-hotelier.model';
import { ArticleMenu } from '../../../models/article-menu.model';
import { CategorieMenu } from '../../../models/categorie-menu.model';
import { PosCatalogMode, PosStore } from '../state/pos.store';

/**
 * Grille tactile-friendly des articles disponibles, segmentÃ©e par catÃ©gorie.
 *
 *  - Toolbar sticky : champ recherche + chips horizontales catÃ©gories.
 *  - Grille de tuiles responsives (2 â†’ 5 colonnes selon largeur, CSS Grid).
 *  - Click sur tuile = ajout panier (relayÃ© au `PosStore`).
 *  - Feedback de feedback ajout : `flashArticleId` reset auto aprÃ¨s 350 ms.
 */
@Component({
  selector: 'app-pos-article-grid',
  templateUrl: './article-grid.component.html',
  styleUrls: ['./article-grid.component.scss'],
  standalone: false,
})
export class ArticleGridComponent implements OnInit, OnDestroy {
  readonly categories$: Observable<CategorieMenu[]> = this.store.categories$;

  /** Snapshot des catégories pour résoudre rapidement le nom depuis un categorieId. */
  private categoriesById = new Map<number, CategorieMenu>();
  readonly selectedCategorieId$: Observable<number | null> =
    this.store.selectedCategorieId$;
  readonly articlesLoading$: Observable<boolean> = this.store.articlesLoading$;
  readonly articleSearch$: Observable<string> = this.store.articleSearch$;

  /** Mode courant (Tour 55) : `ARTICLES` ou `SERVICES`. */
  readonly mode$: Observable<PosCatalogMode> = this.store.mode$;

  /** Services hÃ´teliers (Tour 55). */
  readonly servicesLoading$: Observable<boolean> = this.store.servicesLoading$;

  /** Articles filtrÃ©s client-side par le champ recherche local. */
  readonly filteredArticles$: Observable<ArticleMenu[]> = this.store.select(
    this.store.articles$,
    this.store.articleSearch$,
    (articles, search) => {
      const term = search.trim().toLocaleLowerCase();
      if (!term) {
        return articles;
      }
      return articles.filter((a) =>
        a.nom.toLocaleLowerCase().includes(term),
      );
    },
  );

  /** Services hÃ´teliers filtrÃ©s client-side par le champ recherche local. */
  readonly filteredServices$: Observable<ServiceHotelier[]> = this.store.select(
    this.store.services$,
    this.store.articleSearch$,
    (services, search) => {
      const term = search.trim().toLocaleLowerCase();
      if (!term) {
        return services;
      }
      return services.filter((s) => {
        const haystack = [
          s.nomService,
          s.codeService,
          s.nomType,
          s.description,
        ]
          .filter((v): v is string => !!v)
          .map((v) => v.toLocaleLowerCase());
        return haystack.some((h) => h.includes(term));
      });
    },
  );

  /** Article rÃ©cemment ajoutÃ© (pour pulse animation). */
  flashArticleId: number | null = null;
  /** Service rÃ©cemment ajoutÃ© (pour pulse animation, Tour 55). */
  flashServiceId: number | null = null;

  private readonly searchInput$ = new Subject<string>();
  private readonly destroy$ = new Subject<void>();
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly store: PosStore) {}

  ngOnInit(): void {
    this.store.loadArticles(null);

    this.categories$
      .pipe(takeUntil(this.destroy$))
      .subscribe((cats) => {
        this.categoriesById.clear();
        for (const c of cats) {
          if (c.categorieId != null) this.categoriesById.set(c.categorieId, c);
        }
      });

    this.searchInput$
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        map((v) => v.trim()),
        takeUntil(this.destroy$),
      )
      .subscribe((term) => this.store.setArticleSearch(term));
  }

  /**
   * Bascule entre les modes (Tour 55). Lazy-load des services au premier
   * passage en mode `SERVICES`.
   */
  onSetMode(mode: PosCatalogMode): void {
    this.store.setMode(mode);
    if (mode === 'SERVICES') {
      combineLatest([this.store.servicesLoaded$, this.store.servicesLoading$])
        .pipe(take(1))
        .subscribe(([loaded, loading]) => {
          if (!loaded && !loading) {
            this.store.loadServices();
          }
        });
    }
  }

  /** Retourne le nom de la catégorie de l'article (lookup local). */
  categorieNom(article: ArticleMenu): string {
    if (article.categorieId == null) return '';
    return this.categoriesById.get(article.categorieId)?.nom ?? '';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.flashTimer) {
      clearTimeout(this.flashTimer);
    }
  }

  onSelectCategorie(categorieId: number | null): void {
    this.store.setSelectedCategorie(categorieId);
    this.store.loadArticles(categorieId);
  }

  onSearchChange(value: string): void {
    this.searchInput$.next(value);
  }

  onAddArticle(article: ArticleMenu): void {
    if (article.disponible === false || article.articleId == null) {
      return;
    }
    this.store.addArticle(article);
    this.flashArticleId = article.articleId;
    if (this.flashTimer) {
      clearTimeout(this.flashTimer);
    }
    this.flashTimer = setTimeout(() => {
      this.flashArticleId = null;
    }, 350);
  }

  /** Ajout d'un service hÃ´telier au panier (Tour 55). */
  onAddService(service: ServiceHotelier): void {
    if (service.actif === false || service.serviceId == null) {
      return;
    }
    this.store.addService(service);
    this.flashServiceId = service.serviceId;
    if (this.flashTimer) {
      clearTimeout(this.flashTimer);
    }
    this.flashTimer = setTimeout(() => {
      this.flashServiceId = null;
    }, 350);
  }

  trackByCategorieId(_index: number, c: CategorieMenu): number | string {
    return c.categorieId ?? _index;
  }

  trackByArticleId(_index: number, a: ArticleMenu): number | string {
    return a.articleId ?? _index;
  }

  trackByServiceId(_index: number, s: ServiceHotelier): number | string {
    return s.serviceId ?? _index;
  }
}
