import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Hotel } from '../../models/hotel.admin.model';
import { Role } from '../../models/role.admin.model';
import { User } from '../../models/user.admin.model';
import { HotelsAdminService } from '../../services/hotels.admin.service';
import { RolesAdminService } from '../../services/roles.admin.service';
import { UsersAdminService } from '../../services/users.admin.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un utilisateur (vue SUPERADMIN).
 *
 * <h3>Deux modes de routage</h3>
 * <ul>
 *   <li><b>Mode standalone</b> (route `/admin/users/new` ou `/admin/users/:userId`) :
 *     `hotelId` est un <b>champ du formulaire</b>, l'utilisateur choisit
 *     l'hôtel par son nom dans un select. C'est le flux principal depuis
 *     la liste utilisateurs (consigne user 2026-05-17).</li>
 *   <li><b>Mode scoped hôtel</b> (route `/admin/hotels/:hotelId/users/...`) :
 *     `hotelId` est lu du path param, le champ select est masqué. Conservé
 *     pour les entrées depuis le détail d'un hôtel.</li>
 * </ul>
 *
 * Le champ `hotelId` est pré-rempli si le queryParam `?hotelId=X` est fourni
 * (cas où l'utilisateur avait un filtre actif dans la liste).
 *
 * Validators :
 *  - `hotelId` : required en mode standalone uniquement
 *  - `username` : required + maxLength(50) + pattern alphanumérique. <b>Désactivé en édition.</b>
 *  - `password` : required + minLength(8) en création. <b>Désactivé en édition</b>
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
  /** Hotel résolu (route path-param OU lu depuis le form après submit). */
  hotelId: number | null = null;
  /** `true` quand l'hôtel n'est PAS dans le path → afficher le select hôtel. */
  hotelInForm = false;
  editingId: number | null = null;
  roles: Role[] = [];
  hotels: Hotel[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly usersService: UsersAdminService,
    private readonly rolesService: RolesAdminService,
    private readonly hotelsService: HotelsAdminService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.loadRoles();

    // Mode 1 : path param `:hotelId` (route legacy `/admin/hotels/:hotelId/users/...`)
    const hotelIdParam = this.route.snapshot.paramMap.get('hotelId');
    if (hotelIdParam) {
      const hotelId = Number(hotelIdParam);
      if (!Number.isFinite(hotelId)) {
        this.state = 'error';
        return;
      }
      this.hotelId = hotelId;
      this.hotelInForm = false;
    } else {
      // Mode 2 : standalone (`/admin/users/new` ou `/admin/users/:userId`)
      // → hotelId vient du champ form. Préchargement de la liste hôtels.
      this.hotelInForm = true;
      this.form.addControl('hotelId', this.fb.control(null, [Validators.required]));
      this.loadHotels();

      // Pré-remplir le select si ?hotelId=X en queryParam (cas où la liste
      // avait un filtre actif au moment du clic « Nouvel utilisateur »).
      const queryHotelId = this.route.snapshot.queryParamMap.get('hotelId');
      if (queryHotelId) {
        const qh = Number(queryHotelId);
        if (Number.isFinite(qh)) {
          this.form.patchValue({ hotelId: qh });
        }
      }
    }

    const userIdParam = this.route.snapshot.paramMap.get('userId');
    if (userIdParam && userIdParam !== 'new') {
      const userId = Number(userIdParam);
      if (!Number.isFinite(userId)) {
        this.state = 'error';
        return;
      }
      this.editingId = userId;
      // En édition standalone, on a besoin du hotelId pour faire le GET ; le
      // backend `findById(hotelId, userId)` exige les deux. On résout en
      // récupérant la liste cross-hotel pour trouver l'hôtel du user — fait
      // dans loadExistingStandalone() ci-dessous.
      if (this.hotelId != null) {
        this.loadExisting(this.hotelId, userId);
      } else {
        this.loadExistingStandalone(userId);
      }
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
    // En mode standalone, hotelId vient du form ; en mode scoped, du path.
    const effectiveHotelId = this.hotelInForm
      ? Number(this.form.value.hotelId)
      : this.hotelId;
    if (effectiveHotelId == null || !Number.isFinite(effectiveHotelId)) {
      this.form.markAllAsTouched();
      return;
    }
    this.hotelId = effectiveHotelId;
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
      ? this.usersService.update(effectiveHotelId, this.editingId, payload)
      : this.usersService.create(effectiveHotelId, payload);

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

  /**
   * Charge un échantillon large (200) d'hôtels actifs pour le select de
   * création SUPERADMIN. La cible produit < 100 hôtels en pratique.
   */
  private loadHotels(): void {
    this.hotelsService
      .page({}, 0, 200, 'nom', 'asc')
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of(null)),
      )
      .subscribe((p) => {
        this.hotels = p?.content ?? [];
      });
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

  /**
   * Édition standalone (`/admin/users/:userId` sans hotelId path) : on lit
   * d'abord le User dans la liste cross-hotel pour récupérer son hotelId,
   * puis on fait le findById scoped. Le select hôtel reste affiché mais
   * désactivé (déplacement cross-hotel non géré ici).
   */
  private loadExistingStandalone(userId: number): void {
    this.state = 'loading';
    this.usersService
      .pageAll({}, 0, 1000, 'username', 'asc')
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (p) => {
          const found = p.content.find((u) => u.userId === userId);
          if (!found || found.hotelId == null) {
            this.state = 'error';
            return;
          }
          this.hotelId = found.hotelId;
          this.form.patchValue({ hotelId: found.hotelId });
          this.form.get('hotelId')?.disable({ emitEvent: false });
          this.loadExisting(found.hotelId, userId);
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
