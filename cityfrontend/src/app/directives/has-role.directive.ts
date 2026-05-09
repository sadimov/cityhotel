import { Directive, Input, OnInit, TemplateRef, ViewContainerRef, OnDestroy } from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
import { AuthService } from '../services/auth.service';

@Directive({
  selector: '[appHasRole]',
  standalone: false
})
export class HasRoleDirective implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private requiredRoles: string[] = [];

  @Input() set appHasRole(roles: string | string[]) {
    this.requiredRoles = Array.isArray(roles) ? roles : [roles];
    this.updateView();
  }

  constructor(
    private templateRef: TemplateRef<any>,
    private viewContainer: ViewContainerRef,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    // Écouter les changements d'utilisateur
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.updateView();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateView(): void {
    this.viewContainer.clear();
    
    if (this.hasRequiredRole()) {
      this.viewContainer.createEmbeddedView(this.templateRef);
    }
  }

  private hasRequiredRole(): boolean {
    if (!this.requiredRoles || this.requiredRoles.length === 0) {
      return true;
    }

    return this.authService.hasAnyRole(this.requiredRoles);
  }
}