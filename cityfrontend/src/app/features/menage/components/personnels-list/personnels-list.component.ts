import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import {
  FiltresPersonnels,
  Personnel,
  SPECIALITES_PERSONNEL,
  SpecialitePersonnel,
} from '../../models/personnel.model';
import { PersonnelsService } from '../../services/personnels.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface PersonnelsPageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresPersonnels;
}

/**
 * Liste paginée du personnel de ménage.
 *
 * Pattern aligné sur `restaurant/articles-list` (Tour 23) et
 * `inventory/produits-list` (Tour 16) :
 *  - table Bootstrap + pagination maison + états loading/error/empty/ready
 *  - recherche serveur avec debounce 300 ms
 *  - filtres : statut actif, spécialité
 *  - actions ligne : édition, désactivation/réactivation (soft delete)
 *
 * Pas de DELETE dur (cf. spec : `desactiver`/`reactiver` côté backend
 * pour préserver l'historique).
 */
@Component({
  selector: 'app-personnels-list',
  templateUrl: './personnels-list.component.html',
  styleUrls: ['./personnels-list.component.scss'],
  standalone: false,
})
export class PersonnelsListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<Personnel> | null = null;
  request: PersonnelsPageRequest = {
    page: 0,
    size: 10,
    sortBy: 'nom',
    sortDir: 'asc',
    filtres: {},
  };
  searchTerm = '';
  readonly specialites: ReadonlyArray<SpecialitePersonnel> = SPECIALITES_PERSONNEL;
  togglingId: number | null = null;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly personnelsService: PersonnelsService,
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
    this.personnelsService
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

  onSpecialiteFilterChange(value: string): void {
    const specialite = value || undefined;
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, specialite },
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
    this.router.navigate(['/menage/personnel/new']);
  }

  edit(personnel: Personnel): void {
    if (personnel.personnelId == null) {
      return;
    }
    this.router.navigate(['/menage/personnel', personnel.personnelId]);
  }

  toggleActif(personnel: Personnel): void {
    if (personnel.personnelId == null) {
      return;
    }
    const id = personnel.personnelId;
    const wasActif = personnel.actif !== false;
    const action$ = wasActif
      ? this.personnelsService.desactiver(id)
      : this.personnelsService.reactiver(id);

    const confirmKey = wasActif
      ? 'menage.personnel.messages.desactiverConfirm'
      : 'menage.personnel.messages.reactiverConfirm';
    const successKey = wasActif
      ? 'menage.personnel.messages.desactiverSuccess'
      : 'menage.personnel.messages.reactiverSuccess';
    const errorKey = wasActif
      ? 'menage.personnel.messages.desactiverError'
      : 'menage.personnel.messages.reactiverError';

    Swal.fire({
      title: this.i18n.translate(confirmKey),
      text: personnel.nomComplet ?? `${personnel.prenom} ${personnel.nom}`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('menage.actions.confirm'),
      cancelButtonText: this.i18n.translate('menage.actions.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.togglingId = id;
      action$
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.togglingId = null;
          }),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate(successKey),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate(errorKey),
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
   * Désérialise le champ `specialites` (JSON string) en libellés
   * lisibles. Tolère un format brut si parse JSON échoue.
   */
  formatSpecialites(personnel: Personnel): string {
    if (!personnel.specialites) {
      return '-';
    }
    try {
      const parsed: unknown = JSON.parse(personnel.specialites);
      if (Array.isArray(parsed)) {
        return parsed.filter((x): x is string => typeof x === 'string').join(', ');
      }
    } catch {
      // valeur brute
    }
    return personnel.specialites;
  }
}
