import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { CategorieProduit } from '../../models/categorie.model';
import { Fournisseur } from '../../models/fournisseur.model';
import { Produit } from '../../models/produit.model';
import { CategoriesService } from '../../services/categories.service';
import { FournisseursService } from '../../services/fournisseurs.service';
import { ProduitsService } from '../../services/produits.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un produit.
 *
 * Validators (Tour 16) :
 *  - codeProduit / nomProduit / categorieId / uniteMesure : required
 *  - prixUnitaire / seuilAlerte / seuilCritique / stockActuel : >= 0
 *  - codeProduit : pattern alphanumérique majuscule (3-20 chars)
 */
@Component({
  selector: 'app-produit-form',
  templateUrl: './produit-form.component.html',
  styleUrls: ['./produit-form.component.scss'],
  standalone: false,
})
export class ProduitFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;
  categories: CategorieProduit[] = [];
  fournisseurs: Fournisseur[] = [];

  /** Liste statique des unités de mesure usuelles. */
  readonly unites: ReadonlyArray<{ value: string; labelKey: string }> = [
    { value: 'kg', labelKey: 'inventory.produit.unites.kg' },
    { value: 'g', labelKey: 'inventory.produit.unites.g' },
    { value: 'l', labelKey: 'inventory.produit.unites.l' },
    { value: 'ml', labelKey: 'inventory.produit.unites.ml' },
    { value: 'unité', labelKey: 'inventory.produit.unites.unite' },
    { value: 'boîte', labelKey: 'inventory.produit.unites.boite' },
    { value: 'paquet', labelKey: 'inventory.produit.unites.paquet' },
    { value: 'bouteille', labelKey: 'inventory.produit.unites.bouteille' },
    { value: 'carton', labelKey: 'inventory.produit.unites.carton' },
  ];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly produitsService: ProduitsService,
    private readonly categoriesService: CategoriesService,
    private readonly fournisseursService: FournisseursService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.loadReferentiels();

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

    const payload: Produit = {
      codeProduit: String(raw.codeProduit).toUpperCase().trim(),
      nomProduit: String(raw.nomProduit).trim(),
      description: raw.description || undefined,
      categorieId: Number(raw.categorieId),
      uniteMesure: raw.uniteMesure,
      prixUnitaire: Number(raw.prixUnitaire ?? 0),
      seuilAlerte: Number(raw.seuilAlerte ?? 0),
      seuilCritique: Number(raw.seuilCritique ?? 0),
      stockActuel: Number(raw.stockActuel ?? 0),
      fournisseurPrincipalId: raw.fournisseurPrincipalId
        ? Number(raw.fournisseurPrincipalId)
        : undefined,
      estFacturable: !!raw.estFacturable,
      actif: raw.actif !== false,
    };

    const obs$ = this.editingId
      ? this.produitsService.update(this.editingId, payload)
      : this.produitsService.create(payload);

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
            ? 'inventory.produit.messages.updateSuccess'
            : 'inventory.produit.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/inventory/produits']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('inventory.produit.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/inventory/produits']);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    return this.fb.group({
      codeProduit: [
        '',
        [
          Validators.required,
          Validators.minLength(3),
          Validators.maxLength(20),
          Validators.pattern(/^[A-Z0-9_-]{3,20}$/i),
        ],
      ],
      nomProduit: ['', [Validators.required, Validators.maxLength(150)]],
      description: [''],
      categorieId: [null, [Validators.required]],
      uniteMesure: ['unité', [Validators.required]],
      prixUnitaire: [0, [Validators.required, Validators.min(0)]],
      seuilAlerte: [0, [Validators.required, Validators.min(0)]],
      seuilCritique: [0, [Validators.required, Validators.min(0)]],
      stockActuel: [0, [Validators.required, Validators.min(0)]],
      fournisseurPrincipalId: [null],
      estFacturable: [true],
      actif: [true],
    });
  }

  private loadReferentiels(): void {
    forkJoin({
      categories: this.categoriesService.findActives().pipe(catchError(() => of([] as CategorieProduit[]))),
      fournisseurs: this.fournisseursService.findActifs().pipe(catchError(() => of([] as Fournisseur[]))),
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe(({ categories, fournisseurs }) => {
        this.categories = categories;
        this.fournisseurs = fournisseurs;
        if (this.state === 'loading' && !this.editingId) {
          this.state = 'ready';
        }
      });
  }

  private loadExisting(id: number): void {
    this.produitsService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (p) => {
          this.hydrateForm(p);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(p: Produit): void {
    this.form.patchValue({
      codeProduit: p.codeProduit ?? '',
      nomProduit: p.nomProduit ?? '',
      description: p.description ?? '',
      categorieId: p.categorieId ?? null,
      uniteMesure: p.uniteMesure ?? 'unité',
      prixUnitaire: p.prixUnitaire ?? 0,
      seuilAlerte: p.seuilAlerte ?? 0,
      seuilCritique: p.seuilCritique ?? 0,
      stockActuel: p.stockActuel ?? 0,
      fournisseurPrincipalId: p.fournisseurPrincipalId ?? null,
      estFacturable: p.estFacturable !== false,
      actif: p.actif !== false,
    });
  }
}
