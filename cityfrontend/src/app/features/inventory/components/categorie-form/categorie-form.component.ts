import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { CategorieProduit } from '../../models/categorie.model';
import { CategoriesService } from '../../services/categories.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'une catégorie de produit (Tour 51).
 */
@Component({
  selector: 'app-categorie-form',
  templateUrl: './categorie-form.component.html',
  standalone: false,
})
export class CategorieFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'ready';
  editingId: number | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly categoriesService: CategoriesService,
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
      this.categoriesService
        .findById(id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (c) => {
            this.form.patchValue({
              codeCategorie: c.codeCategorie ?? '',
              nomCategorie: c.nomCategorie ?? '',
              description: c.description ?? '',
              actif: c.actif !== false,
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
    const payload: CategorieProduit = {
      codeCategorie: String(raw.codeCategorie).toUpperCase().trim(),
      nomCategorie: String(raw.nomCategorie).trim(),
      description: raw.description || undefined,
      actif: raw.actif !== false,
    };
    const obs$ = this.editingId
      ? this.categoriesService.update(this.editingId, payload)
      : this.categoriesService.create(payload);
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
            ? 'inventory.categories.messages.updateSuccess'
            : 'inventory.categories.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/inventory/categories']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('inventory.categories.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/inventory/categories']);
  }

  private buildForm(): FormGroup {
    return this.fb.group({
      codeCategorie: [
        '',
        [Validators.required, Validators.minLength(2), Validators.maxLength(10), Validators.pattern(/^[A-Z0-9_-]{2,10}$/i)],
      ],
      nomCategorie: ['', [Validators.required, Validators.maxLength(100)]],
      description: [''],
      actif: [true],
    });
  }
}
