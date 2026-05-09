import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import {
  BehaviorSubject,
  Observable,
  firstValueFrom,
  from,
  race,
  throwError,
} from 'rxjs';
import { distinctUntilChanged, map, take } from 'rxjs/operators';

import { AuthActions } from '../store/auth/auth.actions';
import {
  selectAuthLoading,
  selectCurrentHotel,
  selectCurrentUser,
  selectIsAuthenticated,
  selectRoles,
} from '../store/auth/auth.selectors';
import {
  getStoredLoginResponse,
  getStoredToken,
  isTokenStillValid,
} from '../store/auth/auth.storage';

/* ────────────────────────────────────────────────────────────────────────── */
/*  Types publics — conservés pour compat (login.component.ts, interceptors,  */
/*  guards, etc.). Ne PAS retirer ces interfaces tant que les consommateurs   */
/*  ne sont pas migrés.                                                       */
/* ────────────────────────────────────────────────────────────────────────── */

export interface LoginRequest {
  username: string;
  password: string;
  rememberMe?: boolean;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiryDate: string;
  userId: number;
  username: string;
  email: string;
  prenom: string;
  nom: string;
  nomComplet: string;
  // `hotelId` retiré du DTO serveur (Tour 7C, 2026-05-05) — il est désormais
  // dérivé côté client depuis le claim JWT via `decodeJwt(token).hotelId`.
  // Le claim reste signé serveur, donc non falsifiable.
  hotelCode: string;
  hotelNom: string;
  roleCode: string;
  roleNom: string;
  sessionId: string;
  derniereConnexion: string;
}

/**
 * Payload du JWT City Hotel (cf. backend `JwtTokenProvider`).
 *
 * `hotelId` peut être `null` pour un SUPERADMIN sans hôtel rattaché ; côté
 * front on le projette sur `0` (sentinel ROOT, cohérent avec `ROOT = 0L`
 * côté backend) afin de garder `UserInfo.hotelId: number` non-nullable.
 */
export interface CityJwtPayload {
  sub: string;
  username: string;
  email: string;
  hotelId: number | null;
  hotelCode: string;
  roleCode: string;
  iat: number;
  exp: number;
}

/**
 * Décode le payload d'un JWT (sans vérification de signature — le serveur
 * l'a déjà faite). Utilisé pour extraire des claims côté client (`hotelId`,
 * `roleCode`, ...) sans round-trip serveur.
 *
 * Variante "moderne" : `atob` puis re-encodage UTF-8 via `decodeURIComponent`
 * pour gérer correctement les caractères non-ASCII (évite l'utilisation de
 * `escape` déprécié).
 *
 * @throws Error si le token est mal formé / non-JSON.
 */
export function decodeJwt<T = Record<string, unknown>>(token: string): T {
  try {
    const payload = token.split('.')[1];
    if (!payload) {
      throw new Error('missing payload segment');
    }
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as T;
  } catch (e) {
    throw new Error('Invalid JWT payload: ' + (e as Error).message);
  }
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  error?: string;
  timestamp: string;
}

export interface UserInfo {
  userId: number;
  username: string;
  email: string;
  prenom: string;
  nom: string;
  nomComplet: string;
  hotelId: number;
  hotelCode: string;
  hotelNom: string;
  roleCode: string;
  roleNom: string;
  isAuthenticated: boolean;
}

