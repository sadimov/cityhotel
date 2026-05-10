import { Injectable } from '@angular/core';
import { Router, CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    
    // Vérifier si l'utilisateur est authentifié
    if (this.authService.isAuthenticated()) {
      return true;
    }

    // Sauvegarder l'URL tentée pour redirection après connexion
    const returnUrl = state.url;
    // Tour 38 (H6) — ne pas leaker le contexte JWT/URL en production.
    // Le log est strictement gardé en build dev pour le debug local.
    if (!environment.production) {
      // eslint-disable-next-line no-console
      console.log('🔐 Accès non autorisé, redirection vers login. URL tentée:', returnUrl);
    }
    
    // Rediriger vers la page de connexion
    this.router.navigate(['/login'], { 
      queryParams: { returnUrl } 
    });
    
    return false;
  }
}