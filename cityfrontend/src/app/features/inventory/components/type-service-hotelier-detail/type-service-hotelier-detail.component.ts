import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { TypeServiceHotelier } from '../../models/type-service-hotelier.model';
import { TypesServicesHoteliersService } from '../../services/types-services-hoteliers.service';

type DetailState = 'loading' | 'ready' | 'error';

/** Vue détail (read-only) d'un type de service hôtelier (Tour 51bis). */
@Component({
  selector: 'app-type-service-hotelier-detail',
  templateUrl: './type-service-hotelier-detail.component.html',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TypeServiceHotelierDetailComponent implements OnInit, OnDestroy {
  state: DetailState = 'loading';
  entity: TypeServiceHotelier | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly service: TypesServicesHoteliersService,
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
        next: (t) => {
          this.entity = t;
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
    if (this.entity?.typeServiceId != null) {
      this.router.navigate([
        '/inventory/types-services-hoteliers',
        this.entity.typeServiceId,
      ]);
    }
  }

  back(): void {
    this.router.navigate(['/inventory/types-services-hoteliers']);
  }
}
