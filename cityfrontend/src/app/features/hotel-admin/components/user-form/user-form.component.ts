import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Role } from '../../../admin/models/role.admin.model';
import { RolesAdminService } from '../../../admin/services/roles.admin.service';
import { HotelUser, HotelUserCreate, HotelUserUpdate } from '../../models/hotel-user.model';
import { HotelUsersService } from '../../services/hotel-users.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un utilisateur (vue ADMIN d'hôtel).
 *
 * Différences avec `features/admin/.../user-form` (vue SUPERADMIN) :
 *  - PAS de `hotelId` (tenant résolu côté serveur via JWT).
 *  - Sélecteur de rôle FILTRE `SUPERADMIN` et `ADMIN` (anti-escalation UI).
 *    Le backend rejette aussi côté serveur (`error.user.role.escalation.forbidden`).
 *  - Le `roleId` (et non `roleCode`) est envoyé au backend, conformément
 *    aux DTOs `DBUserCreateAdminDto` / `DBUserUpdateAdminDto`.
 *  - En édition, `username` est en lecture seule ; le mot de passe se gère
 *    via l'action « réinitialiser » de la liste.
 */
@Component({
  selector: 'app-hotel-admin-user-form',
  templateUrl: './user-form.component.html',
  styleUrls: ['./user-form.component.scss'],
  standalone: false,
})
export class UserFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'ready';
  editingId: number | null = null;
  roles: Role[] = [];

  /** Codes de rôles interdits par anti-escalation. */
  private static readonly FORBIDDEN_ROLES: ReadonlyArray<string> = ['SUPERADMIN', 'ADMIN'];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly usersService: HotelUsersService,
    private readonly rolesService: RolesAdminService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.loadRoles();

    const userIdParam = this.route.snapshot.paramMap.get('id');
    if (userIdParam && userIdParam !== 'new') {
      const userId = Number(userIdParam);
      if (!Number.isFinite(userId)) {
        this.state = 'error';
        return;
      }
      this.editingId = userId;
      this.loadExisting(userId);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get isEditing(): boolean {
    return this.editingId !== null;
  }

  /** Liste filtrée des rôles (SUPERADMIN/ADMIN exclus pour anti-escalation UI). */
  get availableRoles(): Role[] {
    return this.roles.filter((r) => !UserFormComponent.FORBIDDEN_ROLES.includes(r.code));
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.state = 'submitting';
    const raw = this.form.getRawValue();

    if (this.editingId) {
      const payload: HotelUserUpdate = {
        email: this.trimOrUndef(raw.email),
        prenom: this.trimOrUndef(raw.prenom),
        nom: this.trimOrUndef(raw.nom),
        telephone: this.trimOrUndef(raw.telephone),
        poste: this.trimOrUndef(raw.poste),
        roleId: Number(raw.roleId),
      };
      this.usersService
        .update(this.editingId, payload)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.state = this.state === 'submitting' ? 'ready' : this.state)),
        )
        .subscribe({
          next: () => this.onSaveSuccess(true),
          error: (err: HttpErrorResponse) => this.onSaveError(err),
        });
    } else {
      const payload: HotelUserCreate = {
        username: String(raw.username).trim(),
        password: String(raw.password),
        email: String(raw.email).trim(),
        prenom: String(raw.prenom).trim(),
        nom: String(raw.nom).trim(),
        telephone: this.trimOrUndef(raw.telephone),
        poste: this.trimOrUndef(raw.poste),
        roleId: Number(raw.roleId),
      };
      this.usersService
        .create(payload)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.state = this.state === 'submitting' ? 'ready' : this.state)),
        )
        .subscribe({
          next: () => this.onSaveSuccess(false),
          error: (err: HttpErrorResponse) => this.onSaveError(err),
        });
    }
  }

  cancelEdit(): void {
    this.router.navigate(['/hotel-admin/users']);
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
          Validators.minLength(3),
          Validators.maxLength(100),
          Validators.pattern(/^[A-Za-z0-9._-]+$/),
        ],
      ],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(100)]],
      prenom: ['', [Validators.required, Validators.maxLength(100)]],
      nom: ['', [Validators.required, Validators.maxLength(100)]],
      telephone: ['', [Validators.maxLength(20)]],
      poste: ['', [Validators.maxLength(100)]],
      roleId: [null, [Validators.required]],
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

  private loadExisting(userId: number): void {
    this.state = 'loading';
    this.usersService
      .findById(userId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (u) => {
          this.hydrateForm(u);
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

  private hydrateForm(u: HotelUser): void {
    this.form.patchValue({
      username: u.username ?? '',
      password: '',
      email: u.email ?? '',
      prenom: u.prenom ?? '',
      nom: u.nom ?? '',
      telephone: u.telephone ?? '',
      poste: u.poste ?? '',
      roleId: u.roleId ?? null,
    });
  }

  private trimOrUndef(value: unknown): string | undefined {
    if (typeof value !== 'string') {
      return undefined;
    }
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : undefined;
  }

  private onSaveSuccess(isEdit: boolean): void {
    const successKey = isEdit
      ? 'admin.users.messages.updateSuccess'
      : 'admin.users.messages.createSuccess';
    Swal.fire({
      icon: 'success',
      title: this.i18n.translate(successKey),
      timer: 1500,
      showConfirmButton: false,
    });
    this.router.navigate(['/hotel-admin/users']);
  }

  /**
   * Affiche l'erreur backend en traduisant la clé i18n si présente
   * (`error.user.role.escalation.forbidden`, `error.user.self.action.forbidden`,
   * `error.user.username.exists`, etc.).
   */
  private onSaveError(err: HttpErrorResponse): void {
    let title = '';
    const body = err.error;
    if (body && typeof body === 'object') {
      const candidate =
        (body as { error?: string }).error ??
        (body as { message?: string }).message ??
        null;
      if (typeof candidate === 'string' && candidate.startsWith('error.')) {
        const translated = this.i18n.translate(candidate, '');
        if (translated && translated !== candidate) {
          title = translated;
        }
      }
    }
    if (!title) {
      title = this.i18n.translate('admin.users.messages.saveError');
    }
    Swal.fire({ icon: 'error', title });
  }
}
