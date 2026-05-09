import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import {
  ArticleMenu,
  FiltresArticlesMenus,
} from '../../models/article-menu.model';
import { CategorieMenu } from '../../models/categorie-menu.model';
import { ArticlesMenusService } from '../../services/articles-menus.service';
import { CategoriesMenusService } from '../../services/categories-menus.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface ArticlesPageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresArticlesMenus;
}

/**
 * Liste paginée des articles du menu (catalogue restaurant).
 *
 * Pattern aligné sur `produits-list` (Tour 16) et `factures-list` (Tour 19) :
 *  - table Bootstrap + pagination maison + états loading/error/empty/ready
 *  - recherche serveur avec debounce 300 ms
 *  - filtres : catégorie, disponibilité, statut actif
 *  - actions ligne : édition, suppression, bascule disponibilité
 */
@Component({
  selector: 'app-articles-list',
  templateUrl: './articles-list.component.html',
  styleUrls: ['./articles-list.component.scss'],
  standalone: false,
})
export class ArticlesListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<ArticleMenu> | null = null;
  request: ArticlesPageRequest = {
    page: 0,
    size: 10,
    sortBy: 'nomArticle',
    sortDir: 'asc',
    filtres: {},
  };
  searchTerm = '';
  categories: CategorieMenu[] = [];
  deleting = false;
  togglingId: number | null = null;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly articlesService: ArticlesMenusService,
    private readonly categoriesService: CategoriesMenusService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.loadCategories();
    this.load();
  }

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.state = 'loading';
    this.articlesService
      .page(
        this.request.filtres,
        this.request.page,
        this.request.size,
        this.request.sortBy,
        this.request.sortDir,
      )
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((p) => {
        if (!p) {
          return;
        }
        this.page = p;
        this.state = p.numberOfElements === 0 ? 'empty' : 'ready';
      });
  }

  onSearchChange(value: string): void {
    this.searchTerm = value;
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => {
      this.request = {
        ...this.request,
        page: 0,
        filtres: { ...this.request.filtres, search: value.trim() || undefined },
      };
      this.load();
    }, 300);
  }

  onCategorieFilterChange(value: string): void {
    const categorieId = value ? Number(value) : undefined;
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, categorieId },
    };
    this.load();
  }

  onDisponibleFilterChange(value: string): void {
    let disponible: boolean | undefined;
    if (value === 'true') {
      disponible = true;
    } else if (value === 'false') {
      disponible = false;
    } else {
      disponible = undefined;
    }
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, disponible },
    };
    this.load();
  }

  onActifFilterChange(value: string): void {
    let actif: boolean | undefined;
    if (value === 'true') {
      actif = true;
    } else if (value === 'false') {
      actif = false;
    } else {
      actif = undefined;
    }
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, actif },
    };
    this.load();
  }

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) {
      return;
    }
    this.request = { ...this.request, page };
    this.load();
  }

  createNew(): void {
    this.router.navigate(['/restaurant/articles/new']);
  }

  edit(article: ArticleMenu): void {
    if (article.articleId == null) {
      return;
    }
    this.router.navigate(['/restaurant/articles', article.articleId]);
  }

  toggleDisponibilite(article: ArticleMenu): void {
    if (article.articleId == null) {
      return;
    }
    const id = article.articleId;
    const next = !article.disponible;
    this.togglingId = id;
    this.articlesService
      .setDisponibilite(id, next)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.togglingId = null;
        }),
      )
      .subscribe({
        next: () => {
          this.load();
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('restaurant.article.messages.toggleError'),
          });
        },
      });
  }

  remove(article: ArticleMenu): void {
    if (article.articleId == null) {
      return;
    }
    const id = article.articleId;
    Swal.fire({
      title: this.i18n.translate('restaurant.article.messages.deleteConfirm'),
      text: article.nomArticle,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('restaurant.article.actions.delete'),
      cancelButtonText: this.i18n.translate('restaurant.actions.close'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.deleting = true;
      this.articlesService
        .delete(id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.deleting = false;
          }),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('restaurant.article.messages.deleteSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('restaurant.article.messages.deleteError'),
            });
          },
        });
    });
  }

  get pagesArray(): number[] {
    if (!this.page) {
      return [];
    }
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  /**
   * Retrouve le nom de la catégorie pour affichage. Évite un join côté
   * backend : on utilise les catégories actives chargées au montage.
   * Si le backend renvoie déjà `nomCategorie` dans l'article, on
   * l'utilise en priorité.
   */
  categorieNom(article: ArticleMenu): string {
    if (article.nomCategorie) {
      return article.nomCategorie;
    }
    const found = this.categories.find((c) => c.categorieId === article.categorieId);
    return found?.nomCategorie ?? '-';
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private loadCategories(): void {
    this.categoriesService
      .findActives()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of([] as CategorieMenu[])),
      )
      .subscribe((cats) => {
        this.categories = cats;
      });
  }
}
