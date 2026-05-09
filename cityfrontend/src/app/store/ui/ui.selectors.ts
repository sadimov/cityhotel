import { createFeatureSelector, createSelector } from '@ngrx/store';

import { UI_FEATURE_KEY, UiState } from './ui.state';

export const selectUiState = createFeatureSelector<UiState>(UI_FEATURE_KEY);

export const selectCurrentLang = createSelector(
  selectUiState,
  (state) => state.currentLang,
);

export const selectSidebarCollapsed = createSelector(
  selectUiState,
  (state) => state.sidebarCollapsed,
);

export const selectIsRtl = createSelector(
  selectCurrentLang,
  (lang) => lang === 'ar',
);

export const selectTheme = createSelector(
  selectUiState,
  (state) => state.theme,
);
