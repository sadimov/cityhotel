import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import {
  FactureCreateDto,
  LigneFactureCreateDto,
  TypeFacture,
  TYPES_FACTURE,
  TypeLigneFacture,
  TYPES_LIGNE_FACTURE,
} from '../../models/facture.model';
import { FacturesService } from '../../services/factures.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire de **création** d'une facture (le backend n'expose pas
 * `PUT /factures/{id}` — l'édition se fait par cycle BROUILLON → EMISE →
 * AVOIR si correction nécessaire).
 *
 * Schéma d'entrée : `FactureCreateDto`
 *   - `typeFacture` (required)
 *   - une cible parmi `compteId / clientId / societeId / reservationId /
 *     fournisseurId` — au moins l'une doit être renseignée (validateur
 *     groupe)
 *   - `dateFacture` / `dateEcheance` / `devise` / `commentaires` optionnels
 *   - `lignes` optionnel (vide → BROUILLON ; sinon recalcul auto serveur)
 *
 * NB : la sélection des entités cibles (compte, client, société...) se fait
 * en saisie d'identifiant numérique tant que les wrappers
 * `<app-search-select>` partagés ne sont pas disponibles. La valeur `0` ou
 * négative est rejetée avant envoi.
 *
 * Total live calculé via `valueChanges` du FormArray (anti-pattern logique
 * dans le template — cf. CLAUDE.md cityfrontend §4.2).
 */
