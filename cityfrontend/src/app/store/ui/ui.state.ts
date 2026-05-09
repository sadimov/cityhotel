/**
 * State du feature store `ui`.
 *
 * Préférences d'affichage globales :
 * - `currentLang` : langue active (fr / ar / en)
 * - `sidebarCollapsed` : sidebar pliée ou dépliée
 * - `theme` : thème courant (réservé futur, défaut `'light'`)
 *
 * `currentLang` et `sidebarCollapsed` sont persistés dans `localStorage`
 * via les effects (cf. `ui.effects.ts`).
 */

export type Lang = 'fr' | 'ar' | 'en';
export type Theme = 'light' | 'dark';

export interface UiState {
  currentLang: Lang;
  sidebarCollapsed: boolean;
  theme: Theme;
}

export const UI_FEATURE_KEY = 'ui';

export const DEFAULT_LANG: Lang = 'fr';
export const SUPPORTED_LANGS: ReadonlyArray<Lang> = ['fr', 'ar', 'en'];

export const initialUiState: UiState = {
  currentLang: DEFAULT_LANG,
  sidebarCollapsed: false,
  theme: 'light',
};
