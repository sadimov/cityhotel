import { Component, OnDestroy, OnInit } from '@angular/core';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import { Page } from '../../../../finance/models/page.model';
import {
  ExerciceDto,
  STATUT_EXERCICE_BADGE_MAP,
  StatutExercice,
} from '../../../models/exercice.model';
import { ExercicesService } from '../../../services/exercices.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste paginée des exercices comptables — module comptabilite (B7).
 *
 * Actions exposées :
 *  - bouton "Clôturer" sur les exercices en statut `OUVERT`
 *    (confirmation SweetAlert2 — action irréversible).
 *
 * La création d'un exercice n'est pas exposée côté API back B7 : la
 * gestion du cycle exercice est faite par initialisation côté serveur.
 */
@Component({
  selector: 'app-exercices-list',
  templateUrl: './exercices-list.component.html',
  standalone: false,
})
export class ExercicesListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: Page<ExerciceDto> | null = null;
  request = { page: 0, size: 10, sort: 'dateDebut,desc' };
  closingId: number | null = null;

  readonly StatutExercice = StatutExercice;
  readonly badgeMap = STATUT_EXERCICE_BADGE_MAP;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly service: ExercicesService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.state = 'loading';
    this.service
      .page(this.request)
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
        this.state = p.numberOfElements === 0 ? 'empty' : 'ready';
      });
  }

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) return;
    this.request = { ...this.request, page };
    this.load();
  }

  cloturer(ex: ExerciceDto): void {
    if (ex.statut !== StatutExercice.OUVERT) return;
    Swal.fire({
      title: this.i18n.translate('comptabilite.exercice.messages.cloturerConfirm'),
      text: this.i18n.translate('comptabilite.exercice.messages.cloturerWarning'),
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('common.confirm'),
      cancelButtonText: this.i18n.translate('common.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) return;
      this.closingId = ex.id;
      this.service
        .cloturer(ex.id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.closingId = null)),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('comptabilite.exercice.messages.cloturerSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: (err) => {
            const key = err?.error?.message || 'error.exercice.cloture';
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate(key),
            });
          },
        });
    });
  }

  get exercices(): ExerciceDto[] {
    return this.page?.content ?? [];
  }

  get pagesArray(): number[] {
    if (!this.page) return [];
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  statutKey(s: StatutExercice): string {
    return `comptabilite.exercice.statut.${s}`;
  }
}
