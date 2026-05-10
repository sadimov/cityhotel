import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  CanActivateChild,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '../services/auth.service';

/**
 * Guard d'accès à la zone administration cross-tenant (SUPERADMIN).
 *
 * Single source of truth pour `/admin` : seul cet écran nécessite
 * une vérification spécifique du rôle `SUPERADMIN` ; on n'empile PAS
 * un `RoleGuard` parent dans `app-routing.module.ts` pour éviter une
 * double vérification redondante (cf. consigne Tour 31).
 *
 * Comportement :
 *  - Non authentifié → redirect `/login` (avec `returnUrl` préservé).
 *  - Authentifié mais non SUPERADMIN → redirect `/dashboard`
 *    (cohérent avec `RoleGuard.canActivate`).
 *  - SUPERADMIN → accès accordé.
 *
 * `canActivateChild` délègue à `canActivate` pour appliquer la même
 * politique sur tous les sous-onglets (`/admin/hotels`, `/admin/users`,
 * `/admin/roles`, `/admin/parametres`) sans répéter la logique.
 */
@Injectable({ providedIn: 'root' })
export class SuperAdminGuard implements CanActivate, CanActivateChild {
  private static readonly REQUIRED_ROLE = 'SUPERADMIN';

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot,
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    if (!this.authService.isAuthenticated()) {
      return this.router.createUrlTree(['/login'], {
        queryParams: { returnUrl: state.url },
      });
    }

    if (!this.authService.hasRole(SuperAdminGuard.REQUIRED_ROLE)) {
      return this.router.createUrlTree(['/dashboard']);
    }

    return true;
  }

  canActivateChild(
    childRoute: ActivatedRouteSnapshot,
    state: RouterStateSnapshot,
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    return this.canActivate(childRoute, state);
  }
}
