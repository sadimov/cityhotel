import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { AuthService } from '../../../../services/auth.service';
import { TranslationService } from '../../../../services/translation.service';
import { TarifChambre } from '../../models/tarif-chambre.model';
import { TypeChambre } from '../../models/type-chambre.model';
import { TarifChambreService } from '../../services/tarif-chambre.service';
import { TypesChambreService } from '../../services/types-chambre.service';

type ListState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste des tarifs saisonniers — affichage PAR TYPE de chambre.
 *
 * Le backend n'expose pas de pagination native pour cet objet : on attend une
 * volumétrie raisonnable (quelques saisons par type). L'utilisateur choisit
 * un type via le dropdown, puis on appelle `GET /by-type/{typeId}`.
 */
@Component({
  selector: 'app-tarifs-chambre-list',
  templateUrl: './tarifs-chambre-list.component.html',
  styleUrls: ['./tarifs-chambre-list.component.scss'],
  standalone: false,
})
export class TarifsChambreListComponent implements OnInit, OnDestroy {
  state: ListState = 'idle';
  types: TypeChambre[] = [];
  tarifs: TarifChambre[] = [];
  selectedTypeId: number | null = null;
  busy = false;

  private readonly destroy$ = new Subject<void>();
  private readonly writeRoles = ['SUPERADMIN', 'ADMIN', 'GERANT'];

  constructor(
    private readonly tarifsService: TarifChambreService,
    private readonly typesService: TypesChambreService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
    private readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.loadTypes();
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
        next: (list) => {
          this.types = list;
          // Auto-sélection du premier type pour éviter un écran vide.
          if (list.length > 0 && this.selectedTypeId == null) {
            this.selectedTypeId = list[0].typeId ?? null;
            if (this.selectedTypeId != null) {
              this.loadTarifs();
            }
          }
        },
        error: () => {
          this.types = [];
        },
      });
  }

  onTypeChange(value: string): void {
    const v = value ? Number(value) : null;
    this.selectedTypeId = Number.isFinite(v as number) ? v : null;
    if (this.selectedTypeId != null) {
      this.loadTarifs();
    } else {
      this.tarifs = [];
      this.state = 'idle';
    }
  }

  loadTarifs(): void {
    if (this.selectedTypeId == null) return;
    this.state = 'loading';
    this.tarifsService
      .findByType(this.selectedTypeId)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((list) => {
        if (list == null) return;
        this.tarifs = list;
        this.state = list.length === 0 ? 'empty' : 'ready';
      });
  }

  createNew(): void {
    // Pré-remplir avec le type sélectionné via query param.
    const queryParams = this.selectedTypeId != null ? { typeId: this.selectedTypeId } : {};
    this.router.navigate(['/hebergement/tarifs-chambre/new'], { queryParams });
  }

  edit(t: TarifChambre): void {
    if (t.tarifId == null) return;
    this.router.navigate(['/hebergement/tarifs-chambre', t.tarifId]);
  }

  remove(t: TarifChambre): void {
    if (t.tarifId == null) return;
    const id = t.tarifId;
    Swal.fire({
      icon: 'warning',
      title: this.i18n.translate('hebergement.tarifsChambre.messages.deleteConfirm'),
      text: t.nomTarif,
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('hebergement.tarifsChambre.actions.delete'),
      cancelButtonText: this.i18n.translate('common.cancel'),
      confirmButtonColor: '#dc3545',
      reverseButtons: true,
    }).then((res) => {
      if (!res.isConfirmed) return;
      this.busy = true;
      this.tarifsService
        .delete(id)
        .pipe(takeUntil(this.destroy$), finalize(() => (this.busy = false)))
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('hebergement.tarifsChambre.messages.deleteSuccess'),
              timer: 1200,
              showConfirmButton: false,
            });
            this.loadTarifs();
          },
          error: (err) => {
            const key = err?.error?.error || 'hebergement.tarifsChambre.messages.deleteError';
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate(key),
            });
          },
        });
    });
  }
}
