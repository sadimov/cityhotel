import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { Hotel } from '../../models/hotel.admin.model';
import { Role } from '../../models/role.admin.model';
import { FiltresUsers, ResetPasswordResponse, User } from '../../models/user.admin.model';
import { HotelsAdminService } from '../../services/hotels.admin.service';
import { RolesAdminService } from '../../services/roles.admin.service';
import { UsersAdminService } from '../../services/users.admin.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface UsersPageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresUsers;
}

/**
 * Liste cross-hotel des utilisateurs (vue SUPERADMIN).
 *
 * Filtres :
 *  - recherche debounce 300 ms
 *  - hôtel (alimenté par `?hotelId=` au démarrage si fourni)
 *  - rôle
 *  - statut actif / inactif / tous
 *
 * Actions ligne :
 *  - éditer (vers `/admin/hotels/{hotelId}/users/{userId}`)
 *  - verrouiller / déverrouiller
 *  - reset-password : modal SweetAlert2 affichant le mdp en clair
 *    une seule fois + bouton « copier dans le presse-papier ».
 *  - désactiver (soft delete)
 */
@Component({
  selector: 'app-admin-users-list',
  templateUrl: './users-list.component.html',
  styleUrls: ['./users-list.component.scss'],
  standalone: false,
})
export class UsersListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<User> | null = null;
  request: UsersPageRequest = {
    page: 0,
    size: 10,
    sortBy: 'username',
    sortDir: 'asc',
    filtres: {},
  };
  searchTerm = '';
  hotels: Hotel[] = [];
  roles: Role[] = [];
  busyId: number | null = null;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly usersService: UsersAdminService,
    private readonly hotelsService: HotelsAdminService,
    private readonly rolesService: RolesAdminService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    // Pré-filtre via query param ?hotelId=
    const hotelIdParam = this.route.snapshot.queryParamMap.get('hotelId');
    if (hotelIdParam) {
      const hotelId = Number(hotelIdParam);
      if (Number.isFinite(hotelId)) {
        this.request = {
          ...this.request,
          filtres: { ...this.request.filtres, hotelId },
        };
      }
    }
    this.loadReferentiels();
    this.load();
  }

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.state = 'loading';
    this.usersService
      .pageAll(
        this.request.filtres,
        this.request.page,
        this.request.size,
        this.request.sortBy,
        this.request.sortDir,
      )
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((p) => {
        if (!p) {
          return;
        }
        this.page = p;
        this.state = p.numberOfElements === 0 ? 'empty' : 'ready';
      });
  }

  onSearchChange(value: string): void {
    this.searchTerm = value;
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => {
      this.request = {
        ...this.request,
        page: 0,
        filtres: { ...this.request.filtres, search: value.trim() || undefined },
      };
      this.load();
    }, 300);
  }

  onHotelFilterChange(value: string): void {
    const hotelId = value ? Number(value) : undefined;
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, hotelId: Number.isFinite(hotelId) ? hotelId : undefined },
    };
    this.load();
  }

  onRoleFilterChange(value: string): void {
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, roleCode: value || undefined },
    };
    this.load();
  }

  onActifFilterChange(value: string): void {
    let actif: boolean | undefined;
    if (value === 'true') {
      actif = true;
    } else if (value === 'false') {
      actif = false;
    } else {
      actif = undefined;
    }
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, actif },
    };
    this.load();
  }

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) {
      return;
    }
    this.request = { ...this.request, page };
    this.load();
  }

  createNew(): void {
    // L'hôtel est désormais choisi via un champ du formulaire de création
    // (pas via le filtre liste). Si l'utilisateur a pré-sélectionné un
    // hôtel dans le filtre, on le passe en queryParam pour pré-remplir.
    const hotelId = this.request.filtres.hotelId;
    const extras = hotelId != null ? { queryParams: { hotelId } } : {};
    this.router.navigate(['/admin/users/new'], extras);
  }

  edit(user: User): void {
    if (user.userId == null || user.hotelId == null) {
      return;
    }
    this.router.navigate(['/admin/hotels', user.hotelId, 'users', user.userId]);
  }

  toggleVerrou(user: User): void {
    if (user.userId == null || user.hotelId == null) {
      return;
    }
    const hotelId = user.hotelId;
    const userId = user.userId;
    const isLocked = user.verrouille === true;
    const action$ = isLocked
      ? this.usersService.deverrouiller(hotelId, userId)
      : this.usersService.verrouiller(hotelId, userId);
    const confirmKey = isLocked
      ? 'admin.users.messages.deverrouillerConfirm'
      : 'admin.users.messages.verrouillerConfirm';
    const successKey = isLocked
      ? 'admin.users.messages.deverrouillerSuccess'
      : 'admin.users.messages.verrouillerSuccess';
    const errorKey = isLocked
      ? 'admin.users.messages.deverrouillerError'
      : 'admin.users.messages.verrouillerError';

    Swal.fire({
      title: this.i18n.translate(confirmKey),
      text: user.username,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('admin.actions.confirm'),
      cancelButtonText: this.i18n.translate('admin.actions.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.busyId = userId;
      action$
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.busyId = null;
          }),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate(successKey),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate(errorKey),
            });
          },
        });
    });
  }

  desactiver(user: User): void {
    if (user.userId == null || user.hotelId == null) {
      return;
    }
    const hotelId = user.hotelId;
    const userId = user.userId;
    Swal.fire({
      title: this.i18n.translate('admin.users.messages.desactiverConfirm'),
      text: user.username,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('admin.actions.confirm'),
      cancelButtonText: this.i18n.translate('admin.actions.cancel'),
      confirmButtonColor: '#d33',
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.busyId = userId;
      this.usersService
        .desactiver(hotelId, userId)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.busyId = null;
          }),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('admin.users.messages.desactiverSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('admin.users.messages.desactiverError'),
            });
          },
        });
    });
  }

  resetPassword(user: User): void {
    if (user.userId == null || user.hotelId == null) {
      return;
    }
    const hotelId = user.hotelId;
    const userId = user.userId;
    Swal.fire({
      title: this.i18n.translate('admin.users.messages.resetPasswordConfirm'),
      text: user.username,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('admin.actions.confirm'),
      cancelButtonText: this.i18n.translate('admin.actions.cancel'),
      confirmButtonColor: '#d33',
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.busyId = userId;
      this.usersService
        .resetPassword(hotelId, userId)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.busyId = null;
          }),
        )
        .subscribe({
          next: (response) => this.showResetPasswordDialog(response),
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('admin.users.messages.resetPasswordError'),
            });
          },
        });
    });
  }

  get pagesArray(): number[] {
    if (!this.page) {
      return [];
    }
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  /**
   * Charge les référentiels nécessaires aux filtres (rôles + hôtels).
   * Erreurs silencieuses : on garde la liste vide si une charge échoue
   * — la liste des users reste utilisable.
   */
  private loadReferentiels(): void {
    this.rolesService
      .findAll()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of([] as Role[])),
      )
      .subscribe((roles) => (this.roles = roles));

    // On charge un large échantillon (200) sans pagination — l'admin
    // SaaS gère un nombre raisonnable d'hôtels (< 100 dans la cible).
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

  private showResetPasswordDialog(response: ResetPasswordResponse): void {
    const labelTitle = this.i18n.translate('admin.users.resetPasswordModal.title');
    const labelUsername = this.i18n.translate('admin.users.resetPasswordModal.username');
    const labelNew = this.i18n.translate('admin.users.resetPasswordModal.newPassword');
    const labelHint = this.i18n.translate('admin.users.resetPasswordModal.hint');
    const labelCopy = this.i18n.translate('admin.users.resetPasswordModal.copy');
    const labelCopied = this.i18n.translate('admin.users.resetPasswordModal.copied');
    const labelClose = this.i18n.translate('admin.actions.close');

    const escapeHtml = (s: string): string =>
      s.replace(/[&<>"']/g, (ch) =>
        ({
          '&': '&amp;',
          '<': '&lt;',
          '>': '&gt;',
          '"': '&quot;',
          "'": '&#39;',
        })[ch] ?? ch,
      );

    const usernameSafe = escapeHtml(response.username);
    const passwordSafe = escapeHtml(response.newPassword);

    const html = `
      <div class="text-start">
        <p class="mb-2"><strong>${escapeHtml(labelUsername)} :</strong> <code>${usernameSafe}</code></p>
        <p class="mb-2"><strong>${escapeHtml(labelNew)} :</strong></p>
        <div class="d-flex gap-2 align-items-center">
          <input id="reset-pwd-value" type="text" readonly class="form-control" value="${passwordSafe}" />
          <button id="reset-pwd-copy" type="button" class="btn btn-outline-primary">${escapeHtml(labelCopy)}</button>
        </div>
        <p class="text-muted small mt-3 mb-0">${escapeHtml(labelHint)}</p>
      </div>
    `;

    Swal.fire({
      title: labelTitle,
      html,
      icon: 'success',
      showConfirmButton: true,
      confirmButtonText: labelClose,
      didOpen: () => {
        const btn = document.getElementById('reset-pwd-copy') as HTMLButtonElement | null;
        if (!btn) {
          return;
        }
        btn.addEventListener('click', () => {
          if (typeof navigator === 'undefined' || !navigator.clipboard) {
            return;
          }
          navigator.clipboard
            .writeText(response.newPassword)
            .then(() => {
              btn.textContent = labelCopied;
              btn.classList.remove('btn-outline-primary');
              btn.classList.add('btn-success');
              setTimeout(() => {
                btn.textContent = labelCopy;
                btn.classList.remove('btn-success');
                btn.classList.add('btn-outline-primary');
              }, 2000);
            })
            .catch(() => {
              // silencieux : navigateur hors HTTPS / autorisations refusées
            });
        });
      },
    });
  }
}
