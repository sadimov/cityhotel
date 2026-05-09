/**
 * Barrel root du store NgRx — agrège les feature stores `auth` et `ui`.
 *
 * Convention : chaque feature module métier (clients, inventory, etc.) ouvrira
 * son propre dossier `store/<feature>/` câblé via `StoreModule.forFeature(...)` +
 * `EffectsModule.forFeature(...)` dans son module Angular.
 */
export * from './auth';
export * from './ui';
