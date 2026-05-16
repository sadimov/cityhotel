import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { ServiceHotelier } from '../../models/service-hotelier.model';
import { TypeServiceHotelier } from '../../models/type-service-hotelier.model';
import { ServicesHoteliersService } from '../../services/services-hoteliers.service';
import { TypesServicesHoteliersService } from '../../services/types-services-hoteliers.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un service hôtelier (Tour 51 Phase A).
 */
@Component({
  selector: 'app-service-hotelier-form',
  templateUrl: './service-hotelier-form.component.html',
  standalone: false,
})
export class ServiceHotelierFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;
  types: TypeServiceHotelier[] = [];

  readonly unites: ReadonlyArray<{ value: string; labelKey: string }> = [
    { value: 'unité', labelKey: 'inventory.produit.unites.unite' },
    { value: 'forfait', labelKey: 'inventory.services.unites.forfait' },
    { value: 'heure', labelKey: 'inventory.services.unites.heure' },
    { value: 'jour', labelKey: 'inventory.services.unites.jour' },
    { value: 'nuit', labelKey: 'inventory.services.unites.nuit' },
  ];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly servicesService: ServicesHoteliersService,
    private readonly typesService: TypesServicesHoteliersService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.loadTypes();

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && idParam !== 'new') {
      const id = Number(idParam);
      if (!Number.isFinite(id)) {
        this.state = 'error';
        return;
      }
      this.editingId = id;
      this.servicesService
        .findById(id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (s) => {
            this.form.patchValue({
              codeService: s.codeService ?? '',
              nomService: s.nomService ?? '',
              description: s.description ?? '',
              typeServiceId: s.typeServiceId ?? null,
              uniteMesure: s.uniteMesure ?? 'unité',
              prixUnitaire: s.prixUnitaire ?? 0,
              estFacturable: s.estFacturable !== false,
              actif: s.actif !== false,
            });
            this.state = 'ready';
          },
          error: () => {
            this.state = 'error';
          },
        });
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get isEditing(): boolean {
    return this.editingId !== null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.state = 'submitting';
    const raw = this.form.getRawValue();
    const payload: ServiceHotelier = {
      typeServiceId: Number(raw.typeServiceId),
      codeService: String(raw.codeService).toUpperCase().trim(),
      nomService: String(raw.nomService).trim(),
      description: raw.description || undefined,
      uniteMesure: raw.uniteMesure || undefined,
      prixUnitaire: Number(raw.prixUnitaire ?? 0),
      estFacturable: raw.estFacturable !== false,
      actif: raw.actif !== false,
    };
    const obs$ = this.editingId
      ? this.servicesService.update(this.editingId, payload)
      : this.servicesService.create(payload);
    obs$
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          if (this.state === 'submitting') {
            this.state = 'ready';
          }
        }),
      )
      .subscribe({
        next: () => {
          const successKey = this.editingId
            ? 'inventory.services.messages.updateSuccess'
            : 'inventory.services.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/inventory/services-hoteliers']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('inventory.services.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/inventory/services-hoteliers']);
  }

  private buildForm(): FormGroup {
    return this.fb.group({
      codeService: [
        '',
        [Validators.required, Validators.minLength(2), Validators.maxLength(20), Validators.pattern(/^[A-Z0-9_-]{2,20}$/i)],
      ],
      nomService: ['', [Validators.required, Validators.maxLength(150)]],
      description: [''],
      typeServiceId: [null, [Validators.required]],
      uniteMesure: ['unité', [Validators.required, Validators.maxLength(20)]],
      prixUnitaire: [0, [Validators.required, Validators.min(0)]],
      estFacturable: [true],
      actif: [true],
    });
  }

  private loadTypes(): void {
    this.typesService
      .findActifs()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of([] as TypeServiceHotelier[])),
      )
      .subscribe((types) => {
        this.types = types;
        if (this.state === 'loading' && !this.editingId) {
          this.state = 'ready';
        }
      });
  }
}
