import { createFeatureSelector, createSelector } from '@ngrx/store';

import { AUTH_FEATURE_KEY, AuthState } from './auth.state';

/**
 * Sélecteurs du feature store `auth`.
 *
 * Le feature key (`auth`) doit correspondre au nom utilisé dans
 * `StoreModule.forRoot({ auth: authReducer, ... })` côté `AppModule`.
 */
export const selectAuthState = createFeatureSelector<AuthState>(AUTH_FEATURE_KEY);

export const selectToken = createSelector(
  selectAuthState,
  (state) => state.token,
);

export const selectCurrentUser = createSelector(
  selectAuthState,
  (state) => state.currentUser,
);

export const selectCurrentHotel = createSelector(
  selectAuthState,
  (state) => state.currentHotel,
);

export const selectRoles = createSelector(
  selectAuthState,
  (state) => state.roles,
);

export const selectIsAuthenticated = createSelector(
  selectAuthState,
  (state) => state.token !== null && state.currentUser !== null,
);

export const selectAuthLoading = createSelector(
  selectAuthState,
  (state) => state.loading,
);

export const selectAuthError = createSelector(
  selectAuthState,
  (state) => state.error,
);

/**
 * Factory : sélecteur paramétré qui retourne `true` si l'utilisateur courant
 * possède le rôle demandé.
 */
export const selectHasRole = (role: string) =>
  createSelector(selectRoles, (roles) => roles.includes(role));

/**
 * Factory : `true` si l'utilisateur courant possède au moins un des rôles
 * demandés. Utilisé par `roleGuard`.
 */
export const selectHasAnyRole = (roles: string[]) =>
  createSelector(selectRoles, (current) =>
    roles.some((r) => current.includes(r)),
  );
