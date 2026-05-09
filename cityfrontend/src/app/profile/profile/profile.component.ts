import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { AuthService, UserInfo } from '../../services/auth.service';
import { TranslationService } from '../../services/translation.service';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss'],
  standalone: false
})
export class ProfileComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  currentUser: UserInfo | null = null;
  profileForm: FormGroup;
  passwordForm: FormGroup;
  isLoading = false;
  isSaving = false;
  activeTab = 'profile';
  showCurrentPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;
  successMessage = '';
  errorMessage = '';

  constructor(
    private formBuilder: FormBuilder,
    private authService: AuthService,
    public translationService: TranslationService
  ) {
    this.profileForm = this.formBuilder.group({
      prenom: ['', [Validators.required, Validators.minLength(2)]],
      nom: ['', [Validators.required, Validators.minLength(2)]],
      email: ['', [Validators.required, Validators.email]],
      telephone: [''],
      poste: ['']
    });

    this.passwordForm = this.formBuilder.group({
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]]
    }, {
      validators: this.passwordMatchValidator
    });
  }

  ngOnInit(): void {
    // S'abonner aux changements d'utilisateur
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.currentUser = user;
        if (user) {
          this.populateForm(user);
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Remplir le formulaire avec les données utilisateur
   */
  public populateForm(user: UserInfo): void {
    this.profileForm.patchValue({
      prenom: user.prenom,
      nom: user.nom,
      email: user.email,
      telephone: '', // À récupérer depuis le backend
      poste: '' // À récupérer depuis le backend
    });
  }

  /**
   * Validateur pour vérifier que les mots de passe correspondent
   */
  private passwordMatchValidator(form: FormGroup) {
    const newPassword = form.get('newPassword');
    const confirmPassword = form.get('confirmPassword');
    
    if (newPassword && confirmPassword && newPassword.value !== confirmPassword.value) {
      confirmPassword.setErrors({ passwordMismatch: true });
      return { passwordMismatch: true };
    }
    
    return null;
  }

  /**
   * Changer d'onglet
   */
  setActiveTab(tab: string): void {
    this.activeTab = tab;
    this.clearMessages();
  }

  /**
   * Basculer l'affichage du mot de passe
   */
  togglePasswordVisibility(field: string): void {
    switch (field) {
      case 'current':
        this.showCurrentPassword = !this.showCurrentPassword;
        break;
      case 'new':
        this.showNewPassword = !this.showNewPassword;
        break;
      case 'confirm':
        this.showConfirmPassword = !this.showConfirmPassword;
        break;
    }
  }

  /**
   * Sauvegarder le profil
   */
  onSaveProfile(): void {
    if (this.profileForm.invalid) {
      this.markFormGroupTouched(this.profileForm);
      return;
    }

    this.isSaving = true;
    this.clearMessages();

    // Simuler la sauvegarde (à remplacer par un appel API)
    setTimeout(() => {
      try {
        // TODO: Appeler le service pour mettre à jour le profil
        this.successMessage = 'Profil mis à jour avec succès';
        this.isSaving = false;
        
        // Masquer le message après 3 secondes
        setTimeout(() => {
          this.successMessage = '';
        }, 3000);
      } catch (error) {
        this.errorMessage = 'Erreur lors de la mise à jour du profil';
        this.isSaving = false;
      }
    }, 1000);
  }

  /**
   * Changer le mot de passe
   */
  onChangePassword(): void {
    if (this.passwordForm.invalid) {
      this.markFormGroupTouched(this.passwordForm);
      return;
    }

    this.isSaving = true;
    this.clearMessages();

    const formData = this.passwordForm.value;

    // Simuler le changement de mot de passe (à remplacer par un appel API)
    setTimeout(() => {
      try {
        // TODO: Appeler le service pour changer le mot de passe
        // this.authService.updatePassword(userId, formData.currentPassword, formData.newPassword)
        
        this.successMessage = 'Mot de passe modifié avec succès';
        this.passwordForm.reset();
        this.isSaving = false;
        
        // Masquer le message après 3 secondes
        setTimeout(() => {
          this.successMessage = '';
        }, 3000);
      } catch (error) {
        this.errorMessage = 'Erreur lors du changement de mot de passe';
        this.isSaving = false;
      }
    }, 1000);
  }

  /**
   * Vérifier si un champ a une erreur
   */
  hasFieldError(form: FormGroup, fieldName: string): boolean {
    const field = form.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  /**
   * Obtenir le message d'erreur d'un champ
   */
  getFieldError(form: FormGroup, fieldName: string): string {
    const field = form.get(fieldName);
    if (!field || !field.errors) {
      return '';
    }

    if (field.errors['required']) {
      return `Ce champ est obligatoire`;
    }
    
    if (field.errors['minlength']) {
      const requiredLength = field.errors['minlength'].requiredLength;
      return `Minimum ${requiredLength} caractères requis`;
    }

    if (field.errors['email']) {
      return 'Format d\'email invalide';
    }

    if (field.errors['passwordMismatch']) {
      return 'Les mots de passe ne correspondent pas';
    }

    return 'Champ invalide';
  }

  /**
   * Marquer tous les champs comme touchés
   */
  private markFormGroupTouched(form: FormGroup): void {
    Object.keys(form.controls).forEach(key => {
      const control = form.get(key);
      if (control) {
        control.markAsTouched();
      }
    });
  }

  /**
   * Effacer les messages
   */
  private clearMessages(): void {
    this.successMessage = '';
    this.errorMessage = '';
  }

  /**
   * Obtenir l'avatar de l'utilisateur
   */
  getUserAvatar(): string {
    if (this.currentUser?.userId) {
      return `assets/images/avatars/user-${this.currentUser.userId}.jpg`;
    }
    return 'assets/images/default-avatar.png';
  }

  /**
   * Obtenir les initiales de l'utilisateur
   */
  getUserInitials(): string {
    if (this.currentUser) {
      const firstInitial = this.currentUser.prenom?.charAt(0).toUpperCase() || '';
      const lastInitial = this.currentUser.nom?.charAt(0).toUpperCase() || '';
      return firstInitial + lastInitial;
    }
    return 'U';
  }

  /**
   * Obtenir la couleur de l'avatar selon le rôle
   */
  getAvatarColor(): string {
    if (!this.currentUser) return 'bg-secondary';
    
    switch (this.currentUser.roleCode) {
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

  /**
   * Gérer l'erreur d'image
   */
  onImageError(event: any): void {
    event.target.style.display = 'none';
  }

  /**
   * Télécharger une nouvelle photo de profil
   */
  onFileSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      // TODO: Implémenter l'upload de photo
      console.log('Photo sélectionnée:', file);
    }
  }
}