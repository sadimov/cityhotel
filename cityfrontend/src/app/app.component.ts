import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { Subject, takeUntil } from 'rxjs';
import { TranslationService } from './services/translation.service';
import { AuthService } from './services/auth.service';
import { AuthActions } from './store/auth/auth.actions';
import { UiActions } from './store/ui/ui.actions';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
  standalone: false
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'City Hotel - Gestion Hôtelière';
  private destroy$ = new Subject<void>();

  private translationService = inject(TranslationService);
  private authService = inject(AuthService);
  private store = inject(Store);

  ngOnInit(): void {
    // Tour 5B : hydrater les stores NgRx au démarrage de l'app.
    // - `AuthActions.bootstrap` : réhydrate `auth` depuis localStorage si JWT valide.
    // - `UiActions.hydratePreferences` : réhydrate `ui` (langue + sidebar) depuis localStorage.
    this.store.dispatch(AuthActions.bootstrap());
    this.store.dispatch(UiActions.hydratePreferences());

    // Initialiser la langue de l'application
    this.initializeLanguage();
    
    // Configurer la direction du document selon la langue
    this.translationService.currentLanguage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(language => {
        this.updateDocumentDirection(language);
      });

    // Initialiser l'authentification
    this.initializeAuth();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Initialiser la langue de l'application
   */
  private initializeLanguage(): void {
    // La langue est automatiquement initialisée par le service
    const currentLanguage = this.translationService.getCurrentLanguage();
    this.updateDocumentDirection(currentLanguage);
    
    console.log(`🌍 Application initialisée en ${currentLanguage.toUpperCase()}`);
  }

  /**
   * Mettre à jour la direction du document selon la langue
   */
  private updateDocumentDirection(language: string): void {
    const direction = language === 'ar' ? 'rtl' : 'ltr';
    document.documentElement.setAttribute('dir', direction);
    document.documentElement.setAttribute('lang', language);
    
    // Ajouter/supprimer la classe pour le style RTL
    if (language === 'ar') {
      document.body.classList.add('rtl');
      document.body.classList.remove('ltr');
    } else {
      document.body.classList.add('ltr');
      document.body.classList.remove('rtl');
    }

    // Mettre à jour le titre de la page
    this.updatePageTitle();
  }

  /**
   * Mettre à jour le titre de la page
   */
  private updatePageTitle(): void {
    const appName = this.translationService.translate('app.name');
    const subtitle = this.translationService.translate('app.subtitle');
    document.title = `${appName} - ${subtitle}`;
  }

  /**
   * Initialiser l'authentification
   */
  private initializeAuth(): void {
    // Vérifier l'état de l'authentification au démarrage
    if (this.authService.isAuthenticated()) {
      console.log('🔐 Utilisateur déjà authentifié');
      
      // Recharger les informations utilisateur si nécessaire
      this.authService.getCurrentUser().subscribe({
        next: (user) => {
          console.log('✅ Informations utilisateur rechargées:', user.username);
        },
        error: (error) => {
          console.warn('⚠️ Erreur lors du rechargement des informations utilisateur:', error);
          // En cas d'erreur, déconnecter l'utilisateur
          this.authService.logout().subscribe();
        }
      });
    } else {
      console.log('🔓 Utilisateur non authentifié');
    }
  }
}