import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { AuthService, UserInfo } from '../../services/auth.service';
import { ProfileService } from '../../services/profile.service';
import { Profile } from '../../services/profile.model';
import { TranslationService } from '../../services/translation.service';

/**
 * Page de profil self-service.
 *
 * Refondue Tour B (2026-05-13) :
 *  - Plus de `setTimeout` mockés — toutes les opérations passent par
 *    {@link ProfileService} (vrai HTTP backend).
 *  - Chargement initial via `GET /api/profile/me` pour récupérer téléphone /
 *    poste / avatarUrl (champs absents de `AuthService.currentUser$`).
 *  - Upload + suppression d'avatar avec validations front (taille / MIME)
 *    + reset de l'input file pour permettre une 2e tentative sur le même fichier.
 *  - Mapping des clés i18n d'erreurs backend (`error.user.password.*`) sur
 *    SweetAlert2 pour un retour utilisateur explicite.
 *  - Email reste affiché en lecture seule (le backend n'expose pas l'édition
 *    de l'email via le profil self-service — workflow de validation hors scope).
 */
@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss'],
  standalone: false,
})
export class ProfileComponent implements OnInit, OnDestroy {
  /** Taille max acceptée pour l'avatar : 2 MB (aligné backend). */
  private static readonly AVATAR_MAX_SIZE = 2 * 1024 * 1024;
  /** Types MIME autorisés pour l'avatar (aligné backend). */
  private static readonly AVATAR_ALLOWED_TYPES: ReadonlyArray<string> = [
    'image/jpeg',
    'image/png',
    'image/webp',
  ];

  private readonly destroy$ = new Subject<void>();

  /** UserInfo issu du JWT (auth) — sert au header avant le chargement du profil complet. */
  currentUser: UserInfo | null = null;
  /** Profil complet chargé via `GET /api/profile/me`. */
  profile: Profile | null = null;

  profileForm: FormGroup;
  passwordForm: FormGroup;

  isLoading = false;
  isSaving = false;
  isUploadingAvatar = false;

  activeTab: 'profile' | 'security' | 'preferences' = 'profile';
  showCurrentPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;

