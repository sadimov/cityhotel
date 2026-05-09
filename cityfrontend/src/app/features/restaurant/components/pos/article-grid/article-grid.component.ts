import { Component, OnDestroy, OnInit } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, map, takeUntil } from 'rxjs/operators';

import { ArticleMenu } from '../../../models/article-menu.model';
import { CategorieMenu } from '../../../models/categorie-menu.model';
import { PosStore } from '../state/pos.store';

/**
 * Grille tactile-friendly des articles disponibles, segmentée par catégorie.
 *
 *  - Charge les catégories via le store au montage.
 *  - Filtre les articles affichés via la recherche locale (champ `nomArticle`).
 *  - Au clic sur un article, l'ajoute au panier via `PosStore.addArticle`.
 *
 * Le tri par catégorie est géré côté store (filtre serveur via
 * `findDisponibles(categorieId)`).
 */
@Component({
  selector: 'app-pos-article-grid',
  templateUrl: './article-grid.component.html',
  styleUrls: ['./article-grid.component.scss'],
  standalone: false,
})
export class ArticleGridComponent implements OnInit, OnDestroy {
  readonly categories$: Observable<CategorieMenu[]> = this.store.categories$;
  readonly selectedCategorieId$: Observable<number | null> =
    this.store.selectedCategorieId$;
  readonly articlesLoading$: Observable<boolean> = this.store.articlesLoading$;
  readonly articleSearch$: Observable<string> = this.store.articleSearch$;

  /** Articles filtrés client-side par le champ recherche local. */
  readonly filteredArticles$: Observable<ArticleMenu[]> = this.store.select(
    this.store.articles$,
    this.store.articleSearch$,
    (articles, search) => {
      const term = search.trim().toLocaleLowerCase();
      if (!term) {
        return articles;
      }
      return articles.filter((a) =>
        a.nomArticle.toLocaleLowerCase().includes(term),
      );
    },
  );

  private readonly searchInput$ = new Subject<string>();
  private readonly destroy$ = new Subject<void>();

  constructor(private readonly store: PosStore) {}

  ngOnInit(): void {
    this.store.loadArticles(null);

    this.searchInput$
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        map((v) => v.trim()),
        takeUntil(this.destroy$),
      )
      .subscribe((term) => this.store.setArticleSearch(term));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSelectCategorie(categorieId: number | null): void {
    this.store.setSelectedCategorie(categorieId);
    this.store.loadArticles(categorieId);
  }

  onSearchChange(value: string): void {
    this.searchInput$.next(value);
  }

  onAddArticle(article: ArticleMenu): void {
    this.store.addArticle(article);
  }

  trackByCategorieId(_index: number, c: CategorieMenu): number | string {
    return c.categorieId ?? _index;
  }

  trackByArticleId(_index: number, a: ArticleMenu): number | string {
    return a.articleId ?? _index;
  }
}
