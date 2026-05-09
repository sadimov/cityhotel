import { UiActions } from './ui.actions';
import { uiReducer } from './ui.reducer';
import { initialUiState } from './ui.state';

describe('uiReducer', () => {
  it('returns the initial state for unknown actions', () => {
    const state = uiReducer(undefined, { type: '@@unknown' } as never);
    expect(state).toEqual(initialUiState);
  });

  it('sets language on Set Language', () => {
    const next = uiReducer(
      initialUiState,
      UiActions.setLanguage({ lang: 'ar' }),
    );
    expect(next.currentLang).toBe('ar');
  });

  it('toggles sidebar', () => {
    const next = uiReducer(initialUiState, UiActions.toggleSidebar());
    expect(next.sidebarCollapsed).toBe(!initialUiState.sidebarCollapsed);
  });

  it('hydrates preferences', () => {
    const next = uiReducer(
      initialUiState,
      UiActions.hydratePreferencesSuccess({
        currentLang: 'en',
        sidebarCollapsed: true,
      }),
    );
    expect(next.currentLang).toBe('en');
    expect(next.sidebarCollapsed).toBeTrue();
  });
});