/**
 * `AuthService` — façade publique du flux d'authentification.
 *
 * Depuis le Tour 5B (NgRx 21), l'implémentation interne est un fin wrapper
 * autour du store `auth` :
 *  - `login()` / `logout()` / `refreshToken()` dispatchent une action et
 *    retournent un `Observable` qui se résout sur `*Success` ou rejette sur
 *    `*Failure`.
 *  - `getToken()` / `isAuthenticated()` / `hasRole()` lisent le store
 *    de manière synchrone (snapshot via `take(1)`) — l'API publique reste
 *    booléenne / synchrone pour préserver les guards et l'intercepteur.
 *  - `currentUser$` / `isAuthenticated$` exposent le store NgRx en flux.
 *
 * Le JWT et le LoginResponse sérialisé restent stockés en `localStorage` :
 * c'est la source de vérité au démarrage de l'app — l'effect `bootstrap$`
 * réhydrate le store dès que `AuthActions.bootstrap()` est dispatché par
 * `AppComponent`.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  /** Snapshot synchrone du `UserInfo` courant — alimenté par le store. */
  private readonly currentUserSubject = new BehaviorSubject<UserInfo | null>(null);
  public readonly currentUser$: Observable<UserInfo | null> =
    this.currentUserSubject.asObservable();

  /** Snapshot synchrone de l'état d'authentification — alimenté par le store. */
  private readonly isAuthenticatedSubject = new BehaviorSubject<boolean>(false);
  public readonly isAuthenticated$: Observable<boolean> =
    this.isAuthenticatedSubject.asObservable();

  /** Loading flag, exposé en flux pour les composants. */
  public readonly loading$: Observable<boolean>;

  private readonly store = inject(Store);
  private readonly actions$ = inject(Actions);
  private readonly router = inject(Router);

  constructor() {
    // Hydratation initiale en cas de rechargement de page : si un JWT valide
    // existe en localStorage, on précharge `currentUserSubject` pour que les
    // guards qui sont évalués AVANT l'arrivée de `bootstrapSuccess` ne refusent
    // pas l'accès. Dispatch lui-même fait par AppComponent.
    const storedToken = getStoredToken();
    const storedUser = getStoredLoginResponse();
    if (storedToken && storedUser && isTokenStillValid(storedToken)) {
      this.currentUserSubject.next(this.toUserInfo(storedUser));
      this.isAuthenticatedSubject.next(true);
    }

    // Synchroniser les BehaviorSubjects avec le store NgRx (source de vérité).
    this.store
      .select(selectCurrentUser)
      .pipe(distinctUntilChanged())
      .subscribe((user) => {
        const hotel = this.snapshot(selectCurrentHotel);
        const roles = this.snapshot(selectRoles);
        if (user && hotel) {
          this.currentUserSubject.next({
            userId: user.userId,
            username: user.username,
            email: user.email,
            prenom: user.prenom,
            nom: user.nom,
            nomComplet: user.nomComplet,
            hotelId: hotel.hotelId,
            hotelCode: hotel.hotelCode,
            hotelNom: hotel.hotelNom,
            roleCode: roles[0] ?? '',
            roleNom: '',
            isAuthenticated: true,
          });
        } else {
          this.currentUserSubject.next(null);
        }
      });

    this.store
      .select(selectIsAuthenticated)
      .pipe(distinctUntilChanged())
      .subscribe((isAuth) => this.isAuthenticatedSubject.next(isAuth));

    this.loading$ = this.store.select(selectAuthLoading);
  }

  /* ────────────────────────────────────────────────────────────────────── */
  /*  Actions                                                                */
  /* ────────────────────────────────────────────────────────────────────── */

  /**
   * Lance le flow login. Retourne un `Observable<LoginResponse>` qui émet
   * sur `loginSuccess` ou throw sur `loginFailure` (le `message` de l'erreur
   * est une **clé i18n**, pas du texte traduit).
   */
  login(credentials: LoginRequest, returnUrl?: string): Observable<LoginResponse> {
    const success$ = this.actions$.pipe(
      ofType(AuthActions.loginSuccess),
      take(1),
      map(({ response }) => response),
    );
    const failure$ = this.actions$.pipe(
      ofType(AuthActions.loginFailure),
      take(1),
      map(({ errorKey }) => {
        throw new Error(errorKey);
      }),
    );
    this.store.dispatch(AuthActions.login({ credentials, returnUrl }));
    return race(success$, failure$);
  }

  /**
   * Lance le flow logout. La redirection vers `/login` est gérée par
   * `AuthEffects.logoutSuccess$` (effect dispatch:false).
   */
  logout(): Observable<void> {
    const done$ = this.actions$.pipe(
      ofType(AuthActions.logoutSuccess),
      take(1),
      map(() => undefined),
    );
    this.store.dispatch(AuthActions.logout());
    return done$;
  }

  /**
   * Rafraîchit le JWT.
   */
  refreshToken(): Observable<LoginResponse> {
    const success$ = this.actions$.pipe(
      ofType(AuthActions.refreshTokenSuccess),
      take(1),
      map(({ response }) => response),
    );
    const failure$ = this.actions$.pipe(
      ofType(AuthActions.refreshTokenFailure),
      take(1),
      map(({ errorKey }) => {
        throw new Error(errorKey);
      }),
    );
    this.store.dispatch(AuthActions.refreshToken());
    return race(success$, failure$);
  }

  /**
   * Recharge l'utilisateur courant via /auth/me.
   */
  getCurrentUser(): Observable<LoginResponse> {
    const success$ = this.actions$.pipe(
      ofType(AuthActions.loadCurrentUserSuccess),
      take(1),
      map(({ response }) => response),
    );
    const failure$ = this.actions$.pipe(
      ofType(AuthActions.loadCurrentUserFailure),
      take(1),
      map(({ errorKey }) => {
        throw new Error(errorKey);
      }),
    );
    this.store.dispatch(AuthActions.loadCurrentUser());
    return race(success$, failure$);
  }

  /**
   * Validation du token : on délègue maintenant au backend via /auth/me.
   * Conservé pour compat de signature mais l'usage recommandé est
   * `refreshToken()` ou `getCurrentUser()`.
   */
  validateToken(): Observable<boolean> {
    const token = this.getToken();
    if (!token) {
      return throwError(() => new Error('error.auth.noToken'));
    }
    return from(
      firstValueFrom(this.getCurrentUser())
        .then(() => true)
        .catch(() => false),
    );
  }

  /* ────────────────────────────────────────────────────────────────────── */
  /*  Lectures synchrones — façade pour guards / intercepteurs               */
  /* ────────────────────────────────────────────────────────────────────── */

  /**
   * Vérifie si l'utilisateur est authentifié (snapshot synchrone).
   * Combine store + localStorage : le localStorage est la source de vérité
   * tant que `bootstrapSuccess` n'a pas été dispatché.
   */
  isAuthenticated(): boolean {
    const inStore = this.snapshot(selectIsAuthenticated);
    if (inStore) {
      return true;
    }
    const token = getStoredToken();
    return !!token && isTokenStillValid(token);
  }

  /**
   * Retourne le JWT courant — lit `localStorage` (source de vérité robuste,
   * survit à un F5 avant le bootstrap du store).
   */
  getToken(): string | null {
    return getStoredToken();
  }

  /** Snapshot synchrone du UserInfo courant. */
  getCurrentUserValue(): UserInfo | null {
    return this.currentUserSubject.value;
  }

  hasRole(role: string): boolean {
    const roles = this.snapshot(selectRoles);
    if (roles.length > 0) {
      return roles.includes(role);
    }
    // Fallback localStorage (avant bootstrap du store)
    const stored = getStoredLoginResponse();
    return stored ? stored.roleCode === role : false;
  }

  hasAnyRole(roles: string[]): boolean {
    const current = this.snapshot(selectRoles);
    if (current.length > 0) {
      return roles.some((r) => current.includes(r));
    }
    const stored = getStoredLoginResponse();
    return stored ? roles.includes(stored.roleCode) : false;
  }

  getUserRole(): string | null {
    const roles = this.snapshot(selectRoles);
    if (roles.length > 0) {
      return roles[0];
    }
    const stored = getStoredLoginResponse();
    return stored?.roleCode ?? null;
  }

  /**
   * Redirection post-login en fonction du rôle. Conservé pour compat de
   * signature ; en pratique, `AuthEffects.loginSuccessRedirect$` fait le job.
   */
  redirectToRoleBasedDashboard(): void {
    const role = this.getUserRole();
    switch (role) {
      case 'SUPERADMIN':
      case 'ADMIN':
      case 'GERANT':
        this.router.navigate(['/dashboard']);
        break;
      case 'RECEPTION':
      case 'RESREC':
        this.router.navigate(['/reservations']);
        break;
      case 'RESTAURANT':
        this.router.navigate(['/restaurant']);
        break;
      case 'MENAGE':
        this.router.navigate(['/housekeeping']);
        break;
      case 'MAGASIN':
        this.router.navigate(['/orders']);
        break;
      default:
        this.router.navigate(['/profile']);
        break;
    }
  }

  /* ────────────────────────────────────────────────────────────────────── */
  /*  Helpers privés                                                         */
  /* ────────────────────────────────────────────────────────────────────── */

  /**
   * Snapshot synchrone d'un sélecteur. Utilise `take(1)` sur le store —
   * NgRx Store est synchrone donc la valeur est disponible immédiatement.
   */
  private snapshot<T>(
    selector: (state: object) => T,
  ): T {
    let value!: T;
    this.store
      .select(selector)
      .pipe(take(1))
      .subscribe((v) => (value = v));
    return value;
  }

  private toUserInfo(response: LoginResponse): UserInfo {
    // Tour 7C : `hotelId` n'est plus dans le DTO serveur — on le dérive du
    // claim JWT signé. `?? 0` = sentinel ROOT pour les SUPERADMIN sans hôtel.
    const hotelId = decodeJwt<CityJwtPayload>(response.token).hotelId ?? 0;
    return {
      userId: response.userId,
      username: response.username,
      email: response.email,
      prenom: response.prenom,
      nom: response.nom,
      nomComplet:
        response.nomComplet || `${response.prenom} ${response.nom}`,
      hotelId,
      hotelCode: response.hotelCode,
      hotelNom: response.hotelNom,
      roleCode: response.roleCode,
      roleNom: response.roleNom,
      isAuthenticated: true,
    };
  }
}

