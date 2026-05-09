import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService, LoginRequest } from '../../services/auth.service';
import { TranslationService, Language } from '../../services/translation.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss'],
  standalone: false
})
export class LoginComponent implements OnInit {
  loginForm: FormGroup;
  loading = false;
  error = '';
  returnUrl = '';
  showPassword = false;

  constructor(
    private formBuilder: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    public translationService: TranslationService
  ) {
    // Rediriger si déjà connecté
    if (this.authService.isAuthenticated()) {
      this.authService.redirectToRoleBasedDashboard();
    }

    this.loginForm = this.formBuilder.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      rememberMe: [false]
    });
  }

  ngOnInit(): void {
    // Récupérer l'URL de retour
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '';
  }

  /**
   * Soumission du formulaire de connexion
   */
  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.markFormGroupTouched();
      return;
    }

    this.loading = true;
    this.error = '';

    const loginRequest: LoginRequest = {
      username: this.loginForm.value.username.trim(),
      password: this.loginForm.value.password,
      rememberMe: this.loginForm.value.rememberMe
    };

    this.authService.login(loginRequest).subscribe({
      next: (response) => {
        console.log('Connexion réussie:', response);

        // Rediriger vers l'URL de retour ou vers le dashboard approprié
        if (this.returnUrl) {
          this.router.navigateByUrl(this.returnUrl);
        } else {
          this.authService.redirectToRoleBasedDashboard();
        }
      },
      error: (error) => {
        console.error('Erreur de connexion:', error);
        this.error = error;
        this.loading = false;
      }
    });
  }

  /**
   * Basculer l'affichage du mot de passe
   */
  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }

  /**
   * Changer la langue
   */
  changeLanguage(language: Language): void {
    this.translationService.setLanguage(language);
  }

  /**
   * Obtenir la langue actuelle
   */
  getCurrentLanguage(): Language {
    return this.translationService.getCurrentLanguage();
  }

  /**
   * Vérifier si un champ a une erreur
   */
  hasFieldError(fieldName: string): boolean {
    const field = this.loginForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  /**
   * Obtenir le message d'erreur d'un champ
   */
  getFieldError(fieldName: string): string {
    const field = this.loginForm.get(fieldName);
    if (!field || !field.errors) {
      return '';
    }

    if (field.errors['required']) {
      return `${this.translationService.translate('auth.' + fieldName)} est obligatoire`;
    }
    
    if (field.errors['minlength']) {
      const requiredLength = field.errors['minlength'].requiredLength;
      return `Minimum ${requiredLength} caractères requis`;
    }

    return 'Champ invalide';
  }

  /**
   * Marquer tous les champs comme touchés pour afficher les erreurs
   */
  private markFormGroupTouched(): void {
    Object.keys(this.loginForm.controls).forEach(key => {
      const control = this.loginForm.get(key);
      if (control) {
        control.markAsTouched();
      }
    });
  }

  /**
   * Gérer le mot de passe oublié
   */
  onForgotPassword(): void {
    // TODO: Implémenter la fonctionnalité de mot de passe oublié
    alert('Fonctionnalité en cours de développement');
  }
}