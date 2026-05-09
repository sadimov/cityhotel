import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Observable } from 'rxjs';

export type Language = 'fr' | 'ar' | 'en';

const SUPPORTED_LANGUAGES: ReadonlyArray<Language> = ['fr', 'ar', 'en'];
const DEFAULT_LANGUAGE: Language = 'fr';

/**
 * Wrapper synchrone autour de `@ngx-translate/core`.
 *
 * Conserve l'API publique historique (utilisée par ~7 composants) :
 *  - `currentLanguage$` : observable de la langue courante
 *  - `getCurrentLanguage()` / `setLanguage(lang)`
 *  - `translate(key, paramsOrFallback?)` : signature compatible
 *      - 2e arg `string`  -> fallback si la clé n'existe pas
 *      - 2e arg `object`  -> paramètres d'interpolation passés à ngx-translate
 *
 * Pilote en parallèle :
 *  - `<html dir>` (`rtl` pour l'arabe)
 *  - `<html lang>`
 *  - persistance dans `localStorage` sous la clé `city_hotel_language`.
 */
@Injectable({
  providedIn: 'root',
})
export class TranslationService {
  private readonly LANGUAGE_KEY = 'city_hotel_language';
  private readonly currentLanguageSubject: BehaviorSubject<Language>;
  public readonly currentLanguage$: Observable<Language>;

  constructor(private readonly ngxTranslate: TranslateService) {
    // Déclarer les langues supportées et la langue de fallback
    this.ngxTranslate.addLangs([...SUPPORTED_LANGUAGES]);
    this.ngxTranslate.setDefaultLang(DEFAULT_LANGUAGE);

    const initialLanguage = this.resolveInitialLanguage();
    this.currentLanguageSubject = new BehaviorSubject<Language>(initialLanguage);
    this.currentLanguage$ = this.currentLanguageSubject.asObservable();

    // Charger les traductions de la langue initiale + appliquer dir/lang
    this.applyLanguage(initialLanguage, /* persist */ false);
  }

  /**
   * Obtenir la langue actuelle.
   */
  getCurrentLanguage(): Language {
    return this.currentLanguageSubject.value;
  }

  /**
   * Changer la langue de l'application.
   * - Met à jour ngx-translate (`use`)
   * - Persiste dans `localStorage`
   * - Met à jour `<html dir>` et `<html lang>`
   * - Émet sur `currentLanguage$`
   */
  setLanguage(language: Language): void {
    if (!SUPPORTED_LANGUAGES.includes(language)) {
      return;
    }
    this.applyLanguage(language, /* persist */ true);
    this.currentLanguageSubject.next(language);
  }

  /**
   * Traduire une clé de manière synchrone.
   *
   * Signature compatible avec l'ancien service maison :
   * - `translate('auth.login')` → traduction simple
   * - `translate('auth.login', 'Fallback')` → fallback si clé absente
   * - `translate('greeting', { name: 'John' })` → interpolation ngx-translate
   *
   * Retourne la clé elle-même si aucune traduction n'est trouvée et
   * qu'aucun fallback n'a été fourni (comportement ngx-translate par défaut).
   */
  translate(key: string, paramsOrFallback?: string | Record<string, unknown>): string {
    if (typeof paramsOrFallback === 'string') {
      const value = this.ngxTranslate.instant(key);
      // ngx-translate renvoie la clé quand la traduction n'existe pas
      return value === key ? paramsOrFallback : value;
    }
    return this.ngxTranslate.instant(key, paramsOrFallback);
  }

  // ---------------------------------------------------------------------------
  // Méthodes obsolètes — gardées en stub no-op pour compat ascendante.
  // Aucun composant ne les utilise (vérifié par grep le 2026-05-06), mais on
  // les conserve avec un avertissement plutôt que de risquer une régression.
  // ---------------------------------------------------------------------------

  /** @deprecated Migrer vers les fichiers JSON `assets/i18n/{lang}.json`. */
  addTranslations(_newTranslations: unknown): void {
    console.warn(
      '[TranslationService] addTranslations() est obsolète. ' +
        'Ajoute les clés dans assets/i18n/{fr,ar,en}.json à la place.'
    );
  }

  /** @deprecated Utiliser `TranslateService.getTranslation(lang)` de ngx-translate. */
  getTranslations(_language?: Language): Record<string, string> {
    console.warn(
      '[TranslationService] getTranslations() est obsolète. ' +
        'Préfère TranslateService.getTranslation(lang) de @ngx-translate/core.'
    );
    return {};
  }

  /** @deprecated Tester via `TranslateService.instant(key) !== key`. */
  hasTranslation(key: string): boolean {
    return this.ngxTranslate.instant(key) !== key;
  }

  // ---------------------------------------------------------------------------
  // Privé
  // ---------------------------------------------------------------------------

  /**
   * Résout la langue à utiliser au démarrage :
   *  1. localStorage (si valeur supportée)
   *  2. langue navigateur (si supportée)
   *  3. fallback `fr`
   */
  private resolveInitialLanguage(): Language {
    if (typeof localStorage !== 'undefined') {
      const stored = localStorage.getItem(this.LANGUAGE_KEY) as Language | null;
      if (stored && SUPPORTED_LANGUAGES.includes(stored)) {
        return stored;
      }
    }

    if (typeof navigator !== 'undefined' && navigator.language) {
      const browserLang = navigator.language.substring(0, 2) as Language;
      if (SUPPORTED_LANGUAGES.includes(browserLang)) {
        return browserLang;
      }
    }

    return DEFAULT_LANGUAGE;
  }

  /**
   * Applique la langue : ngx-translate + dir/lang + persistance.
   */
  private applyLanguage(language: Language, persist: boolean): void {
    this.ngxTranslate.use(language);

    if (typeof document !== 'undefined') {
      document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
      document.documentElement.lang = language;
    }

    if (persist && typeof localStorage !== 'undefined') {
      localStorage.setItem(this.LANGUAGE_KEY, language);
    }
  }
}
