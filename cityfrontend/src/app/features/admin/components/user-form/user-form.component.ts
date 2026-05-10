import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Role } from '../../models/role.admin.model';
import { User } from '../../models/user.admin.model';
import { RolesAdminService } from '../../services/roles.admin.service';
import { UsersAdminService } from '../../services/users.admin.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un utilisateur (vue SUPERADMIN).
 *
 * `hotelId` est lu du **path param** `:hotelId` (route
 * `/admin/hotels/:hotelId/users/new` ou `/admin/hotels/:hotelId/users/:userId`).
 * C'est l'unique endroit du front où on accepte un `hotelId` côté UI —
 * ce n'est pas un champ libre du formulaire mais une cible explicite
 * de la route SUPERADMIN-only (cf. JSDoc de `UsersAdminService` pour
 * la justification de cette exception).
 *
 * Validators :
 *  - `username` : required + maxLength(50) + pattern alphanumérique. **Désactivé en édition.**
 *  - `password` : required + minLength(8) en création. **Désactivé en édition**
 *    (un changement de mdp se fait via l'action « reset password » de la liste).
 *  - `email` : required + format email
 *  - `prenom` / `nom` : required + maxLength(100)
 *  - `roleCode` : required (sélection dans le référentiel `/admin/roles`)
 *  - `telephone` : optionnel + maxLength(20)
 */
@Component({
  selector: 'app-admin-user-form',
  templateUrl: './user-form.component.html',
  styleUrls: ['./user-form.component.scss'],
  standalone: false,
})
export class UserFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'ready';
  hotelId: number | null = null;
  editingId: number | null = null;
  roles: Role[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly usersService: UsersAdminService,
    private readonly rolesService: RolesAdminService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.loadRoles();

    const hotelIdParam = this.route.snapshot.paramMap.get('hotelId');
    if (!hotelIdParam) {
      this.state = 'error';
      return;
    }
    const hotelId = Number(hotelIdParam);
    if (!Number.isFinite(hotelId)) {
      this.state = 'error';
      return;
    }
    this.hotelId = hotelId;

    const userIdParam = this.route.snapshot.paramMap.get('userId');
    if (userIdParam && userIdParam !== 'new') {
      const userId = Number(userIdParam);
      if (!Number.isFinite(userId)) {
        this.state = 'error';
        return;
      }
      this.editingId = userId;
      this.loadExisting(this.hotelId, userId);
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
    if (this.form.invalid || this.hotelId == null) {
      this.form.markAllAsTouched();
      return;
    }
    this.state = 'submitting';
    const raw = this.form.getRawValue();

    const payload: User = {
      username: String(raw.username).trim(),
      email: String(raw.email).trim(),
      prenom: String(raw.prenom).trim(),
      nom: String(raw.nom).trim(),
      telephone: raw.telephone ? String(raw.telephone).trim() : undefined,
      roleCode: String(raw.roleCode),
      actif: raw.actif !== false,
    };

    // Le password n'est envoyé qu'en création (immutable en édition).
    if (!this.editingId) {
      payload.password = String(raw.password);
    }

    const obs$ = this.editingId
      ? this.usersService.update(this.hotelId, this.editingId, payload)
      : this.usersService.create(this.hotelId, payload);

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
            ? 'admin.users.messages.updateSuccess'
            : 'admin.users.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/admin/users'], {
            queryParams: { hotelId: this.hotelId },
          });
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('admin.users.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/admin/users'], {
      queryParams: { hotelId: this.hotelId },
    });
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    return this.fb.group({
      username: [
        '',
        [
          Validators.required,
          Validators.maxLength(50),
          Validators.pattern(/^[A-Za-z0-9._-]+$/),
        ],
      ],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
      prenom: ['', [Validators.required, Validators.maxLength(100)]],
      nom: ['', [Validators.required, Validators.maxLength(100)]],
      telephone: ['', [Validators.maxLength(20)]],
      roleCode: ['', [Validators.required]],
      actif: [true],
    });
  }

  private loadRoles(): void {
    this.rolesService
      .findAll()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of([] as Role[])),
      )
      .subscribe((roles) => (this.roles = roles));
  }

  private loadExisting(hotelId: number, userId: number): void {
    this.state = 'loading';
    this.usersService
      .findById(hotelId, userId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (u) => {
          this.hydrateForm(u);
          // En édition : username + password verrouillés.
          this.form.get('username')?.disable({ emitEvent: false });
          this.form.get('password')?.disable({ emitEvent: false });
          this.form.get('password')?.clearValidators();
          this.form.get('password')?.updateValueAndValidity({ emitEvent: false });
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(u: User): void {
    this.form.patchValue({
      username: u.username ?? '',
      password: '', // jamais récupéré du serveur
      email: u.email ?? '',
      prenom: u.prenom ?? '',
      nom: u.nom ?? '',
      telephone: u.telephone ?? '',
      roleCode: u.roleCode ?? '',
      actif: u.actif !== false,
    });
  }
}
