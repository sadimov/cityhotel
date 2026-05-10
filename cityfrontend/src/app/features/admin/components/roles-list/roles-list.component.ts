import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, of } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';

import { Role } from '../../models/role.admin.model';
import { RolesAdminService } from '../../services/roles.admin.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface RoleViewModel extends Role {
  expanded?: boolean;
}

/**
 * Liste des rôles (lecture seule).
 *
 * Le référentiel des rôles est seedé par migration Liquibase et n'est
 * pas éditable via l'UI ; ce composant offre uniquement une vue.
 *
 * Permissions affichées en expand/collapse par ligne (chip-list ou
 * bullet-list selon le contenu).
 */
@Component({
  selector: 'app-admin-roles-list',
  templateUrl: './roles-list.component.html',
  styleUrls: ['./roles-list.component.scss'],
  standalone: false,
})
export class RolesListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  roles: RoleViewModel[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(private readonly rolesService: RolesAdminService) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.state = 'loading';
    this.rolesService
      .findAll()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of<Role[]>([]);
        }),
      )
      .subscribe((roles) => {
        if (this.state === 'error') {
          return;
        }
        this.roles = roles.map((r) => ({ ...r, expanded: false }));
        this.state = this.roles.length === 0 ? 'empty' : 'ready';
      });
  }

  toggle(role: RoleViewModel): void {
    role.expanded = !role.expanded;
  }

  hasPermissions(role: RoleViewModel): boolean {
    return Array.isArray(role.permissions) && role.permissions.length > 0;
  }
}
