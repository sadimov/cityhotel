import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { CategorieMenu } from '../../models/categorie-menu.model';
import { CategoriesMenusService } from '../../services/categories-menus.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire crÃ©ation / Ã©dition d'une catÃ©gorie de menu.
 *
 * Validators :
 *  - `nomCategorie` : required + minLength(2) + maxLength(100)
 *  - `ordreAffichage` : >= 0
 *  - `nomCategorieEn` / `nomCategorieAr` : optionnels (i18n trilingue)
 *
 * Pattern alignÃ© sur `produit-form` (Tour 16) et `facture-form` (Tour 19) :
 *  - route sÃ©parÃ©e `/restaurant/categories/new` ou `/restaurant/categories/:id`
 *    (pas de modal embarquÃ©e â€” cohÃ©rence routing/back-button)
 *  - hydratation via `findById()` quand `editingId` prÃ©sent
 *  - SweetAlert2 pour les feedbacks succÃ¨s/erreur
 */
@Component({
  selector: 'app-category-form',
  templateUrl: './category-form.component.html',
  styleUrls: ['./category-form.component.scss'],
  standalone: false,
})
export class CategoryFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly categoriesService: CategoriesMenusService,
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
    } else {
      this.state = 'ready';
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

    const payload: CategorieMenu = {
      nom: String(raw.nom).trim(),
      nomEn: raw.nomEn ? String(raw.nomEn).trim() : undefined,
      nomAr: raw.nomAr ? String(raw.nomAr).trim() : undefined,
      description: raw.description || undefined,
      ordre: Number(raw.ordre ?? 0),
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
            ? 'restaurant.categorie.messages.updateSuccess'
            : 'restaurant.categorie.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/restaurant/categories']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('restaurant.categorie.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/restaurant/categories']);
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // PrivÃ©
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  private buildForm(): FormGroup {
    return this.fb.group({
      nom: [
        '',
        [Validators.required, Validators.minLength(2), Validators.maxLength(100)],
      ],
      nomEn: ['', [Validators.maxLength(100)]],
      nomAr: ['', [Validators.maxLength(100)]],
      description: ['', [Validators.maxLength(500)]],
      ordre: [0, [Validators.required, Validators.min(0)]],
      actif: [true],
    });
  }

  private loadExisting(id: number): void {
    this.categoriesService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (c) => {
          this.hydrateForm(c);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(c: CategorieMenu): void {
    this.form.patchValue({
      nom: c.nom ?? '',
      nomEn: c.nomEn ?? '',
      nomAr: c.nomAr ?? '',
      description: c.description ?? '',
      ordre: c.ordre ?? 0,
      actif: c.actif !== false,
    });
  }
}
