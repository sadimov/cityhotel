import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { TranslationService } from './translation.service';

describe('TranslationService', () => {
  let service: TranslationService;
  let translate: TranslateService;

  beforeEach(() => {
    localStorage.removeItem('city_hotel_language');

    TestBed.configureTestingModule({
      imports: [
        HttpClientTestingModule,
        TranslateModule.forRoot({
          defaultLanguage: 'fr',
        }),
      ],
      providers: [TranslationService],
    });

    translate = TestBed.inject(TranslateService);
    // Précharger des traductions de test pour `instant()`
    translate.setTranslation('fr', { auth: { login: 'Connexion' } }, true);
    translate.setTranslation('en', { auth: { login: 'Login' } }, true);

    service = TestBed.inject(TranslationService);
  });

  it('crée le service avec la langue par défaut fr', () => {
    expect(service).toBeTruthy();
    expect(service.getCurrentLanguage()).toBe('fr');
  });

  it('translate(key) retourne la traduction', () => {
    expect(service.translate('auth.login')).toBe('Connexion');
  });

  it('translate(key, fallback) retourne le fallback si la clé est absente', () => {
    expect(service.translate('inconnu.cle', 'Mon fallback')).toBe('Mon fallback');
  });

  it('translate(key, fallback) retourne la traduction si la clé existe', () => {
    expect(service.translate('auth.login', 'Fallback')).toBe('Connexion');
  });

  it('setLanguage(en) change la langue, persiste et émet', (done) => {
    service.currentLanguage$.subscribe((lang) => {
      if (lang === 'en') {
        expect(localStorage.getItem('city_hotel_language')).toBe('en');
        expect(document.documentElement.lang).toBe('en');
        done();
      }
    });
    service.setLanguage('en');
  });

  it('setLanguage(ar) applique dir=rtl', () => {
    service.setLanguage('ar');
    expect(document.documentElement.dir).toBe('rtl');
  });

  it('setLanguage rejette une langue non supportée', () => {
    service.setLanguage('fr');
    service.setLanguage('xx' as never);
    expect(service.getCurrentLanguage()).toBe('fr');
  });
});
