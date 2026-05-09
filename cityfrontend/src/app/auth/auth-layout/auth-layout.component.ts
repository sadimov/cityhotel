import { Component, OnInit } from '@angular/core';
import { TranslationService } from '../../services/translation.service';

@Component({
  selector: 'app-auth-layout',
  templateUrl: './auth-layout.component.html',
  styleUrls: ['./auth-layout.component.scss'],
  standalone: false
})
export class AuthLayoutComponent implements OnInit {

  constructor(
    public translationService: TranslationService
  ) {}

  ngOnInit(): void {
    // Mettre à jour la direction du document selon la langue
    this.translationService.currentLanguage$.subscribe(language => {
      this.updateDocumentDirection(language);
    });

    // Initialiser la direction
    this.updateDocumentDirection(this.translationService.getCurrentLanguage());
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
    } else {
      document.body.classList.remove('rtl');
    }
  }
}
