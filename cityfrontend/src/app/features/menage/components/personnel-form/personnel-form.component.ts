import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import {
  Personnel,
  SPECIALITES_PERSONNEL,
  SpecialitePersonnel,
} from '../../models/personnel.model';
import { PersonnelsService } from '../../services/personnels.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un personnel de ménage.
 *
 * Validators :
 *  - `numeroEmploye` : required + maxLength(20)
 *  - `prenom` / `nom` : required + maxLength(100)
 *  - `email` : optionnel + format email
 *  - `telephone` : optionnel + maxLength(20)
 *  - `dateEmbauche` : optionnel (date passée ou présente côté UX —
 *    pas de Validator strict, le backend reste juge)
 *  - `specialites` : saisie texte libre OU multi-select de codes connus.
 *    On gère la sérialisation `["chambres","salles"]` <-> texte
 *    « chambres, salles » (même approche qu'`allergenes` du module
 *    restaurant).
 *
 * Pattern aligné sur `restaurant/article-form` (Tour 23) et
 * `clients/client-form` (Tour 8).
 */
@Component({
  selector: 'app-personnel-form',
  templateUrl: './personnel-form.component.html',
  styleUrls: ['./personnel-form.component.scss'],
  standalone: false,
})
export class PersonnelFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'ready';
  editingId: number | null = null;
  readonly specialites: ReadonlyArray<SpecialitePersonnel> = SPECIALITES_PERSONNEL;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly personnelsService: PersonnelsService,
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

    const payload: Personnel = {
      numeroEmploye: String(raw.numeroEmploye).trim(),
      prenom: String(raw.prenom).trim(),
      nom: String(raw.nom).trim(),
      telephone: raw.telephone ? String(raw.telephone).trim() : undefined,
      email: raw.email ? String(raw.email).trim() : undefined,
      dateEmbauche: raw.dateEmbauche || undefined,
      specialites: this.serializeSpecialites(raw.specialites),
      actif: raw.actif !== false,
    };

    const obs$ = this.editingId
      ? this.personnelsService.update(this.editingId, payload)
      : this.personnelsService.create(payload);

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
            ? 'menage.personnel.messages.updateSuccess'
            : 'menage.personnel.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/menage/personnel']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('menage.personnel.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/menage/personnel']);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    return this.fb.group({
      numeroEmploye: [
        '',
        [Validators.required, Validators.maxLength(20)],
      ],
      prenom: [
        '',
        [Validators.required, Validators.minLength(2), Validators.maxLength(100)],
      ],
      nom: [
        '',
        [Validators.required, Validators.minLength(2), Validators.maxLength(100)],
      ],
      telephone: ['', [Validators.maxLength(20)]],
      email: ['', [Validators.email, Validators.maxLength(100)]],
      dateEmbauche: [''],
      specialites: [''],
      actif: [true],
    });
  }

  private loadExisting(id: number): void {
    this.state = 'loading';
    this.personnelsService
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

  private hydrateForm(p: Personnel): void {
    this.form.patchValue({
      numeroEmploye: p.numeroEmploye ?? '',
      prenom: p.prenom ?? '',
      nom: p.nom ?? '',
      telephone: p.telephone ?? '',
      email: p.email ?? '',
      dateEmbauche: p.dateEmbauche ?? '',
      specialites: this.deserializeSpecialites(p.specialites),
      actif: p.actif !== false,
    });
  }

  /**
   * Sérialise une saisie texte « chambres, salles » en JSON
   * `["chambres","salles"]`. Renvoie `undefined` si la liste est vide.
   */
  private serializeSpecialites(input: unknown): string | undefined {
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
   * Désérialise un JSON `["chambres","salles"]` en saisie texte
   * « chambres, salles ». Tolère un format brut.
   */
  private deserializeSpecialites(json: string | undefined): string {
    if (!json) {
      return '';
    }
    try {
      const parsed: unknown = JSON.parse(json);
      if (Array.isArray(parsed)) {
        return parsed.filter((x): x is string => typeof x === 'string').join(', ');
      }
    } catch {
      // valeur brute
    }
    return json;
  }
}
