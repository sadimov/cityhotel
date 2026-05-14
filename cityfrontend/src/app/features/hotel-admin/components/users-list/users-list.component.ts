import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { PageResponse } from '../../../../shared/models/api.model';
import { AuthService } from '../../../../services/auth.service';
import { TranslationService } from '../../../../services/translation.service';
import { HotelUser, HotelUserResetPasswordResponse } from '../../models/hotel-user.model';
import { HotelUsersService } from '../../services/hotel-users.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste des utilisateurs du tenant courant (vue ADMIN d'hôtel).
 *
 * Actions par ligne :
 *  - éditer (vers `/hotel-admin/users/:id`)
 *  - verrouiller / déverrouiller
 *  - reset mot de passe (modal Swal copiable)
 *  - désactiver (soft delete)
 *
 * Anti-suicide UX : si la ligne courante correspond au user connecté,
 * les actions destructives (verrouiller, désactiver, reset-password) sont
 * MASQUÉES. Le bouton "Modifier" reste visible (l'ADMIN peut modifier ses
 * propres infos via cette UI ou via `/profile`).
 *
 * Backend bloque aussi ces actions (cf. `error.user.self.action.forbidden`) —
 * la garde UI ici est uniquement pour la clarté de l'expérience.
 */
@Component({
  selector: 'app-hotel-admin-users-list',
  templateUrl: './users-list.component.html',
  styleUrls: ['./users-list.component.scss'],
  standalone: false,
})
export class UsersListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<HotelUser> | null = null;
  request = {
    page: 0,
    size: 10,
    sortBy: 'username' as string,
    sortDir: 'asc' as 'asc' | 'desc',
  };
  busyId: number | null = null;
  currentUserId: number | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly usersService: HotelUsersService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.currentUserId = this.authService.getCurrentUserValue()?.userId ?? null;
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.state = 'loading';
    this.usersService
      .page(this.request.page, this.request.size, this.request.sortBy, this.request.sortDir)
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
        this.state = (p.numberOfElements ?? p.content.length) === 0 ? 'empty' : 'ready';
      });
  }

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) {
      return;
    }
    this.request = { ...this.request, page };
    this.load();
  }

  createNew(): void {
    this.router.navigate(['/hotel-admin/users/new']);
  }

  edit(user: HotelUser): void {
    if (user.userId == null) {
      return;
    }
    this.router.navigate(['/hotel-admin/users', user.userId]);
  }

  /** Vrai si cette ligne correspond à l'utilisateur courant (cache les actions destructives). */
  isSelf(user: HotelUser): boolean {
    return this.currentUserId != null && user.userId === this.currentUserId;
  }

  toggleVerrou(user: HotelUser): void {
    if (user.userId == null || this.isSelf(user)) {
      return;
    }
    const userId = user.userId;
    const isLocked = user.compteVerrouille === true;
    const action$ = isLocked
      ? this.usersService.deverrouiller(userId)
      : this.usersService.verrouiller(userId);
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
          finalize(() => (this.busyId = null)),
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
          error: (err: HttpErrorResponse) =>
            this.showError(err, errorKey),
        });
    });
  }

  desactiver(user: HotelUser): void {
    if (user.userId == null || this.isSelf(user)) {
      return;
    }
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
        .desactiver(userId)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.busyId = null)),
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
          error: (err: HttpErrorResponse) =>
            this.showError(err, 'admin.users.messages.desactiverError'),
        });
    });
  }

  resetPassword(user: HotelUser): void {
    if (user.userId == null || this.isSelf(user)) {
      return;
    }
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
        .resetPassword(userId)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.busyId = null)),
        )
        .subscribe({
          next: (response) => this.showResetPasswordDialog(response),
          error: (err: HttpErrorResponse) =>
            this.showError(err, 'admin.users.messages.resetPasswordError'),
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
   * Affiche une erreur backend. Si le payload contient une clé i18n
   * (`error.user.self.action.forbidden`, `error.user.role.escalation.forbidden`),
   * on la traduit ; sinon on retombe sur la clé fallback.
   */
  private showError(err: HttpErrorResponse, fallbackKey: string): void {
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
      title = this.i18n.translate(fallbackKey);
    }
    Swal.fire({ icon: 'error', title });
  }

  private showResetPasswordDialog(response: HotelUserResetPasswordResponse): void {
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
    const passwordSafe = escapeHtml(response.motDePasseTemporaire);

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
            .writeText(response.motDePasseTemporaire)
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
              // silencieux (HTTPS / autorisation refusée)
            });
        });
      },
    });
  }
}
