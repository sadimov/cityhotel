import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { FiltresParametres, Parametre } from '../../models/parametre.admin.model';
import { ParametresAdminService } from '../../services/parametres.admin.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface ParametresPageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresParametres;
}

interface CategorieGroup {
  categorie: string;
  items: Parametre[];
}

/**
 * Liste des paramètres globaux (vue SUPERADMIN).
 *
 * Affichage groupé par catégorie. Filtres :
 *  - recherche debounce 300 ms
 *  - filtre `modifiable` (éditables / verrouillés / tous)
 *
 * Actions ligne :
 *  - éditer (vers `/admin/parametres/{id}`) — désactivé si modifiable=false
 *  - supprimer — masqué si modifiable=false (doublé d'un refus serveur 400)
 *  - création (`/admin/parametres/new`)
 */
@Component({
  selector: 'app-admin-parametres-list',
  templateUrl: './parametres-list.component.html',
  styleUrls: ['./parametres-list.component.scss'],
  standalone: false,
})
export class ParametresListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<Parametre> | null = null;
  request: ParametresPageRequest = {
    page: 0,
    size: 50,
    sortBy: 'cle',
    sortDir: 'asc',
    filtres: {},
  };
  searchTerm = '';
  busyId: number | null = null;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly parametresService: ParametresAdminService,
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
    this.parametresService
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

  onModifiableFilterChange(value: string): void {
    let modifiable: boolean | undefined;
    if (value === 'true') {
      modifiable = true;
    } else if (value === 'false') {
      modifiable = false;
    } else {
      modifiable = undefined;
    }
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, modifiable },
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
    this.router.navigate(['/admin/parametres/new']);
  }

  edit(p: Parametre): void {
    if (p.parametreId == null || p.modifiable === false) {
      return;
    }
    this.router.navigate(['/admin/parametres', p.parametreId]);
  }

  delete(p: Parametre): void {
    if (p.parametreId == null || p.modifiable === false) {
      return;
    }
    const id = p.parametreId;
    Swal.fire({
      title: this.i18n.translate('admin.parametres.messages.deleteConfirm'),
      text: p.cle,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('admin.actions.confirm'),
      cancelButtonText: this.i18n.translate('admin.actions.cancel'),
      confirmButtonColor: '#d33',
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.busyId = id;
      this.parametresService
        .delete(id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.busyId = null;
          }),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('admin.parametres.messages.deleteSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('admin.parametres.messages.deleteError'),
            });
          },
        });
    });
  }

  /**
   * Group-by-categorie sur la page courante.
   * Renvoie une liste stable (categorie ascendante puis cle ascendante).
   */
  get groupedByCategorie(): CategorieGroup[] {
    if (!this.page) {
      return [];
    }
    const groups = new Map<string, Parametre[]>();
    for (const item of this.page.content) {
      const cat = item.categorie?.trim() || '_uncategorized';
      const list = groups.get(cat) ?? [];
      list.push(item);
      groups.set(cat, list);
    }
    return Array.from(groups.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([categorie, items]) => ({
        categorie,
        items: items.slice().sort((a, b) => a.cle.localeCompare(b.cle)),
      }));
  }

  get pagesArray(): number[] {
    if (!this.page) {
      return [];
    }
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }
}
