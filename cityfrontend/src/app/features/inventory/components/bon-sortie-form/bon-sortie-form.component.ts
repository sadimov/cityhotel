import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import {
  BonSortie,
  LigneBonSortie,
  StatutBonSortie,
  STATUTS_BON_SORTIE,
} from '../../models/bon-sortie.model';
import { Produit } from '../../models/produit.model';
import { BonsSortieService } from '../../services/bons-sortie.service';
import { ProduitsService } from '../../services/produits.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un bon de sortie (Tour 51 + Tour 51bis).
 *
 * Version Tour 51bis : ajout des transitions de statut (valider / livrer /
 * annuler) avec confirmation SweetAlert2, motif obligatoire pour l'annulation.
 *  - `brouillon` → "Valider" (POST /valider)
 *  - `valide`    → "Livrer"  (POST /livrer) — génère MouvementStock SORTIE
 *  - `brouillon | valide` → "Annuler" (POST /annuler?motifAnnulation=...)
 */
@Component({
  selector: 'app-bon-sortie-form',
  templateUrl: './bon-sortie-form.component.html',
  standalone: false,
})
export class BonSortieFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;
  produits: Produit[] = [];
  /** Statut courant côté serveur (alimenté à l'hydratation). */
  currentStatut: StatutBonSortie = 'brouillon';
  /** Lock générique pendant une transition (valider/livrer/annuler). */
  transitioning = false;
  readonly statuts = STATUTS_BON_SORTIE;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly bonsSortieService: BonsSortieService,
    private readonly produitsService: ProduitsService,
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

  get canValider(): boolean {
    return this.isEditing && this.currentStatut === 'brouillon';
  }

  get canLivrer(): boolean {
    return this.isEditing && this.currentStatut === 'valide';
  }

  get canAnnuler(): boolean {
    return (
      this.isEditing &&
      (this.currentStatut === 'brouillon' || this.currentStatut === 'valide')
    );
  }

  get lignesArray(): FormArray {
    return this.form.get('lignes') as FormArray;
  }

  addLigne(): void {
    this.lignesArray.push(
      this.fb.group({
        produitId: [null, [Validators.required]],
        quantiteDemandee: [1, [Validators.required, Validators.min(0.01)]],
        commentaires: [''],
      }),
    );
  }

  removeLigne(index: number): void {
    this.lignesArray.removeAt(index);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.state = 'submitting';
    const raw = this.form.getRawValue();
    const lignes: LigneBonSortie[] = (raw.lignes || []).map((l: { produitId: number; quantiteDemandee: number; commentaires?: string }) => ({
      produitId: Number(l.produitId),
      quantiteDemandee: Number(l.quantiteDemandee),
      commentaires: l.commentaires || undefined,
    }));
    const payload: BonSortie = {
      numeroBon: String(raw.numeroBon || '').trim(),
      destination: String(raw.destination).trim(),
      statut: raw.statut,
      dateSortie: raw.dateSortie,
      commentaires: raw.commentaires || undefined,
      lignes,
    };
    const obs$ = this.editingId
      ? this.bonsSortieService.update(this.editingId, payload)
      : this.bonsSortieService.create(payload);
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
            ? 'inventory.bonsSortie.messages.updateSuccess'
            : 'inventory.bonsSortie.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/inventory/bons-sortie']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('inventory.bonsSortie.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/inventory/bons-sortie']);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Transitions de statut (Tour 51bis)
  // ────────────────────────────────────────────────────────────────────────

  valider(): void {
    if (!this.editingId || !this.canValider) {
      return;
    }
    Swal.fire({
      icon: 'question',
      title: this.i18n.translate('inventory.bonsSortie.messages.validerConfirm'),
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('inventory.bonsSortie.actions.valider'),
      cancelButtonText: this.i18n.translate('inventory.actions.cancel'),
      reverseButtons: true,
    }).then((res) => {
      if (!res.isConfirmed) {
        return;
      }
      this.transitioning = true;
      this.bonsSortieService
        .valider(this.editingId!)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.transitioning = false)),
        )
        .subscribe({
          next: (bon) => {
            this.applyTransitionResult(
              bon,
              'inventory.bonsSortie.messages.validerSuccess',
            );
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('inventory.bonsSortie.messages.validerError'),
            });
          },
        });
    });
  }

  livrer(): void {
    if (!this.editingId || !this.canLivrer) {
      return;
    }
    Swal.fire({
      icon: 'warning',
      title: this.i18n.translate('inventory.bonsSortie.messages.livrerConfirm'),
      text: this.i18n.translate('inventory.bonsSortie.messages.livrerWarning'),
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('inventory.bonsSortie.actions.livrer'),
      cancelButtonText: this.i18n.translate('inventory.actions.cancel'),
      reverseButtons: true,
    }).then((res) => {
      if (!res.isConfirmed) {
        return;
      }
      // Livrer toutes les lignes telles que présentes côté serveur (quantité
      // demandée = quantité servie par défaut). L'utilisateur peut ajuster
      // ensuite via les endpoints de modification de lignes si besoin.
      const lignes: LigneBonSortie[] = (this.lignesArray.value || []).map(
        (l: { produitId: number; quantiteDemandee: number; commentaires?: string }) => ({
          produitId: Number(l.produitId),
          quantiteDemandee: Number(l.quantiteDemandee),
          commentaires: l.commentaires || undefined,
        }),
      );
      this.transitioning = true;
      this.bonsSortieService
        .livrer(this.editingId!, lignes)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.transitioning = false)),
        )
        .subscribe({
          next: (bon) => {
            this.applyTransitionResult(
              bon,
              'inventory.bonsSortie.messages.livrerSuccess',
            );
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('inventory.bonsSortie.messages.livrerError'),
            });
          },
        });
    });
  }

  annuler(): void {
    if (!this.editingId || !this.canAnnuler) {
      return;
    }
    Swal.fire({
      icon: 'warning',
      title: this.i18n.translate('inventory.bonsSortie.messages.annulerTitle'),
      input: 'text',
      inputLabel: this.i18n.translate('inventory.bonsSortie.messages.annulerMotifLabel'),
      inputPlaceholder: this.i18n.translate(
        'inventory.bonsSortie.messages.annulerMotifPlaceholder',
      ),
      inputValidator: (value) => {
        if (!value || !value.trim()) {
          return this.i18n.translate(
            'inventory.bonsSortie.messages.annulerMotifRequired',
          );
        }
        return null;
      },
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('inventory.bonsSortie.actions.annuler'),
      cancelButtonText: this.i18n.translate('inventory.actions.cancel'),
      reverseButtons: true,
    }).then((res) => {
      if (!res.isConfirmed || !res.value) {
        return;
      }
      const motif = String(res.value).trim();
      this.transitioning = true;
      this.bonsSortieService
        .annuler(this.editingId!, motif)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.transitioning = false)),
        )
        .subscribe({
          next: (bon) => {
            this.applyTransitionResult(
              bon,
              'inventory.bonsSortie.messages.annulerSuccess',
            );
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('inventory.bonsSortie.messages.annulerError'),
            });
          },
        });
    });
  }

  private applyTransitionResult(bon: BonSortie, successKey: string): void {
    this.hydrateForm(bon);
    Swal.fire({
      icon: 'success',
      title: this.i18n.translate(successKey),
      timer: 1500,
      showConfirmButton: false,
    });
  }

  private buildForm(): FormGroup {
    const today = new Date().toISOString().substring(0, 10);
    return this.fb.group({
      numeroBon: [''],
      destination: ['', [Validators.required, Validators.maxLength(150)]],
      statut: ['brouillon', [Validators.required]],
      dateSortie: [today, [Validators.required]],
      commentaires: [''],
      lignes: this.fb.array([]),
    });
  }

  private loadReferentiels(): void {
    forkJoin({
      produits: this.produitsService.findActifs().pipe(catchError(() => of([] as Produit[]))),
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe(({ produits }) => {
        this.produits = produits;
        if (this.state === 'loading' && !this.editingId) {
          this.state = 'ready';
        }
      });
  }

  private loadExisting(id: number): void {
    this.bonsSortieService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (bon) => {
          this.hydrateForm(bon);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(bon: BonSortie): void {
    this.currentStatut = (bon.statut ?? 'brouillon') as StatutBonSortie;
    this.form.patchValue({
      numeroBon: bon.numeroBon ?? '',
      destination: bon.destination ?? '',
      statut: bon.statut ?? 'brouillon',
      dateSortie: bon.dateSortie ?? new Date().toISOString().substring(0, 10),
      commentaires: bon.commentaires ?? '',
    });
    this.lignesArray.clear();
    (bon.lignes ?? []).forEach((l) =>
      this.lignesArray.push(
        this.fb.group({
          produitId: [l.produitId, [Validators.required]],
          quantiteDemandee: [l.quantiteDemandee, [Validators.required, Validators.min(0.01)]],
          commentaires: [l.commentaires ?? ''],
        }),
      ),
    );
  }
}
