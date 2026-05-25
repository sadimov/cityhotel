import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { AuthService } from '../../../../services/auth.service';
import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { Chambre, FiltresChambres, StatutChambre } from '../../models/chambre.model';
import { TypeChambre } from '../../models/type-chambre.model';
import { ChambresService } from '../../services/chambres.service';
import { TypesChambreService } from '../../services/types-chambre.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste paginée des chambres + filtres (type, statut, étage).
 *
 * Multi-tenant : le filtre `hotelId` est appliqué automatiquement côté backend
 * via `@TenantId` Hibernate (CLAUDE.md racine §6.1).
 */
@Component({
  selector: 'app-chambres-list',
  templateUrl: './chambres-list.component.html',
  styleUrls: ['./chambres-list.component.scss'],
  standalone: false,
})
export class ChambresListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<Chambre> | null = null;
  showInactive = false;
  busy = false;

  /** Types de chambre actifs (alimentation du select de filtre + form). */
  types: TypeChambre[] = [];

  readonly statuts: StatutChambre[] = [
    StatutChambre.DISPONIBLE,
    StatutChambre.OCCUPEE,
    StatutChambre.NETTOYAGE,
    StatutChambre.MAINTENANCE,
    StatutChambre.HORS_SERVICE,
  ];

  filtres: FiltresChambres = {};
  request = { page: 0, size: 10, sortBy: 'numeroChambre', sortDir: 'asc' as 'asc' | 'desc' };

  private readonly destroy$ = new Subject<void>();
  private readonly writeRoles = ['SUPERADMIN', 'ADMIN', 'GERANT'];

  constructor(
    private readonly chambresService: ChambresService,
    private readonly typesService: TypesChambreService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
    private readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.loadTypes();
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get canWrite(): boolean {
    return this.auth.hasAnyRole(this.writeRoles);
  }

  loadTypes(): void {
    this.typesService
      .findActifs()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (list) => (this.types = list),
        error: () => (this.types = []),
      });
  }

  load(): void {
    this.state = 'loading';
    this.chambresService
      .page(this.filtres, this.request.page, this.request.size, this.request.sortBy, this.request.sortDir)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((p) => {
        if (!p) return;
        this.page = p;
        // showInactive = false → on cache les inactives à l'affichage (filtre côté serveur
        // non disponible pour ce critère, on filtre coté client uniquement la page courante).
        if (!this.showInactive) {
          const visibles = (p.content ?? []).filter((c) => c.actif !== false);
          this.page = { ...p, content: visibles, numberOfElements: visibles.length };
          this.state = visibles.length === 0 ? 'empty' : 'ready';
        } else {
          this.state = p.numberOfElements === 0 ? 'empty' : 'ready';
        }
      });
  }

  onFilterTypeChange(value: string): void {
    const v = value ? Number(value) : undefined;
    this.filtres = { ...this.filtres, typeId: Number.isFinite(v as number) ? v : undefined };
    this.request = { ...this.request, page: 0 };
    this.load();
  }

  onFilterStatutChange(value: string): void {
    this.filtres = { ...this.filtres, statut: (value || undefined) as StatutChambre | undefined };
    this.request = { ...this.request, page: 0 };
    this.load();
  }

  onFilterEtageChange(value: string): void {
    const v = value === '' ? undefined : Number(value);
    this.filtres = { ...this.filtres, etage: Number.isFinite(v as number) ? v : undefined };
    this.request = { ...this.request, page: 0 };
    this.load();
  }

  onToggleShowInactive(value: boolean): void {
    this.showInactive = value;
    this.request = { ...this.request, page: 0 };
    this.load();
  }

  goToPage(p: number): void {
    if (!this.page || p < 0 || p >= this.page.totalPages) return;
    this.request = { ...this.request, page: p };
    this.load();
  }

  createNew(): void {
    this.router.navigate(['/hebergement/chambres/new']);
  }

  edit(c: Chambre): void {
    if (c.chambreId == null) return;
    this.router.navigate(['/hebergement/chambres', c.chambreId]);
  }

  deactivate(c: Chambre): void {
    if (c.chambreId == null || c.actif === false) return;
    const id = c.chambreId;
    Swal.fire({
      icon: 'question',
      title: this.i18n.translate('hebergement.chambres.messages.deactivateConfirm'),
      text: c.numeroChambre,
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('hebergement.chambres.actions.deactivate'),
      cancelButtonText: this.i18n.translate('common.cancel'),
      reverseButtons: true,
    }).then((res) => {
      if (!res.isConfirmed) return;
      this.busy = true;
      this.chambresService
        .desactiver(id)
        .pipe(takeUntil(this.destroy$), finalize(() => (this.busy = false)))
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('hebergement.chambres.messages.deactivateSuccess'),
              timer: 1200,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('hebergement.chambres.messages.deactivateError'),
            });
          },
        });
    });
  }

  reactivate(c: Chambre): void {
    if (c.chambreId == null || c.actif !== false) return;
    const id = c.chambreId;
    this.busy = true;
    this.chambresService
      .reactiver(id)
      .pipe(takeUntil(this.destroy$), finalize(() => (this.busy = false)))
      .subscribe({
        next: () => {
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('hebergement.chambres.messages.reactivateSuccess'),
            timer: 1200,
            showConfirmButton: false,
          });
          this.load();
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('hebergement.chambres.messages.reactivateError'),
          });
        },
      });
  }

  statutBadge(statut: StatutChambre | undefined): string {
    switch (statut) {
      case StatutChambre.DISPONIBLE:
        return 'text-bg-success';
      case StatutChambre.OCCUPEE:
        return 'text-bg-danger';
      case StatutChambre.NETTOYAGE:
        return 'text-bg-info';
      case StatutChambre.MAINTENANCE:
        return 'text-bg-warning';
      case StatutChambre.HORS_SERVICE:
        return 'text-bg-secondary';
      default:
        return 'text-bg-light';
    }
  }

  get chambres(): Chambre[] {
    return this.page?.content ?? [];
  }

  get pagesArray(): number[] {
    if (!this.page) return [];
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }
}
