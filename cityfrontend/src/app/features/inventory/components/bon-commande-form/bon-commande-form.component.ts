import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import {
  BonCommande,
  LigneBonCommande,
  StatutBonCommande,
} from '../../models/bon-commande.model';
import { Fournisseur } from '../../models/fournisseur.model';
import { Produit } from '../../models/produit.model';
import { BonsCommandeService } from '../../services/bons-commande.service';
import { FournisseursService } from '../../services/fournisseurs.service';
import { ProduitsService } from '../../services/produits.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un bon de commande avec **FormArray**
 * pour les lignes.
 *
 * Validators :
 *  - fournisseurId / dateCommande : required
 *  - lignes : au moins une ligne valide
 *  - chaque ligne : produitId required, quantiteCommandee >= 1, prixUnitaire >= 0
 *
 * Le total est recalculé live via `valueChanges` du FormArray (cf. CLAUDE.md
 * cityfrontend §4.2 — éviter logique métier dans le template).
 */
@Component({
  selector: 'app-bon-commande-form',
  templateUrl: './bon-commande-form.component.html',
  styleUrls: ['./bon-commande-form.component.scss'],
  standalone: false,
})
export class BonCommandeFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;
  fournisseurs: Fournisseur[] = [];
  produits: Produit[] = [];
  totalCalcule = 0;
  currentStatut: StatutBonCommande = 'brouillon';

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly bonsCommandeService: BonsCommandeService,
    private readonly fournisseursService: FournisseursService,
    private readonly produitsService: ProduitsService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.loadReferentiels();
    this.subscribeTotal();

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

  /** Accès typé au FormArray des lignes (utilisé dans le template). */
  get lignes(): FormArray<FormGroup> {
    return this.form.get('lignes') as FormArray<FormGroup>;
  }

  ligneAt(index: number): FormGroup {
    return this.lignes.at(index);
  }

  addLigne(): void {
    this.lignes.push(this.buildLigne());
  }

  removeLigne(index: number): void {
    this.lignes.removeAt(index);
  }

  /** Sous-total d'une ligne (live, sans recalcul backend). */
  sousTotal(index: number): number {
    const g = this.ligneAt(index);
    const qte = Number(g.get('quantiteCommandee')?.value ?? 0);
    const prix = Number(g.get('prixUnitaire')?.value ?? 0);
    return Number.isFinite(qte * prix) ? qte * prix : 0;
  }

  submit(): void {
    if (this.form.invalid || this.lignes.length === 0) {
      this.form.markAllAsTouched();
      this.lignes.markAllAsTouched();
      return;
    }
    this.state = 'submitting';
    const raw = this.form.getRawValue();

    const lignesPayload: LigneBonCommande[] = (raw.lignes as Array<{
      ligneId?: number;
      produitId: number;
      quantiteCommandee: number;
      prixUnitaire: number;
    }>).map((l) => ({
      ligneId: l.ligneId,
      produitId: Number(l.produitId),
      quantiteCommandee: Number(l.quantiteCommandee),
      prixUnitaire: Number(l.prixUnitaire),
    }));

    const payload: BonCommande = {
      numeroBon: raw.numeroBon || '',
      fournisseurId: Number(raw.fournisseurId),
      statut: this.currentStatut,
      dateCommande: raw.dateCommande,
      dateLivraisonPrevue: raw.dateLivraisonPrevue || undefined,
      montantTotal: this.totalCalcule,
      montantTva: 0,
      commentaires: raw.commentaires || undefined,
      lignes: lignesPayload,
    };

    const obs$ = this.editingId
      ? this.bonsCommandeService.update(this.editingId, payload)
      : this.bonsCommandeService.create(payload);

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
            ? 'inventory.bonCommande.messages.updateSuccess'
            : 'inventory.bonCommande.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/inventory/bons-commande']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('inventory.bonCommande.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/inventory/bons-commande']);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    return this.fb.group({
      numeroBon: [''],
      fournisseurId: [null, [Validators.required]],
      dateCommande: ['', [Validators.required]],
      dateLivraisonPrevue: [''],
      commentaires: [''],
      lignes: this.fb.array<FormGroup>([this.buildLigne()]),
    });
  }

  private buildLigne(ligne?: LigneBonCommande): FormGroup {
    return this.fb.group({
      ligneId: new FormControl<number | null>(ligne?.ligneId ?? null),
      produitId: new FormControl<number | null>(ligne?.produitId ?? null, {
        validators: [Validators.required],
      }),
      quantiteCommandee: new FormControl<number>(
        ligne?.quantiteCommandee ?? 1,
        { validators: [Validators.required, Validators.min(1)], nonNullable: true },
      ),
      prixUnitaire: new FormControl<number>(ligne?.prixUnitaire ?? 0, {
        validators: [Validators.required, Validators.min(0)],
        nonNullable: true,
      }),
    });
  }

  private subscribeTotal(): void {
    // Recalcule le total à chaque changement (lignes ou contenu d'une ligne).
    this.form.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.totalCalcule = this.lignes.controls.reduce((acc, g) => {
          const qte = Number(g.get('quantiteCommandee')?.value ?? 0);
          const prix = Number(g.get('prixUnitaire')?.value ?? 0);
          const sous = qte * prix;
          return acc + (Number.isFinite(sous) ? sous : 0);
        }, 0);
      });
  }

  private loadReferentiels(): void {
    forkJoin({
      fournisseurs: this.fournisseursService
        .findActifs()
        .pipe(catchError(() => of([] as Fournisseur[]))),
      produits: this.produitsService
        .findActifs()
        .pipe(catchError(() => of([] as Produit[]))),
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe(({ fournisseurs, produits }) => {
        this.fournisseurs = fournisseurs;
        this.produits = produits;
        if (this.state === 'loading' && !this.editingId) {
          this.state = 'ready';
        }
      });
  }

  private loadExisting(id: number): void {
    this.bonsCommandeService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (b) => {
          this.hydrateForm(b);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(b: BonCommande): void {
    this.currentStatut = b.statut ?? 'brouillon';
    this.form.patchValue({
      numeroBon: b.numeroBon ?? '',
      fournisseurId: b.fournisseurId ?? null,
      dateCommande: b.dateCommande ?? '',
      dateLivraisonPrevue: b.dateLivraisonPrevue ?? '',
      commentaires: b.commentaires ?? '',
    });
    // Reconstruire le FormArray avec les lignes existantes
    this.lignes.clear();
    if (b.lignes && b.lignes.length > 0) {
      for (const l of b.lignes) {
        this.lignes.push(this.buildLigne(l));
      }
    } else {
      this.lignes.push(this.buildLigne());
    }
  }
}
