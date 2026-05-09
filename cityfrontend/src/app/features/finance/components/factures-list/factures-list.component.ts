import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';

import { TranslationService } from '../../../../services/translation.service';
import {
  FactureDto,
  StatutFacture,
} from '../../models/facture.model';
import { Page } from '../../models/page.model';
import { FacturesService } from '../../services/factures.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface FacturesPageRequest {
  page: number;
  size: number;
  sort: string;
}

/**
 * Liste paginée des factures.
 *
 * Le backend `FactureController.findAll(Pageable)` ne supporte pas (encore)
 * les filtres serveur (statut / dates / search) — ils seront ajoutés dans
 * un tour ultérieur côté back. Pour l'instant, la pagination + tri Spring
 * Data sont les seuls leviers ; le filtre statut est appliqué côté client
 * sur la page courante.
 *
 * Pattern aligné sur `bons-commande-list` — table Bootstrap + pagination
 * maison + états loading/error/empty/ready.
 */
@Component({
  selector: 'app-factures-list',
  templateUrl: './factures-list.component.html',
  styleUrls: ['./factures-list.component.scss'],
  standalone: false,
})
export class FacturesListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: Page<FactureDto> | null = null;
  request: FacturesPageRequest = {
    page: 0,
    size: 10,
    sort: 'dateFacture,desc',
  };
  selectedStatut: StatutFacture | '' = '';

  readonly statuts: ReadonlyArray<StatutFacture> = [
    StatutFacture.BROUILLON,
    StatutFacture.EMISE,
    StatutFacture.PARTIELLEMENT_PAYEE,
    StatutFacture.PAYEE,
    StatutFacture.ANNULEE,
  ];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly facturesService: FacturesService,
    private readonly router: Router,
    private readonly _i18n: TranslationService,
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
    this.facturesService
      .page({
        page: this.request.page,
        size: this.request.size,
        sort: this.request.sort,
      })
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

  onStatutChange(value: string): void {
    this.selectedStatut = (value || '') as StatutFacture | '';
    // Filtre client-side sur la page courante (pas de filtre serveur côté
    // back tant que `FactureController.findAll` n'expose pas de Specification).
  }

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) {
      return;
    }
    this.request = { ...this.request, page };
    this.load();
  }

  createNew(): void {
    this.router.navigate(['/finance/factures/new']);
  }

  view(facture: FactureDto): void {
    if (facture.factureId == null) {
      return;
    }
    this.router.navigate(['/finance/factures', facture.factureId]);
  }

  encaisser(facture: FactureDto): void {
    if (facture.factureId == null) {
      return;
    }
    this.router.navigate(['/finance/factures', facture.factureId, 'paiement']);
  }

  /**
   * Factures affichées (filtre statut client-side).
   */
  get factures(): FactureDto[] {
    const all = this.page?.content ?? [];
    if (!this.selectedStatut) return all;
    return all.filter((f) => f.statut === this.selectedStatut);
  }

  get pagesArray(): number[] {
    if (!this.page) {
      return [];
    }
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  /** Classe Bootstrap badge selon le statut comptable. */
  badgeClass(statut: StatutFacture | undefined): string {
    switch (statut) {
      case StatutFacture.BROUILLON:
        return 'text-bg-secondary';
      case StatutFacture.EMISE:
        return 'text-bg-info';
      case StatutFacture.PARTIELLEMENT_PAYEE:
        return 'text-bg-warning';
      case StatutFacture.PAYEE:
        return 'text-bg-success';
      case StatutFacture.ANNULEE:
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  statutKey(statut: StatutFacture | undefined): string {
    if (!statut) {
      return 'finance.facture.statut.BROUILLON';
    }
    return 'finance.facture.statut.' + statut;
  }

  /**
   * Vrai si la facture est encaissable (statut EMISE ou PARTIELLEMENT_PAYEE
   * et montant restant > 0).
   */
  peutEncaisser(facture: FactureDto): boolean {
    if (facture.statut !== StatutFacture.EMISE
      && facture.statut !== StatutFacture.PARTIELLEMENT_PAYEE) {
      return false;
    }
    return Number(facture.montantRestant ?? 0) > 0;
  }
}
