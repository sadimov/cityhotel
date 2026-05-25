import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { AuthService } from '../../../../services/auth.service';
import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { TypeChambre } from '../../models/type-chambre.model';
import { TypesChambreService } from '../../services/types-chambre.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste paginée des types de chambres (multi-tenant via JWT côté serveur).
 *
 * Actions :
 *  - Nouveau type (en-tête)
 *  - Modifier (icône crayon)
 *  - Désactiver (orange, si actif) / Réactiver (vert, si inactif)
 *
 * Les boutons d'écriture sont masqués si l'utilisateur n'est pas
 * SUPERADMIN / ADMIN / GERANT (UX). Le backend reste la garde d'autorité.
 */
@Component({
  selector: 'app-types-chambre-list',
  templateUrl: './types-chambre-list.component.html',
  styleUrls: ['./types-chambre-list.component.scss'],
  standalone: false,
})
export class TypesChambreListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<TypeChambre> | null = null;
  showInactive = false;
  busy = false;

  request = { page: 0, size: 10, sortBy: 'typeNom', sortDir: 'asc' as 'asc' | 'desc' };

  private readonly destroy$ = new Subject<void>();
  private readonly writeRoles = ['SUPERADMIN', 'ADMIN', 'GERANT'];

  constructor(
    private readonly typesService: TypesChambreService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
    private readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get canWrite(): boolean {
    return this.auth.hasAnyRole(this.writeRoles);
  }

  load(): void {
    this.state = 'loading';
    // showInactive = true → ne pas filtrer par actif. Sinon, restreindre aux actifs.
    const actif = this.showInactive ? undefined : true;
    this.typesService
      .page(this.request.page, this.request.size, actif, this.request.sortBy, this.request.sortDir)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((p) => {
        if (!p) return;
        this.page = p;
        this.state = p.numberOfElements === 0 ? 'empty' : 'ready';
      });
  }

  onToggleShowInactive(value: boolean): void {
    this.showInactive = value;
    this.request = { ...this.request, page: 0 };
    this.load();
  }

  goToPage(p: number): void {
    if (!this.page || p < 0 || p >= this.page.totalPages) return;
    this.request = { ...this.request, page: p };
    this.load();
  }

  createNew(): void {
    this.router.navigate(['/hebergement/types-chambre/new']);
  }

  edit(t: TypeChambre): void {
    if (t.typeId == null) return;
    this.router.navigate(['/hebergement/types-chambre', t.typeId]);
  }

  deactivate(t: TypeChambre): void {
    if (t.typeId == null || t.actif === false) return;
    const id = t.typeId;
    Swal.fire({
      icon: 'question',
      title: this.i18n.translate('hebergement.typesChambre.messages.deactivateConfirm'),
      text: t.typeNom,
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('hebergement.typesChambre.actions.deactivate'),
      cancelButtonText: this.i18n.translate('common.cancel'),
      reverseButtons: true,
    }).then((res) => {
      if (!res.isConfirmed) return;
      this.busy = true;
      this.typesService
        .desactiver(id)
        .pipe(takeUntil(this.destroy$), finalize(() => (this.busy = false)))
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('hebergement.typesChambre.messages.deactivateSuccess'),
              timer: 1200,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('hebergement.typesChambre.messages.deactivateError'),
            });
          },
        });
    });
  }

  reactivate(t: TypeChambre): void {
    if (t.typeId == null || t.actif !== false) return;
    const id = t.typeId;
    this.busy = true;
    this.typesService
      .reactiver(id)
      .pipe(takeUntil(this.destroy$), finalize(() => (this.busy = false)))
      .subscribe({
        next: () => {
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('hebergement.typesChambre.messages.reactivateSuccess'),
            timer: 1200,
            showConfirmButton: false,
          });
          this.load();
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('hebergement.typesChambre.messages.reactivateError'),
          });
        },
      });
  }

  get types(): TypeChambre[] {
    return this.page?.content ?? [];
  }

  get pagesArray(): number[] {
    if (!this.page) return [];
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }
}
