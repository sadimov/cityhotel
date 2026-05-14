import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { TypeServiceHotelier } from '../../models/type-service-hotelier.model';
import { TypesServicesHoteliersService } from '../../services/types-services-hoteliers.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un type de service hôtelier (Tour 51 Phase A).
 */
@Component({
  selector: 'app-type-service-hotelier-form',
  templateUrl: './type-service-hotelier-form.component.html',
  standalone: false,
})
export class TypeServiceHotelierFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'ready';
  editingId: number | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly typesService: TypesServicesHoteliersService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && idParam !== 'new') {
      const id = Number(idParam);
      if (!Number.isFinite(id)) {
        this.state = 'error';
        return;
      }
      this.editingId = id;
      this.state = 'loading';
      this.typesService
        .findById(id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (t) => {
            this.form.patchValue({
              codeType: t.codeType ?? '',
              nomType: t.nomType ?? '',
              description: t.description ?? '',
              actif: t.actif !== false,
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
    const payload: TypeServiceHotelier = {
      codeType: String(raw.codeType).toUpperCase().trim(),
      nomType: String(raw.nomType).trim(),
      description: raw.description || undefined,
      actif: raw.actif !== false,
    };
    const obs$ = this.editingId
      ? this.typesService.update(this.editingId, payload)
      : this.typesService.create(payload);
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
            ? 'inventory.typesServices.messages.updateSuccess'
            : 'inventory.typesServices.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/inventory/types-services-hoteliers']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('inventory.typesServices.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/inventory/types-services-hoteliers']);
  }

  private buildForm(): FormGroup {
    return this.fb.group({
      codeType: [
        '',
        [Validators.required, Validators.minLength(2), Validators.maxLength(10), Validators.pattern(/^[A-Z0-9_-]{2,10}$/i)],
      ],
      nomType: ['', [Validators.required, Validators.maxLength(100)]],
      description: [''],
      actif: [true],
    });
  }
}
