import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';

import { Page } from '../../../../finance/models/page.model';
import {
  EcritureComptableDto,
  STATUT_ECRITURE_BADGE_MAP,
  StatutEcriture,
} from '../../../models/ecriture.model';
import { EcrituresService } from '../../../services/ecritures.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste paginée des écritures comptables (B7).
 *
 * Filtres client-side (statut) sur la page courante.
 * Filtres serveur ad-hoc (journal/compte/exercice) accessibles via les
 * écrans dédiés grand-livre / journal-viewer.
 */
@Component({
  selector: 'app-ecritures-list',
  templateUrl: './ecritures-list.component.html',
  standalone: false,
})
export class EcrituresListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: Page<EcritureComptableDto> | null = null;
  request = { page: 0, size: 10, sort: 'dateComptable,desc' };
  selectedStatut: StatutEcriture | '' = '';

  readonly statuts: ReadonlyArray<StatutEcriture> = [
    StatutEcriture.BROUILLON,
    StatutEcriture.VALIDEE,
    StatutEcriture.CONTRE_PASSEE,
  ];
  readonly badgeMap = STATUT_ECRITURE_BADGE_MAP;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly service: EcrituresService,
    private readonly router: Router,
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

  onStatutChange(value: string): void {
    this.selectedStatut = (value || '') as StatutEcriture | '';
  }

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) return;
    this.request = { ...this.request, page };
    this.load();
  }

  createNew(): void {
    this.router.navigate(['/comptabilite/ecritures/new']);
  }

  view(ec: EcritureComptableDto): void {
    this.router.navigate(['/comptabilite/ecritures', ec.id]);
  }

  get ecritures(): EcritureComptableDto[] {
    const all = this.page?.content ?? [];
    if (!this.selectedStatut) return all;
    return all.filter((e) => e.statut === this.selectedStatut);
  }

  get pagesArray(): number[] {
    if (!this.page) return [];
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  statutKey(s: StatutEcriture): string {
    return `comptabilite.ecriture.statut.${s}`;
  }
}
