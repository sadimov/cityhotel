import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Hotel } from '../../models/hotel.admin.model';
import { HotelsAdminService } from '../../services/hotels.admin.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un hôtel (vue SUPERADMIN).
 *
 * Validators :
 *  - `code` : required + maxLength(20) + pattern alphanumérique.
 *    **Immutable en édition** (utilisé dans le JWT et les imports).
 *  - `nom` : required + maxLength(150)
 *  - `email` : optionnel + format email
 *  - `telephone` : optionnel + maxLength(20)
 *  - `siteWeb` : optionnel + maxLength(255)
 *
 * Pattern aligné sur `menage/personnel-form` (Tour 27).
 */
@Component({
  selector: 'app-admin-hotel-form',
  templateUrl: './hotel-form.component.html',
  styleUrls: ['./hotel-form.component.scss'],
  standalone: false,
})
export class HotelFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'ready';
  editingId: number | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly hotelsService: HotelsAdminService,
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
      this.loadExisting(id);
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

    const payload: Hotel = {
      code: String(raw.code).trim(),
      nom: String(raw.nom).trim(),
      adresse: raw.adresse ? String(raw.adresse).trim() : undefined,
      ville: raw.ville ? String(raw.ville).trim() : undefined,
      pays: raw.pays ? String(raw.pays).trim() : undefined,
      telephone: raw.telephone ? String(raw.telephone).trim() : undefined,
      email: raw.email ? String(raw.email).trim() : undefined,
      siteWeb: raw.siteWeb ? String(raw.siteWeb).trim() : undefined,
      actif: raw.actif !== false,
    };

    const obs$ = this.editingId
      ? this.hotelsService.update(this.editingId, payload)
      : this.hotelsService.create(payload);

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
            ? 'admin.hotels.messages.updateSuccess'
            : 'admin.hotels.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/admin/hotels']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('admin.hotels.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/admin/hotels']);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    return this.fb.group({
      code: [
        '',
        [
          Validators.required,
          Validators.maxLength(20),
          Validators.pattern(/^[A-Za-z0-9_-]+$/),
        ],
      ],
      nom: ['', [Validators.required, Validators.maxLength(150)]],
      adresse: ['', [Validators.maxLength(255)]],
      ville: ['', [Validators.maxLength(100)]],
      pays: ['', [Validators.maxLength(100)]],
      telephone: ['', [Validators.maxLength(20)]],
      email: ['', [Validators.email, Validators.maxLength(150)]],
      siteWeb: ['', [Validators.maxLength(255)]],
      actif: [true],
    });
  }

  private loadExisting(id: number): void {
    this.state = 'loading';
    this.hotelsService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (h) => {
          this.hydrateForm(h);
          // Code immutable en édition (utilisé comme clé business JWT).
          this.form.get('code')?.disable({ emitEvent: false });
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(h: Hotel): void {
    this.form.patchValue({
      code: h.code ?? '',
      nom: h.nom ?? '',
      adresse: h.adresse ?? '',
      ville: h.ville ?? '',
      pays: h.pays ?? '',
      telephone: h.telephone ?? '',
      email: h.email ?? '',
      siteWeb: h.siteWeb ?? '',
      actif: h.actif !== false,
    });
  }
}
