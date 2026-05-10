import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { SuperAdminGuard } from './super-admin.guard';

describe('SuperAdminGuard', () => {
  let guard: SuperAdminGuard;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const dummyRoute = {} as ActivatedRouteSnapshot;
  const dummyState = { url: '/admin/hotels' } as RouterStateSnapshot;

  beforeEach(() => {
    const authSpy = jasmine.createSpyObj<AuthService>('AuthService', [
      'isAuthenticated',
      'hasRole',
    ]);
    const rtrSpy = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);

    TestBed.configureTestingModule({
      providers: [
        SuperAdminGuard,
        { provide: AuthService, useValue: authSpy },
        { provide: Router, useValue: rtrSpy },
      ],
    });

    guard = TestBed.inject(SuperAdminGuard);
    authServiceSpy = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    routerSpy = TestBed.inject(Router) as jasmine.SpyObj<Router>;
  });

  it('should be created', () => {
    expect(guard).toBeTruthy();
  });

  it('should redirect to /login when user is not authenticated', () => {
    const tree = {} as UrlTree;
    authServiceSpy.isAuthenticated.and.returnValue(false);
    routerSpy.createUrlTree.and.returnValue(tree);

    const result = guard.canActivate(dummyRoute, dummyState);

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/admin/hotels' },
    });
    expect(result).toBe(tree);
  });

  it('should redirect to /dashboard when user is authenticated but not SUPERADMIN', () => {
    const tree = {} as UrlTree;
    authServiceSpy.isAuthenticated.and.returnValue(true);
    authServiceSpy.hasRole.and.returnValue(false);
    routerSpy.createUrlTree.and.returnValue(tree);

    const result = guard.canActivate(dummyRoute, dummyState);

    expect(authServiceSpy.hasRole).toHaveBeenCalledWith('SUPERADMIN');
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
    expect(result).toBe(tree);
  });

  it('should grant access when user is authenticated and SUPERADMIN', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);
    authServiceSpy.hasRole.and.returnValue(true);

    const result = guard.canActivate(dummyRoute, dummyState);

    expect(result).toBe(true);
    expect(routerSpy.createUrlTree).not.toHaveBeenCalled();
  });

  it('canActivateChild should delegate to canActivate (grant when SUPERADMIN)', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);
    authServiceSpy.hasRole.and.returnValue(true);

    const result = guard.canActivateChild(dummyRoute, dummyState);

    expect(result).toBe(true);
  });

  it('canActivateChild should delegate to canActivate (redirect when not SUPERADMIN)', () => {
    const tree = {} as UrlTree;
    authServiceSpy.isAuthenticated.and.returnValue(true);
    authServiceSpy.hasRole.and.returnValue(false);
    routerSpy.createUrlTree.and.returnValue(tree);

    const result = guard.canActivateChild(dummyRoute, dummyState);

    expect(result).toBe(tree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
  });
});
