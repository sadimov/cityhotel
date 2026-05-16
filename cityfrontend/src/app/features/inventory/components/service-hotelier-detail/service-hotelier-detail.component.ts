import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { ServiceHotelier } from '../../models/service-hotelier.model';
import { ServicesHoteliersService } from '../../services/services-hoteliers.service';

type DetailState = 'loading' | 'ready' | 'error';

/** Vue détail (read-only) d'un service hôtelier (Tour 51bis). */
@Component({
  selector: 'app-service-hotelier-detail',
  templateUrl: './service-hotelier-detail.component.html',
  standalone: false,
})
export class ServiceHotelierDetailComponent implements OnInit, OnDestroy {
  state: DetailState = 'loading';
  entity: ServiceHotelier | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly service: ServicesHoteliersService,
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
        next: (s) => {
          this.entity = s;
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
    if (this.entity?.serviceId != null) {
      this.router.navigate(['/inventory/services-hoteliers', this.entity.serviceId]);
    }
  }

  back(): void {
    this.router.navigate(['/inventory/services-hoteliers']);
  }
}
