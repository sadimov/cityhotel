import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { Fournisseur } from '../../models/fournisseur.model';
import { FournisseursService } from '../../services/fournisseurs.service';

type DetailState = 'loading' | 'ready' | 'error';

/** Vue détail (read-only) d'un fournisseur (Tour 51bis). */
@Component({
  selector: 'app-fournisseur-detail',
  templateUrl: './fournisseur-detail.component.html',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FournisseurDetailComponent implements OnInit, OnDestroy {
  state: DetailState = 'loading';
  entity: Fournisseur | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly service: FournisseursService,
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
        next: (f) => {
          this.entity = f;
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
    if (this.entity?.fournisseurId != null) {
      this.router.navigate(['/inventory/fournisseurs', this.entity.fournisseurId]);
    }
  }

  back(): void {
    this.router.navigate(['/inventory/fournisseurs']);
  }
}
