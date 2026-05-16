import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import {
  CategorieMenu,
  FiltresCategoriesMenus,
} from '../../models/categorie-menu.model';
import { CategoriesMenusService } from '../../services/categories-menus.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface CategoriesPageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresCategoriesMenus;
}

/**
 * Liste paginÃ©e des catÃ©gories de menu (catalogue restaurant).
 *
 * Pattern alignÃ© sur `produits-list` (Tour 16) et `factures-list` (Tour 19) :
 *  - table Bootstrap + pagination maison + Ã©tats loading/error/empty/ready
 *  - recherche serveur avec debounce 300 ms
 *  - tri par dÃ©faut : `ordreAffichage asc`
 *
 * NB : DataTables.net direct n'est pas utilisÃ© ici (le wrapper
 * `<app-data-table>` n'existe pas encore dans `shared/`). Migration
 * possible quand le wrapper sera disponible.
 */
@Component({
  selector: 'app-categories-list',
  templateUrl: './categories-list.component.html',
  styleUrls: ['./categories-list.component.scss'],
  standalone: false,
})
export class CategoriesListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<CategorieMenu> | null = null;
  request: CategoriesPageRequest = {
    page: 0,
    size: 10,
    sortBy: 'ordre',
    sortDir: 'asc',
    filtres: {},
  };
  searchTerm = '';
  deleting = false;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly categoriesService: CategoriesMenusService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
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
    this.categoriesService
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
    this.router.navigate(['/restaurant/categories/new']);
  }

  edit(cat: CategorieMenu): void {
    if (cat.categorieId == null) {
      return;
    }
    this.router.navigate(['/restaurant/categories', cat.categorieId]);
  }

  remove(cat: CategorieMenu): void {
    if (cat.categorieId == null) {
      return;
    }
    const id = cat.categorieId;
    Swal.fire({
      title: this.i18n.translate('restaurant.categorie.messages.deleteConfirm'),
      text: cat.nom,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('restaurant.categorie.actions.delete'),
      cancelButtonText: this.i18n.translate('restaurant.actions.close'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.deleting = true;
      this.categoriesService
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
              title: this.i18n.translate('restaurant.categorie.messages.deleteSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('restaurant.categorie.messages.deleteError'),
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
}
