import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { ArticleMenu } from '../../models/article-menu.model';
import { CategorieMenu } from '../../models/categorie-menu.model';
import { ArticlesMenusService } from '../../services/articles-menus.service';
import { CategoriesMenusService } from '../../services/categories-menus.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire crÃ©ation / Ã©dition d'un article du menu.
 *
 * Validators :
 *  - `categorieId` : required
 *  - `nomArticle` : required + minLength(2) + maxLength(150)
 *  - `prix` : required + min(0)
 *  - `coutIngredients` / `tempsPreparation` : optionnels, >= 0
 *  - `codeArticle` : optionnel, maxLength(20)
 *
 * Pattern alignÃ© sur `produit-form` (Tour 16). Le champ `allergenes`
 * est saisi en texte libre (le composant gÃ¨re le sÃ©rialisation JSON
 * `["x","y"]` quand l'utilisateur saisit "x, y") â€” UI plus avancÃ©e
 * diffÃ©rÃ©e Ã  un tour ultÃ©rieur.
 */
@Component({
  selector: 'app-article-form',
  templateUrl: './article-form.component.html',
  styleUrls: ['./article-form.component.scss'],
  standalone: false,
})
export class ArticleFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;
  categories: CategorieMenu[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly articlesService: ArticlesMenusService,
    private readonly categoriesService: CategoriesMenusService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.loadCategories();

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

    const payload: ArticleMenu = {
      categorieId: Number(raw.categorieId),
      codeArticle: raw.codeArticle ? String(raw.codeArticle).toUpperCase().trim() : undefined,
      nom: String(raw.nom).trim(),
      nomEn: raw.nomEn ? String(raw.nomEn).trim() : undefined,
      nomAr: raw.nomAr ? String(raw.nomAr).trim() : undefined,
      description: raw.description || undefined,
      descriptionEn: raw.descriptionEn || undefined,
      descriptionAr: raw.descriptionAr || undefined,
      prix: Number(raw.prix ?? 0),
      coutIngredients: raw.coutIngredients != null ? Number(raw.coutIngredients) : undefined,
      tempsPreparation: raw.tempsPreparation != null ? Number(raw.tempsPreparation) : undefined,
      allergenes: this.serializeAllergenes(raw.allergenes),
      imageUrl: raw.imageUrl || undefined,
      disponible: raw.disponible !== false,
      actif: raw.actif !== false,
    };

    const obs$ = this.editingId
      ? this.articlesService.update(this.editingId, payload)
      : this.articlesService.create(payload);

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
            ? 'restaurant.article.messages.updateSuccess'
            : 'restaurant.article.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/restaurant/articles']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('restaurant.article.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/restaurant/articles']);
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // PrivÃ©
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  private buildForm(): FormGroup {
    return this.fb.group({
      categorieId: [null, [Validators.required]],
      codeArticle: ['', [Validators.maxLength(20)]],
      nom: [
        '',
        [Validators.required, Validators.minLength(2), Validators.maxLength(150)],
      ],
      nomEn: ['', [Validators.maxLength(150)]],
      nomAr: ['', [Validators.maxLength(150)]],
      description: ['', [Validators.maxLength(500)]],
      descriptionEn: ['', [Validators.maxLength(500)]],
      descriptionAr: ['', [Validators.maxLength(500)]],
      prix: [0, [Validators.required, Validators.min(0)]],
      coutIngredients: [null, [Validators.min(0)]],
      tempsPreparation: [null, [Validators.min(0)]],
      allergenes: [''],
      imageUrl: ['', [Validators.maxLength(500)]],
      disponible: [true],
      actif: [true],
    });
  }

  private loadCategories(): void {
    this.categoriesService
      .findActives()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of([] as CategorieMenu[])),
      )
      .subscribe((cats) => {
        this.categories = cats;
        if (this.state === 'loading' && !this.editingId) {
          this.state = 'ready';
        }
      });
  }

  private loadExisting(id: number): void {
    this.articlesService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (a) => {
          this.hydrateForm(a);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(a: ArticleMenu): void {
    this.form.patchValue({
      categorieId: a.categorieId ?? null,
      codeArticle: a.codeArticle ?? '',
      nom: a.nom ?? '',
      nomEn: a.nomEn ?? '',
      nomAr: a.nomAr ?? '',
      description: a.description ?? '',
      descriptionEn: a.descriptionEn ?? '',
      descriptionAr: a.descriptionAr ?? '',
      prix: a.prix ?? 0,
      coutIngredients: a.coutIngredients ?? null,
      tempsPreparation: a.tempsPreparation ?? null,
      allergenes: this.deserializeAllergenes(a.allergenes),
      imageUrl: a.imageUrl ?? '',
      disponible: a.disponible !== false,
      actif: a.actif !== false,
    });
  }

  /**
   * SÃ©rialise une saisie texte Â« gluten, lactose Â» en JSON `["gluten","lactose"]`.
   * Renvoie `undefined` si la liste est vide pour ne pas polluer le payload.
   */
  private serializeAllergenes(input: unknown): string | undefined {
    if (typeof input !== 'string' || !input.trim()) {
      return undefined;
    }
    const items = input
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
    return items.length > 0 ? JSON.stringify(items) : undefined;
  }

  /**
   * DÃ©sÃ©rialise un JSON `["gluten","lactose"]` en saisie texte
   * Â« gluten, lactose Â». TolÃ¨re un format brut si le backend renvoie
   * autre chose qu'un JSON valide.
   */
  private deserializeAllergenes(json: string | undefined): string {
    if (!json) {
      return '';
    }
    try {
      const parsed: unknown = JSON.parse(json);
      if (Array.isArray(parsed)) {
        return parsed.filter((x) => typeof x === 'string').join(', ');
      }
    } catch {
      // Format inattendu â€” on retombe sur la valeur brute
    }
    return json;
  }
}
