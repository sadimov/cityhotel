import { LoginResponse } from '../../services/auth.service';

/**
 * Helpers de persistance JWT/user dans `localStorage`.
 *
 * Centralisé ici (et utilisé par `AuthEffects` + `AuthService` + l'intercepteur)
 * pour éviter que les clés se duplique dans plusieurs fichiers.
 *
 * **Choix d'archi (Tour 5B 2026-05-06)** : le JWT reste en `localStorage` —
 * c'est la source de vérité au démarrage de l'app (action `Bootstrap` qui
 * réhydrate le store). L'intercepteur lit `localStorage` directement :
 * un sélecteur NgRx synchrone serait overkill pour ce cas. Le store est
 * toujours synchronisé avec localStorage via les effects `loginSuccess$` /
 * `logoutSuccess$` / `refreshTokenSuccess$`.
 */
export const TOKEN_STORAGE_KEY = 'city_hotel_token';
export const USER_STORAGE_KEY = 'city_hotel_user';

export function getStoredToken(): string | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function getStoredLoginResponse(): LoginResponse | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as LoginResponse;
  } catch {
    localStorage.removeItem(USER_STORAGE_KEY);
    return null;
  }
}

export function persistSession(response: LoginResponse): void {
  if (typeof localStorage === 'undefined') {
    return;
  }
  localStorage.setItem(TOKEN_STORAGE_KEY, response.token);
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(response));
}

export function clearStoredSession(): void {
  if (typeof localStorage === 'undefined') {
    return;
  }
  localStorage.removeItem(TOKEN_STORAGE_KEY);
  localStorage.removeItem(USER_STORAGE_KEY);
}

/**
 * Vérifie qu'un JWT n'est pas expiré (sans valider la signature — c'est
 * le rôle du backend). Retourne `true` si le token est encore valide
 * d'après son champ `exp`.
 */
export function isTokenStillValid(token: string): boolean {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return false;
    }
    const payload = JSON.parse(atob(parts[1])) as { exp?: number };
    if (!payload.exp) {
      return true;
    }
    return payload.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}
