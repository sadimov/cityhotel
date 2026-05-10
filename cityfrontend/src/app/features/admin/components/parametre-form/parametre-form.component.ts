import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Parametre } from '../../models/parametre.admin.model';
import { ParametresAdminService } from '../../services/parametres.admin.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error' | 'locked';

/**
 * Formulaire création / édition d'un paramètre global (vue SUPERADMIN).
 *
 * Validators :
 *  - `cle` : required + maxLength(100) + pattern alphanumérique + `.` `_` `-`.
 *    En édition, la clé reste lisible mais éditable (le serveur applique
 *    sa propre règle d'unicité ; on n'a pas de contrainte d'immutabilité
 *    sur la clé contrairement au `code` hôtel).
 *  - `valeur` : required + maxLength(2000)
 *  - `categorie` / `description` / `type` : optionnels
 *
 * Verrouillage `modifiable=false` :
 *  - Le formulaire passe en `state = 'locked'` après le `findById` si
 *    le serveur retourne `modifiable=false`.
 *  - Tout le `FormGroup` est `disable()`d.
 *  - Une bannière d'alerte explique le verrouillage.
 *  - Le bouton « Enregistrer » est désactivé (et la requête PUT serait
 *    refusée 400 par le serveur de toute façon).
 */
@Component({
  selector: 'app-admin-parametre-form',
  templateUrl: './parametre-form.component.html',
  styleUrls: ['./parametre-form.component.scss'],
  standalone: false,
})
export class ParametreFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'ready';
  editingId: number | null = null;
  modifiable = true;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly parametresService: ParametresAdminService,
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

  get isLocked(): boolean {
    return this.state === 'locked';
  }

  submit(): void {
    if (this.form.invalid || this.isLocked) {
      this.form.markAllAsTouched();
      return;
    }
    this.state = 'submitting';
    const raw = this.form.getRawValue();

    const payload: Parametre = {
      cle: String(raw.cle).trim(),
      valeur: String(raw.valeur),
      description: raw.description ? String(raw.description).trim() : undefined,
      categorie: raw.categorie ? String(raw.categorie).trim() : undefined,
      type: raw.type ? String(raw.type).trim() : undefined,
      // En création, `modifiable` est piloté par l'utilisateur ; en édition
      // on conserve la valeur courante (le serveur fait foi de toute façon).
      modifiable: raw.modifiable !== false,
    };

    const obs$ = this.editingId
      ? this.parametresService.update(this.editingId, payload)
      : this.parametresService.create(payload);

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
            ? 'admin.parametres.messages.updateSuccess'
            : 'admin.parametres.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/admin/parametres']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('admin.parametres.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/admin/parametres']);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    return this.fb.group({
      cle: [
        '',
        [
          Validators.required,
          Validators.maxLength(100),
          Validators.pattern(/^[A-Za-z0-9._-]+$/),
        ],
      ],
      valeur: ['', [Validators.required, Validators.maxLength(2000)]],
      description: ['', [Validators.maxLength(500)]],
      categorie: ['', [Validators.maxLength(100)]],
      type: ['', [Validators.maxLength(20)]],
      modifiable: [true],
    });
  }

  private loadExisting(id: number): void {
    this.state = 'loading';
    this.parametresService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (p) => {
          this.modifiable = p.modifiable !== false;
          this.hydrateForm(p);
          if (!this.modifiable) {
            this.form.disable({ emitEvent: false });
            this.state = 'locked';
          } else {
            this.state = 'ready';
          }
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(p: Parametre): void {
    this.form.patchValue({
      cle: p.cle ?? '',
      valeur: p.valeur ?? '',
      description: p.description ?? '',
      categorie: p.categorie ?? '',
      type: p.type ?? '',
      modifiable: p.modifiable !== false,
    });
  }
}
