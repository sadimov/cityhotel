import { createReducer, on } from '@ngrx/store';

import {
  AuthActions,
  mapLoginResponseToHotel,
  mapLoginResponseToRoles,
  mapLoginResponseToUser,
} from './auth.actions';
import { AuthState, initialAuthState } from './auth.state';

/**
 * Reducer du feature store `auth`.
 *
 * - Toutes les transitions sont pures (NgRx runtimeChecks sont activés en mode
 *   strict dans AppModule : strictStateImmutability + strictActionImmutability +
 *   strictStateSerializability + strictActionSerializability).
 * - Aucune logique HTTP / localStorage / router ici — c'est le rôle des effects.
 */
export const authReducer = createReducer(
  initialAuthState,

  // ────────────────────────────────────────────────────────────────────────
  // Bootstrap
  // ────────────────────────────────────────────────────────────────────────
  on(
    AuthActions.bootstrap,
    (state): AuthState => ({ ...state, loading: true, error: null }),
  ),
  on(
    AuthActions.bootstrapSuccess,
    (state, { response }): AuthState => ({
      ...state,
      token: response.token,
      currentUser: mapLoginResponseToUser(response),
      currentHotel: mapLoginResponseToHotel(response),
      roles: mapLoginResponseToRoles(response),
      loading: false,
      error: null,
    }),
  ),
  on(
    AuthActions.bootstrapNoSession,
    (state): AuthState => ({ ...state, loading: false }),
  ),

  // ────────────────────────────────────────────────────────────────────────
  // Login
  // ────────────────────────────────────────────────────────────────────────
  on(
    AuthActions.login,
    (state): AuthState => ({ ...state, loading: true, error: null }),
  ),
  on(
    AuthActions.loginSuccess,
    (state, { response }): AuthState => ({
      ...state,
      token: response.token,
      currentUser: mapLoginResponseToUser(response),
      currentHotel: mapLoginResponseToHotel(response),
      roles: mapLoginResponseToRoles(response),
      loading: false,
      error: null,
    }),
  ),
  on(
    AuthActions.loginFailure,
    (state, { errorKey }): AuthState => ({
      ...state,
      loading: false,
      error: errorKey,
    }),
  ),

  // ────────────────────────────────────────────────────────────────────────
  // Logout
  // ────────────────────────────────────────────────────────────────────────
  on(
    AuthActions.logout,
    (state): AuthState => ({ ...state, loading: true }),
  ),
  on(
    AuthActions.logoutSuccess,
    (): AuthState => ({ ...initialAuthState }),
  ),

  // ────────────────────────────────────────────────────────────────────────
  // Refresh token
  // ────────────────────────────────────────────────────────────────────────
  on(
    AuthActions.refreshToken,
    (state): AuthState => ({ ...state, loading: true, error: null }),
  ),
  on(
    AuthActions.refreshTokenSuccess,
    (state, { response }): AuthState => ({
      ...state,
      token: response.token,
      currentUser: mapLoginResponseToUser(response),
      currentHotel: mapLoginResponseToHotel(response),
      roles: mapLoginResponseToRoles(response),
      loading: false,
      error: null,
    }),
  ),
  on(
    AuthActions.refreshTokenFailure,
    (state, { errorKey }): AuthState => ({
      ...initialAuthState,
      error: errorKey,
    }),
  ),

  // ────────────────────────────────────────────────────────────────────────
  // Load current user
  // ────────────────────────────────────────────────────────────────────────
  on(
    AuthActions.loadCurrentUser,
    (state): AuthState => ({ ...state, loading: true, error: null }),
  ),
  on(
    AuthActions.loadCurrentUserSuccess,
    (state, { response }): AuthState => ({
      ...state,
      currentUser: mapLoginResponseToUser(response),
      currentHotel: mapLoginResponseToHotel(response),
      roles: mapLoginResponseToRoles(response),
      loading: false,
      error: null,
    }),
  ),
  on(
    AuthActions.loadCurrentUserFailure,
    (state, { errorKey }): AuthState => ({
      ...state,
      loading: false,
      error: errorKey,
    }),
  ),

  // ────────────────────────────────────────────────────────────────────────
  // Misc
  // ────────────────────────────────────────────────────────────────────────
  on(
    AuthActions.clearError,
    (state): AuthState => ({ ...state, error: null }),
  ),
);
