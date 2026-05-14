import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import { Page } from '../../../../finance/models/page.model';
import {
  DeclarationTvaDto,
  STATUT_DECLARATION_TVA_BADGE_MAP,
  StatutDeclarationTva,
} from '../../../models/tva.model';
import { TvaService } from '../../../services/tva.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste paginée des déclarations TVA (B7).
 */
@Component({
  selector: 'app-declarations-tva-list',
  templateUrl: './declarations-tva-list.component.html',
  standalone: false,
})
export class DeclarationsTvaListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: Page<DeclarationTvaDto> | null = null;
  request = { page: 0, size: 10, sort: 'dateDebut,desc' };
  validatingId: number | null = null;

  readonly StatutDeclarationTva = StatutDeclarationTva;
  readonly badgeMap = STATUT_DECLARATION_TVA_BADGE_MAP;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly service: TvaService,
    private readonly router: Router,
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
      .pageDeclarations(this.request)
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

  createNew(): void {
    this.router.navigate(['/comptabilite/tva/declarations/new']);
  }

  valider(d: DeclarationTvaDto): void {
    if (d.statut !== StatutDeclarationTva.BROUILLON) return;
    Swal.fire({
      title: this.i18n.translate('comptabilite.declarationTva.messages.validerConfirm'),
      text: this.i18n.translate('comptabilite.declarationTva.messages.validerWarning'),
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('common.confirm'),
      cancelButtonText: this.i18n.translate('common.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) return;
      this.validatingId = d.id;
      this.service
        .validerDeclaration(d.id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.validatingId = null)),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('comptabilite.declarationTva.messages.validerSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: (err) => {
            const key = err?.error?.message || 'error.declaration.validerFailed';
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate(key),
            });
          },
        });
    });
  }

  get declarations(): DeclarationTvaDto[] {
    return this.page?.content ?? [];
  }

  get pagesArray(): number[] {
    if (!this.page) return [];
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  statutKey(s: StatutDeclarationTva): string {
    return `comptabilite.declarationTva.statut.${s}`;
  }
}
