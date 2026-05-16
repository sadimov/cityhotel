import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { BonCommande, StatutBonCommande } from '../../models/bon-commande.model';
import { BonsCommandeService } from '../../services/bons-commande.service';

type DetailState = 'loading' | 'ready' | 'error';

/** Vue détail (read-only) d'un bon de commande avec lignes (Tour 51bis). */
@Component({
  selector: 'app-bon-commande-detail',
  templateUrl: './bon-commande-detail.component.html',
  standalone: false,
})
export class BonCommandeDetailComponent implements OnInit, OnDestroy {
  state: DetailState = 'loading';
  entity: BonCommande | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly service: BonsCommandeService,
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
    if (this.entity?.bonCommandeId != null) {
      this.router.navigate(['/inventory/bons-commande', this.entity.bonCommandeId]);
    }
  }

  back(): void {
    this.router.navigate(['/inventory/bons-commande']);
  }

  badgeClass(statut: StatutBonCommande | undefined): string {
    switch (statut) {
      case 'brouillon':
        return 'text-bg-secondary';
      case 'envoye':
        return 'text-bg-primary';
      case 'confirme':
        return 'text-bg-info';
      case 'recu_partiel':
        return 'text-bg-warning';
      case 'recu_complet':
        return 'text-bg-success';
      case 'annule':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  statutKey(statut: StatutBonCommande | undefined): string {
    return 'inventory.bonCommande.statut.' + (statut ?? 'brouillon');
  }
}
