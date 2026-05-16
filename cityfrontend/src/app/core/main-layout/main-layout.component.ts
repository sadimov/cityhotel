import { Component, OnInit, OnDestroy } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Subject, filter, takeUntil } from 'rxjs';
import { AuthService, UserInfo } from '../../services/auth.service';
import { TranslationService } from '../../services/translation.service';

@Component({
  selector: 'app-main-layout',
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.scss'],
  standalone: false
})
export class MainLayoutComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  currentUser: UserInfo | null = null;
  isLoading = true;
  isFullscreenRoute = false;

  /**
   * Routes plein-écran : padding/maxWidth du wrapper neutralisés pour
   * libérer toute la place horizontale et verticale. Cohérent avec le
   * sidebar qui passe en mode minimisé sur ces routes.
   */
  private static readonly FULLSCREEN_ROUTES: readonly string[] = [
    '/hebergement/calendar',
    '/hebergement/reservations',
    '/hebergement',
    '/restaurant/pos',
  ];

  constructor(
    private authService: AuthService,
    public translationService: TranslationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    // S'abonner aux changements d'utilisateur
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.currentUser = user;
        this.isLoading = false;
      });

    // S'abonner aux changements de langue pour mettre à jour la direction
    this.translationService.currentLanguage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(language => {
        this.updateDocumentDirection(language);
      });

    // Initialiser la direction du document
    this.updateDocumentDirection(this.translationService.getCurrentLanguage());

    // Détecter les routes fullscreen pour neutraliser padding/min-height
    this.updateFullscreenFlag(this.router.url);
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntil(this.destroy$)
      )
      .subscribe(event => this.updateFullscreenFlag(event.url));
  }

  private updateFullscreenFlag(url: string): void {
    this.isFullscreenRoute = MainLayoutComponent.FULLSCREEN_ROUTES.some(prefix =>
      url === prefix || url.startsWith(prefix + '/') || url.startsWith(prefix + '?')
    );
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Mettre à jour la direction du document selon la langue
   */
  private updateDocumentDirection(language: string): void {
    const direction = language === 'ar' ? 'rtl' : 'ltr';
    document.documentElement.setAttribute('dir', direction);
    document.documentElement.setAttribute('lang', language);
  }
}