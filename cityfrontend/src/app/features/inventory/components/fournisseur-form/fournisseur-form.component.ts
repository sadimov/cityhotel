import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Fournisseur } from '../../models/fournisseur.model';
import { FournisseursService } from '../../services/fournisseurs.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un fournisseur (Tour 51).
 */
@Component({
  selector: 'app-fournisseur-form',
  templateUrl: './fournisseur-form.component.html',
  standalone: false,
})
export class FournisseurFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'ready';
  editingId: number | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly fournisseursService: FournisseursService,
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
      this.fournisseursService
        .findById(id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (f) => {
            this.form.patchValue({
              nomFournisseur: f.nomFournisseur ?? '',
              contactPrincipal: f.contactPrincipal ?? '',
              telephone: f.telephone ?? '',
              email: f.email ?? '',
              adresse: f.adresse ?? '',
              ville: f.ville ?? '',
              pays: f.pays ?? '',
              conditionsPaiement: f.conditionsPaiement ?? '',
              actif: f.actif !== false,
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
    const payload: Fournisseur = {
      nomFournisseur: String(raw.nomFournisseur).trim(),
      contactPrincipal: raw.contactPrincipal || undefined,
      telephone: raw.telephone || undefined,
      email: raw.email || undefined,
      adresse: raw.adresse || undefined,
      ville: raw.ville || undefined,
      pays: raw.pays || undefined,
      conditionsPaiement: raw.conditionsPaiement || undefined,
      actif: raw.actif !== false,
    };
    const obs$ = this.editingId
      ? this.fournisseursService.update(this.editingId, payload)
      : this.fournisseursService.create(payload);
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
            ? 'inventory.fournisseurs.messages.updateSuccess'
            : 'inventory.fournisseurs.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/inventory/fournisseurs']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('inventory.fournisseurs.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/inventory/fournisseurs']);
  }

  private buildForm(): FormGroup {
    return this.fb.group({
      nomFournisseur: ['', [Validators.required, Validators.maxLength(150)]],
      contactPrincipal: ['', [Validators.maxLength(100)]],
      telephone: ['', [Validators.maxLength(30)]],
      email: ['', [Validators.email, Validators.maxLength(150)]],
      adresse: ['', [Validators.maxLength(255)]],
      ville: ['', [Validators.maxLength(100)]],
      pays: ['', [Validators.maxLength(100)]],
      conditionsPaiement: ['', [Validators.maxLength(255)]],
      actif: [true],
    });
  }
}
