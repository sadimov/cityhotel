import { createReducer, on } from '@ngrx/store';

import { UiActions } from './ui.actions';
import { UiState, initialUiState } from './ui.state';

/**
 * Reducer du feature store `ui`. Aucune logique HTTP / DOM ici — c'est l'effect
 * qui se charge de la persistance localStorage et de la synchronisation
 * `dir="rtl"` sur `<html>`.
 */
export const uiReducer = createReducer(
  initialUiState,

  on(
    UiActions.hydratePreferencesSuccess,
    (state, { currentLang, sidebarCollapsed }): UiState => ({
      ...state,
      currentLang,
      sidebarCollapsed,
    }),
  ),

  on(
    UiActions.setLanguage,
    (state, { lang }): UiState => ({ ...state, currentLang: lang }),
  ),

  on(
    UiActions.toggleSidebar,
    (state): UiState => ({ ...state, sidebarCollapsed: !state.sidebarCollapsed }),
  ),

  on(
    UiActions.setSidebarCollapsed,
    (state, { collapsed }): UiState => ({ ...state, sidebarCollapsed: collapsed }),
  ),

  on(
    UiActions.setTheme,
    (state, { theme }): UiState => ({ ...state, theme }),
  ),
);
