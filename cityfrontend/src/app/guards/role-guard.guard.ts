import { Injectable } from '@angular/core';
import { Router, CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class RoleGuard implements CanActivate {

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    
    // Vérifier d'abord l'authentification
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/login']);
      return false;
    }

    // Récupérer les rôles requis depuis les données de la route
    const requiredRoles = route.data['roles'] as string[];
    
    if (!requiredRoles || requiredRoles.length === 0) {
      // Aucun rôle spécifique requis, autoriser l'accès
      return true;
    }

    // Vérifier si l'utilisateur a un des rôles requis
    if (this.authService.hasAnyRole(requiredRoles)) {
      return true;
    }

    // L'utilisateur n'a pas les permissions, rediriger vers dashboard ou page d'erreur
    // Tour 38 (H6) — ne pas leaker la liste des rôles requis ni le rôle de l'utilisateur
    // courant en production (information sensible facilitant l'énumération des rôles).
    if (!environment.production) {
      // eslint-disable-next-line no-console
      console.warn('Accès refusé. Rôles requis:', requiredRoles, 'Rôle utilisateur:', this.authService.getUserRole());
    }
    
    // Rediriger vers le dashboard principal ou page d'accès refusé
    this.router.navigate(['/dashboard']);
    return false;
  }
}