@Component({
  selector: 'app-facture-form',
  templateUrl: './facture-form.component.html',
  styleUrls: ['./facture-form.component.scss'],
  standalone: false,
})
export class FactureFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'ready';
  totalCalcule = 0;

  readonly typesFacture = TYPES_FACTURE;
  readonly typesLigne = TYPES_LIGNE_FACTURE;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly facturesService: FacturesService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.subscribeTotal();

    // L'édition n'est pas supportée côté back. Si on arrive via /:id/edit,
    // on redirige vers le détail.
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && idParam !== 'new') {
      const id = Number(idParam);
      if (Number.isFinite(id) && id > 0) {
        this.router.navigate(['/finance/factures', id]);
      } else {
        this.state = 'error';
      }
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Accès typé au FormArray des lignes. */
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
    const qte = Number(g.get('quantite')?.value ?? 0);
    const prix = Number(g.get('prixUnitaire')?.value ?? 0);
    return Number.isFinite(qte * prix) ? qte * prix : 0;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.lignes.markAllAsTouched();
      return;
    }
    this.state = 'submitting';
    const raw = this.form.getRawValue() as {
      typeFacture: TypeFacture;
      compteId: number | null;
      clientId: number | null;
      societeId: number | null;
      reservationId: number | null;
      fournisseurId: number | null;
      dateFacture: string;
      dateEcheance: string;
      devise: string;
      commentaires: string;
      lignes: Array<{
        typeLigne: TypeLigneFacture;
        nuiteeId: number | null;
        produitId: number | null;
        serviceId: number | null;
        commandeId: number | null;
        libelle: string;
        quantite: number;
        prixUnitaire: number;
        tauxTva: number | null;
        datePrestation: string | null;
      }>;
    };

    const lignesPayload: LigneFactureCreateDto[] = raw.lignes.map((l) => ({
      typeLigne: l.typeLigne,
      nuiteeId: this.toIdOrUndef(l.nuiteeId),
      produitId: this.toIdOrUndef(l.produitId),
      serviceId: this.toIdOrUndef(l.serviceId),
      commandeId: this.toIdOrUndef(l.commandeId),
      libelle: String(l.libelle).trim(),
      quantite: Number(l.quantite),
      prixUnitaire: Number(l.prixUnitaire),
      tauxTva: l.tauxTva != null ? Number(l.tauxTva) : undefined,
      datePrestation: l.datePrestation || undefined,
    }));

    const payload: FactureCreateDto = {
      typeFacture: raw.typeFacture,
      compteId: this.toIdOrUndef(raw.compteId),
      clientId: this.toIdOrUndef(raw.clientId),
      societeId: this.toIdOrUndef(raw.societeId),
      reservationId: this.toIdOrUndef(raw.reservationId),
      fournisseurId: this.toIdOrUndef(raw.fournisseurId),
      dateFacture: raw.dateFacture || undefined,
      dateEcheance: raw.dateEcheance || undefined,
      devise: raw.devise?.trim() || undefined,
      commentaires: raw.commentaires?.trim() || undefined,
      lignes: lignesPayload.length > 0 ? lignesPayload : undefined,
    };

    this.facturesService
      .create(payload)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          if (this.state === 'submitting') {
            this.state = 'ready';
          }
        }),
      )
      .subscribe({
        next: (f) => {
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('finance.facture.messages.createSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
          if (f.factureId) {
            this.router.navigate(['/finance/factures', f.factureId]);
          } else {
            this.router.navigate(['/finance/factures']);
          }
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('finance.facture.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/finance/factures']);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    return this.fb.group(
      {
        typeFacture: [TypeFacture.FACTURE, [Validators.required]],
        compteId: [null],
        clientId: [null],
        societeId: [null],
        reservationId: [null],
        fournisseurId: [null],
        dateFacture: [''],
        dateEcheance: [''],
        devise: ['MRU', [Validators.maxLength(3)]],
        commentaires: [''],
        lignes: this.fb.array<FormGroup>([]),
      },
      { validators: [this.atLeastOneCibleValidator] },
    );
  }

  private buildLigne(seed?: Partial<{
    typeLigne: TypeLigneFacture;
    nuiteeId: number;
    produitId: number;
    serviceId: number;
    commandeId: number;
    libelle: string;
    quantite: number;
    prixUnitaire: number;
    tauxTva: number;
    datePrestation: string;
  }>): FormGroup {
    return this.fb.group({
      typeLigne: new FormControl<TypeLigneFacture>(
        seed?.typeLigne ?? TypeLigneFacture.SERVICE,
        { validators: [Validators.required], nonNullable: true },
      ),
      nuiteeId: new FormControl<number | null>(seed?.nuiteeId ?? null),
      produitId: new FormControl<number | null>(seed?.produitId ?? null),
      serviceId: new FormControl<number | null>(seed?.serviceId ?? null),
      commandeId: new FormControl<number | null>(seed?.commandeId ?? null),
      libelle: new FormControl<string>(seed?.libelle ?? '', {
        validators: [Validators.required, Validators.maxLength(500)],
        nonNullable: true,
      }),
      quantite: new FormControl<number>(seed?.quantite ?? 1, {
        validators: [Validators.required, Validators.min(0.001)],
        nonNullable: true,
      }),
      prixUnitaire: new FormControl<number>(seed?.prixUnitaire ?? 0, {
        validators: [Validators.required, Validators.min(0)],
        nonNullable: true,
      }),
      tauxTva: new FormControl<number | null>(seed?.tauxTva ?? null),
      datePrestation: new FormControl<string | null>(
        seed?.datePrestation ?? null,
      ),
    });
  }

  private subscribeTotal(): void {
    this.form.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.totalCalcule = this.lignes.controls.reduce((acc, g) => {
          const qte = Number(g.get('quantite')?.value ?? 0);
          const prix = Number(g.get('prixUnitaire')?.value ?? 0);
          const sous = qte * prix;
          return acc + (Number.isFinite(sous) ? sous : 0);
        }, 0);
      });
  }

  /**
   * Validateur de groupe : au moins une cible (compteId, clientId,
   * societeId, reservationId, fournisseurId) doit être renseignée.
   */
  private readonly atLeastOneCibleValidator = (
    group: FormGroup,
  ): { atLeastOneCible: true } | null => {
    const champs = [
      'compteId',
      'clientId',
      'societeId',
      'reservationId',
      'fournisseurId',
    ];
    const anyFilled = champs.some((nom) => {
      const v = group.get(nom)?.value;
      return v != null && Number(v) > 0;
    });
    return anyFilled ? null : { atLeastOneCible: true };
  };

  /**
   * Convertit une valeur de formulaire (`null`, `''`, `0`, `'42'`) en
   * `number` strictement positif ou `undefined` (pour ne pas envoyer un
   * `0` au backend qui le confondrait avec une absence).
   */
  private toIdOrUndef(value: number | string | null | undefined): number | undefined {
    if (value == null || value === '') return undefined;
    const n = Number(value);
    return Number.isFinite(n) && n > 0 ? n : undefined;
  }
}