  successMessage = '';
  errorMessage = '';

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly profileService: ProfileService,
    public readonly translationService: TranslationService,
  ) {
    this.profileForm = this.fb.group({
      prenom: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      nom: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      email: [{ value: '', disabled: true }],
      telephone: ['', [Validators.maxLength(20)]],
      poste: ['', [Validators.maxLength(100)]],
    });

    this.passwordForm = this.fb.group(
      {
        currentPassword: ['', [Validators.required]],
        newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
        confirmPassword: ['', [Validators.required]],
      },
      { validators: this.passwordMatchValidator },
    );
  }

  ngOnInit(): void {
    // 1. Snapshot AuthService pour l'affichage immédiat (header avatar, badge rôle).
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe((user) => {
        this.currentUser = user;
      });

    // 2. Chargement du profil complet (téléphone, poste, avatarUrl, derniereConnexion...)
    this.loadProfile();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ────────────────────────────────────────────────────────────────────────
  // Chargement
  // ────────────────────────────────────────────────────────────────────────

  private loadProfile(): void {
    this.isLoading = true;
    this.profileService
      .getProfile()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.isLoading = false)),
      )
      .subscribe({
        next: (profile) => {
          this.profile = profile;
          this.populateForm(profile);
        },
        error: () => {
          this.errorMessage = this.translationService.translate(
            'profile.messages.loadError',
            'Impossible de charger votre profil.',
          );
        },
      });
  }

  /** Remplit le formulaire à partir du profil complet (priorité au backend). */
  populateForm(profile: Profile): void {
    this.profileForm.patchValue({
      prenom: profile.prenom,
      nom: profile.nom,
      email: profile.email,
      telephone: profile.telephone ?? '',
      poste: profile.poste ?? '',
    });
  }

  // ────────────────────────────────────────────────────────────────────────
  // Onglets
  // ────────────────────────────────────────────────────────────────────────

  setActiveTab(tab: 'profile' | 'security' | 'preferences'): void {
    this.activeTab = tab;
    this.clearMessages();
  }

  togglePasswordVisibility(field: 'current' | 'new' | 'confirm'): void {
    if (field === 'current') {
      this.showCurrentPassword = !this.showCurrentPassword;
    } else if (field === 'new') {
      this.showNewPassword = !this.showNewPassword;
    } else {
      this.showConfirmPassword = !this.showConfirmPassword;
    }
  }

  // ────────────────────────────────────────────────────────────────────────
  // Sauvegarde profil
  // ────────────────────────────────────────────────────────────────────────

  onSaveProfile(): void {
    if (this.profileForm.invalid) {
      this.markFormGroupTouched(this.profileForm);
      return;
    }
    this.isSaving = true;
    this.clearMessages();
    const raw = this.profileForm.getRawValue();

    this.profileService
      .updateProfile({
        prenom: String(raw.prenom).trim(),
        nom: String(raw.nom).trim(),
        telephone: raw.telephone ? String(raw.telephone).trim() : undefined,
        poste: raw.poste ? String(raw.poste).trim() : undefined,
      })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.isSaving = false)),
      )
      .subscribe({
        next: (profile) => {
          this.profile = profile;
          this.populateForm(profile);
          this.showSuccess('profile.messages.updateSuccess', 'Profil mis à jour avec succès');
        },
        error: (err: HttpErrorResponse) => {
          this.showHttpError(err, 'profile.messages.updateError', 'Impossible de mettre à jour le profil.');
        },
      });
  }

  // ────────────────────────────────────────────────────────────────────────
  // Changement de mot de passe
  // ────────────────────────────────────────────────────────────────────────

  onChangePassword(): void {
    if (this.passwordForm.invalid) {
      this.markFormGroupTouched(this.passwordForm);
      return;
    }
    this.isSaving = true;
    this.clearMessages();
    const raw = this.passwordForm.value;

    this.profileService
      .changePassword({
        ancienMotDePasse: String(raw.currentPassword),
        nouveauMotDePasse: String(raw.newPassword),
        confirmation: String(raw.confirmPassword),
      })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.isSaving = false)),
      )
      .subscribe({
        next: () => {
          this.passwordForm.reset();
          this.showSuccess('profile.messages.passwordSuccess', 'Mot de passe modifié avec succès');
        },
        error: (err: HttpErrorResponse) => {
          this.showHttpError(err, 'profile.messages.passwordError', 'Impossible de modifier le mot de passe.');
        },
      });
  }

  // ────────────────────────────────────────────────────────────────────────
  // Avatar
  // ────────────────────────────────────────────────────────────────────────

  /**
   * Validations côté front avant l'envoi (UX) :
   *  - taille ≤ 2 MB
   *  - MIME parmi jpeg/png/webp
   */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    // Reset pour permettre la re-sélection du même fichier après une erreur.
    input.value = '';

    if (!file) {
      return;
    }
    if (!ProfileComponent.AVATAR_ALLOWED_TYPES.includes(file.type)) {
      Swal.fire({
        icon: 'error',
        title: this.translationService.translate(
          'profile.avatar.errors.type',
          'Format non supporté. Utilisez JPG, PNG ou WebP.',
        ),
      });
      return;
    }
    if (file.size > ProfileComponent.AVATAR_MAX_SIZE) {
      Swal.fire({
        icon: 'error',
        title: this.translationService.translate(
          'profile.avatar.errors.size',
          'Fichier trop volumineux (max 2 Mo).',
        ),
      });
      return;
    }

    this.isUploadingAvatar = true;
    this.profileService
      .uploadAvatar(file)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.isUploadingAvatar = false)),
      )
      .subscribe({
        next: (profile) => {
          this.profile = profile;
          this.showSuccess('profile.avatar.uploadSuccess', 'Avatar mis à jour');
        },
        error: (err: HttpErrorResponse) => {
          this.showHttpError(err, 'profile.avatar.uploadError', "Impossible de téléverser l'avatar.");
        },
      });
  }

  onDeleteAvatar(): void {
    if (!this.profile?.avatarUrl) {
      return;
    }
    Swal.fire({
      title: this.translationService.translate('profile.avatar.deleteConfirm', "Supprimer l'avatar ?"),
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.translationService.translate('admin.actions.confirm', 'Confirmer'),
      cancelButtonText: this.translationService.translate('admin.actions.cancel', 'Annuler'),
      confirmButtonColor: '#d33',
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.isUploadingAvatar = true;
      this.profileService
        .deleteAvatar()
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.isUploadingAvatar = false)),
        )
        .subscribe({
          next: (profile) => {
            this.profile = profile;
            this.showSuccess('profile.avatar.deleteSuccess', 'Avatar supprimé');
          },
          error: (err: HttpErrorResponse) => {
            this.showHttpError(err, 'profile.avatar.deleteError', "Impossible de supprimer l'avatar.");
          },
        });
    });
  }

  // ────────────────────────────────────────────────────────────────────────
  // Accesseurs / helpers d'affichage
  // ────────────────────────────────────────────────────────────────────────

  /** URL absolue de l'avatar pour `<img [src]>` ou `null` si pas d'avatar. */
  get avatarSrc(): string | null {
    return this.profileService.buildAvatarUrl(this.profile);
  }

  /** Initiales pour le fallback quand pas d'avatar. */
  getUserInitials(): string {
    const source = this.profile ?? this.currentUser;
    if (source) {
      const first = source.prenom?.charAt(0).toUpperCase() ?? '';
      const last = source.nom?.charAt(0).toUpperCase() ?? '';
      return `${first}${last}` || 'U';
    }
    return 'U';
  }

  /** Couleur du badge selon le rôle. */
  getAvatarColor(): string {
    const roleCode = this.profile?.roleCode ?? this.currentUser?.roleCode;
    switch (roleCode) {
      case 'SUPERADMIN':
        return 'bg-danger';
      case 'ADMIN':
        return 'bg-primary';
      case 'GERANT':
        return 'bg-success';
      case 'RECEPTION':
        return 'bg-info';
      case 'RESTAURANT':
        return 'bg-warning';
      case 'RESREC':
        return 'bg-purple';
      case 'MENAGE':
        return 'bg-teal';
      case 'MAGASIN':
        return 'bg-orange';
      default:
        return 'bg-secondary';
    }
  }

  /** Nom complet préférentiellement issu du profil chargé, fallback sur AuthService. */
  get displayName(): string {
    if (this.profile) {
      return this.profile.nomComplet || `${this.profile.prenom} ${this.profile.nom}`.trim();
    }
    return this.currentUser?.nomComplet ?? '';
  }

  get displayEmail(): string {
    return this.profile?.email ?? this.currentUser?.email ?? '';
  }

  get displayRoleNom(): string {
    return this.profile?.roleNom ?? this.currentUser?.roleNom ?? '';
  }

  get displayHotelNom(): string {
    return this.profile?.hotelNom ?? this.currentUser?.hotelNom ?? '';
  }

  /**
   * Vrai si l'utilisateur a un avatar côté serveur (active le bouton "Supprimer").
   */
  get hasAvatar(): boolean {
    return !!this.profile?.avatarUrl;
  }

  // ────────────────────────────────────────────────────────────────────────
  // Validation form
  // ────────────────────────────────────────────────────────────────────────

  hasFieldError(form: FormGroup, fieldName: string): boolean {
    const field = form.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  getFieldError(form: FormGroup, fieldName: string): string {
    const field = form.get(fieldName);
    if (!field || !field.errors) {
      return '';
    }
    if (field.errors['required']) {
      return this.translationService.translate('profile.errors.required', 'Ce champ est obligatoire');
    }
    if (field.errors['minlength']) {
      const len = field.errors['minlength'].requiredLength;
      return this.translationService.translate(
        'profile.errors.minLength',
        `Minimum ${len} caractères requis`,
      );
    }
    if (field.errors['maxlength']) {
      const len = field.errors['maxlength'].requiredLength;
      return this.translationService.translate(
        'profile.errors.maxLength',
        `Maximum ${len} caractères autorisés`,
      );
    }
    if (field.errors['email']) {
      return this.translationService.translate('profile.errors.email', "Format d'email invalide");
    }
    if (field.errors['passwordMismatch']) {
      return this.translationService.translate(
        'profile.errors.passwordMismatch',
        'Les mots de passe ne correspondent pas',
      );
    }
    return this.translationService.translate('profile.errors.invalid', 'Champ invalide');
  }

  private passwordMatchValidator(form: FormGroup) {
    const newPassword = form.get('newPassword');
    const confirmPassword = form.get('confirmPassword');
    if (newPassword && confirmPassword && newPassword.value !== confirmPassword.value) {
      confirmPassword.setErrors({ ...(confirmPassword.errors ?? {}), passwordMismatch: true });
      return { passwordMismatch: true };
    }
    if (confirmPassword?.errors) {
      const { passwordMismatch, ...rest } = confirmPassword.errors;
      void passwordMismatch;
      confirmPassword.setErrors(Object.keys(rest).length ? rest : null);
    }
    return null;
  }

  private markFormGroupTouched(form: FormGroup): void {
    Object.keys(form.controls).forEach((key) => {
      const control = form.get(key);
      control?.markAsTouched();
    });
  }

  // ────────────────────────────────────────────────────────────────────────
  // Messages / erreurs HTTP
  // ────────────────────────────────────────────────────────────────────────

  private clearMessages(): void {
    this.successMessage = '';
    this.errorMessage = '';
  }

  private showSuccess(key: string, fallback: string): void {
    const text = this.translationService.translate(key, fallback);
    this.successMessage = text;
    Swal.fire({ icon: 'success', title: text, timer: 1500, showConfirmButton: false });
  }

  /**
   * Traduit la clé d'erreur retournée par le backend (champ `error` ou `message`
   * dans la réponse JSON) en message i18n local. Sinon retombe sur `fallbackKey`.
   */
  private showHttpError(err: HttpErrorResponse, fallbackKey: string, fallbackText: string): void {
    const backendKey = this.extractErrorKey(err);
    let text: string | null = null;
    if (backendKey) {
      const translated = this.translationService.translate(backendKey, '');
      if (translated && translated !== backendKey) {
        text = translated;
      }
    }
    if (!text) {
      text = this.translationService.translate(fallbackKey, fallbackText);
    }
    this.errorMessage = text;
    Swal.fire({ icon: 'error', title: text });
  }

  /** Tente d'extraire une clé i18n depuis la réponse erreur backend. */
  private extractErrorKey(err: HttpErrorResponse): string | null {
    const body = err.error;
    if (body && typeof body === 'object') {
      const candidate =
        (body as { error?: string }).error ??
        (body as { message?: string }).message ??
        null;
      if (typeof candidate === 'string' && candidate.startsWith('error.')) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * Gestion d'erreur sur la balise `<img>` quand le serveur renvoie 404 sur
   * l'avatar (fichier supprimé hors UI, par exemple). On masque la photo
   * pour basculer sur les initiales.
   */
  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.style.display = 'none';
  }
}
