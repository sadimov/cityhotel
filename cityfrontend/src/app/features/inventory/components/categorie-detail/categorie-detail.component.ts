import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { CategorieProduit } from '../../models/categorie.model';
import { CategoriesService } from '../../services/categories.service';

type DetailState = 'loading' | 'ready' | 'error';

/**
 * Vue détail (read-only) d'une catégorie de produit (Tour 51bis).
 *
 * OnPush — pas de logique métier dans le template ; uniquement de la
 * présentation enrichie via `<dl>` 2 colonnes (Bootstrap).
 */
@Component({
  selector: 'app-categorie-detail',
  templateUrl: './categorie-detail.component.html',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategorieDetailComponent implements OnInit, OnDestroy {
  state: DetailState = 'loading';
  entity: CategorieProduit | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly service: CategoriesService,
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
        next: (c) => {
          this.entity = c;
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
    if (this.entity?.categorieId != null) {
      this.router.navigate(['/inventory/categories', this.entity.categorieId]);
    }
  }

  back(): void {
    this.router.navigate(['/inventory/categories']);
  }
}
