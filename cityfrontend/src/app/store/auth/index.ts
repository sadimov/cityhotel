/**
 * Barrel du feature store `auth`.
 * Permet aux consommateurs (composants, services, guards) d'importer
 * `import { AuthActions, selectIsAuthenticated, ... } from '../store/auth'`.
 */
export * from './auth.actions';
export * from './auth.effects';
export * from './auth.reducer';
export * from './auth.selectors';
export * from './auth.state';
export * from './auth.storage';
export * from './auth.api';
