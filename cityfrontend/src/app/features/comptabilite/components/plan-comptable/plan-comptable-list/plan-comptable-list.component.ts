import { Component, OnDestroy, OnInit } from '@angular/core';
import { of, Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';

import { Page } from '../../../../finance/models/page.model';
import {
  NATURE_COMPTE_BADGE_MAP,
  NatureCompte,
  PlanComptableGeneralDto,
  STATUT_COMPTE_BADGE_MAP,
  StatutCompteComptable,
} from '../../../models/plan-comptable.model';
import { PlanComptableService } from '../../../services/plan-comptable.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Consultation seule du Plan Comptable Général (B7).
 *
 * Filtre client-side par classe (1 à 7) sur la page courante.
 */
@Component({
  selector: 'app-plan-comptable-list',
  templateUrl: './plan-comptable-list.component.html',
  standalone: false,
})
export class PlanComptableListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: Page<PlanComptableGeneralDto> | null = null;
  request = { page: 0, size: 50, sort: 'compteCode,asc' };
  selectedClasse: number | '' = '';

  readonly classes: ReadonlyArray<number> = [1, 2, 3, 4, 5, 6, 7];
  readonly natureBadge = NATURE_COMPTE_BADGE_MAP;
  readonly statutBadge = STATUT_COMPTE_BADGE_MAP;

  private readonly destroy$ = new Subject<void>();

  constructor(private readonly service: PlanComptableService) {}

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

  onClasseChange(value: string): void {
    this.selectedClasse = value === '' ? '' : Number(value);
  }

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) return;
    this.request = { ...this.request, page };
    this.load();
  }

  get comptes(): PlanComptableGeneralDto[] {
    const all = this.page?.content ?? [];
    if (this.selectedClasse === '') return all;
    return all.filter((c) => c.classe === this.selectedClasse);
  }

  get pagesArray(): number[] {
    if (!this.page) return [];
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  natureKey(n: NatureCompte): string {
    return `comptabilite.planComptable.nature.${n}`;
  }

  statutKey(s: StatutCompteComptable): string {
    return `comptabilite.planComptable.statut.${s}`;
  }
}
