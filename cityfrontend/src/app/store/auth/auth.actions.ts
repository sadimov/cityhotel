import { createActionGroup, emptyProps, props } from '@ngrx/store';

import {
  CityJwtPayload,
  LoginRequest,
  LoginResponse,
  decodeJwt,
} from '../../services/auth.service';
import { AuthHotel, AuthUser } from './auth.state';

/**
 * Toutes les actions du feature `auth` regroupées via `createActionGroup`.
 *
 * Convention :
 * - `bootstrap` : déclenchée au démarrage de l'app, réhydrate le store
 *   depuis localStorage si un JWT valide est présent.
 * - `login` / `loginSuccess` / `loginFailure` : flow login standard.
 * - `logout` / `logoutSuccess` : flow logout (clear localStorage + redirect).
 * - `refreshToken` / `refreshTokenSuccess` / `refreshTokenFailure` :
 *   rafraîchissement silencieux du JWT.
 * - `loadCurrentUser` / `loadCurrentUserSuccess` : appel /auth/me pour
 *   réhydrater les données user après un refresh ou un changement de profil.
 *
 * Les payloads d'erreur transportent une **clé i18n** (ex. `error.auth.invalidCredentials`),
 * jamais une chaîne déjà traduite — le composant qui consomme l'erreur fera la
 * traduction via ngx-translate.
 */
export const AuthActions = createActionGroup({
  source: 'Auth',
  events: {
    // Bootstrap au démarrage de l'app
    Bootstrap: emptyProps(),
    'Bootstrap Success': props<{ response: LoginResponse }>(),
    'Bootstrap No Session': emptyProps(),

    // Login
    Login: props<{ credentials: LoginRequest; returnUrl?: string }>(),
    'Login Success': props<{ response: LoginResponse; returnUrl?: string }>(),
    'Login Failure': props<{ errorKey: string }>(),

    // Logout
    Logout: emptyProps(),
    'Logout Success': emptyProps(),

    // Refresh token
    'Refresh Token': emptyProps(),
    'Refresh Token Success': props<{ response: LoginResponse }>(),
    'Refresh Token Failure': props<{ errorKey: string }>(),

    // Charger l'utilisateur courant (/auth/me)
    'Load Current User': emptyProps(),
    'Load Current User Success': props<{ response: LoginResponse }>(),
    'Load Current User Failure': props<{ errorKey: string }>(),

    // Reset état d'erreur (UX : nettoyer le message d'erreur quand l'écran login est ré-affiché)
    'Clear Error': emptyProps(),
  },
});

/**
 * Helpers pour mapper la réponse backend vers les structures du store.
 * Centralisés ici pour éviter la duplication entre reducer et effects.
 */
export function mapLoginResponseToUser(response: LoginResponse): AuthUser {
  return {
    userId: response.userId,
    username: response.username,
    email: response.email,
    prenom: response.prenom,
    nom: response.nom,
    nomComplet: response.nomComplet || `${response.prenom} ${response.nom}`,
  };
}

export function mapLoginResponseToHotel(response: LoginResponse): AuthHotel {
  // Tour 7C : `hotelId` n'est plus exposé par le DTO serveur — on le dérive
  // du claim JWT signé. `?? 0` = sentinel ROOT pour les SUPERADMIN sans
  // hôtel (cohérent avec `Hotel.ROOT = 0L` côté backend). Le mapper reste
  // pur (parsing JSON pur, pas de side-effect) et donc compatible avec les
  // runtimeChecks NgRx.
  const hotelId = decodeJwt<CityJwtPayload>(response.token).hotelId ?? 0;
  return {
    hotelId,
    hotelCode: response.hotelCode,
    hotelNom: response.hotelNom,
  };
}

export function mapLoginResponseToRoles(response: LoginResponse): string[] {
  return response.roleCode ? [response.roleCode] : [];
}
