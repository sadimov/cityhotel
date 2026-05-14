import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { BonSortie, StatutBonSortie } from '../../models/bon-sortie.model';
import { BonsSortieService } from '../../services/bons-sortie.service';

type DetailState = 'loading' | 'ready' | 'error';

/** Vue détail (read-only) d'un bon de sortie avec lignes (Tour 51bis). */
@Component({
  selector: 'app-bon-sortie-detail',
  templateUrl: './bon-sortie-detail.component.html',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BonSortieDetailComponent implements OnInit, OnDestroy {
  state: DetailState = 'loading';
  entity: BonSortie | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly service: BonsSortieService,
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = Number(idParam);
    if (!Number.isFinite(id) || id <= 0) {
      this.state = 'error';
      return;
    }
    this.service
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (b) => {
          this.entity = b;
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  edit(): void {
    if (this.entity?.bonSortieId != null) {
      this.router.navigate(['/inventory/bons-sortie', this.entity.bonSortieId]);
    }
  }

  back(): void {
    this.router.navigate(['/inventory/bons-sortie']);
  }

  badgeClass(statut: StatutBonSortie | undefined): string {
    switch (statut) {
      case 'brouillon':
        return 'text-bg-secondary';
      case 'valide':
        return 'text-bg-info';
      case 'livre':
        return 'text-bg-success';
      case 'annule':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  statutKey(statut: StatutBonSortie | undefined): string {
    return 'inventory.bonsSortie.statut.' + (statut ?? 'brouillon');
  }
}
