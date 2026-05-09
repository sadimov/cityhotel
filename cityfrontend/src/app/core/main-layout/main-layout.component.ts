import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
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

  constructor(
    private authService: AuthService,
    public translationService: TranslationService
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