/**
 * State du feature store `auth`.
 *
 * Source de vérité unique du flux d'authentification :
 * - `token` : JWT brut (synchronisé avec localStorage via les effects)
 * - `currentUser` : profil applicatif extrait du payload login
 * - `currentHotel` : hôtel courant (id + code + nom) — alimenté par le JWT,
 *   JAMAIS depuis un input client (cf. CLAUDE.md racine §6.1)
 * - `roles` : rôles du user (string[]) pour les guards et la directive *hasRole
 * - `loading` : un appel auth est-il en cours ?
 * - `error` : clé i18n d'erreur de la dernière action (ou null)
 */

export interface AuthUser {
  userId: number;
  username: string;
  email: string;
  prenom: string;
  nom: string;
  nomComplet: string;
}

export interface AuthHotel {
  hotelId: number;
  hotelCode: string;
  hotelNom: string;
}

export interface AuthState {
  token: string | null;
  currentUser: AuthUser | null;
  currentHotel: AuthHotel | null;
  roles: string[];
  loading: boolean;
  error: string | null;
}

export const initialAuthState: AuthState = {
  token: null,
  currentUser: null,
  currentHotel: null,
  roles: [],
  loading: false,
  error: null,
};

export const AUTH_FEATURE_KEY = 'auth';
