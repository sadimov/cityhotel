import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/client.model';
import { Societe } from '../../models/societe.model';
import { ClientsService } from '../../services/clients.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste paginée des sociétés clients (multi-tenant via JWT côté serveur).
 *
 * Actions :
 *  - Voir / Modifier (selon rôle)
 *  - Désactiver (soft delete) → bouton orange, affiché si actif
 *  - Réactiver → bouton vert, affiché si désactivé
 *  - Supprimer définitivement → bouton rouge, refusé si clients rattachés
 *
 * Les sociétés désactivées sont affichées en grisé + badge "Inactive".
 */
@Component({
  selector: 'app-societes-list',
  templateUrl: './societes-list.component.html',
  styleUrls: ['./societes-list.component.scss'],
  standalone: false,
})
export class SocietesListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<Societe> | null = null;
  searchTerm = '';
  busy = false;

  request = { page: 0, size: 10, sortBy: 'societeNom', sortDir: 'asc' as 'asc' | 'desc' };

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly clientsService: ClientsService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.state = 'loading';
    this.clientsService
      .pageSocietes({ ...this.request, recherche: this.searchTerm.trim() || undefined })
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

  onSearchChange(value: string): void {
    this.searchTerm = value;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.request = { ...this.request, page: 0 };
      this.load();
    }, 300);
  }

  goToPage(p: number): void {
    if (!this.page || p < 0 || p >= this.page.totalPages) return;
    this.request = { ...this.request, page: p };
    this.load();
  }

  createNew(): void {
    this.router.navigate(['/clients/societes/new']);
  }

  edit(s: Societe): void {
    if (s.societeId == null) return;
    this.router.navigate(['/clients/societes', s.societeId]);
  }

  deactivate(s: Societe): void {
    if (s.societeId == null || s.actif === false) return;
    const id = s.societeId;
    Swal.fire({
      icon: 'question',
      title: this.i18n.translate('clients.societes.messages.deactivateConfirm'),
      text: s.societeNom,
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('clients.societes.actions.deactivate'),
      cancelButtonText: this.i18n.translate('common.cancel'),
      reverseButtons: true,
    }).then((res) => {
      if (!res.isConfirmed) return;
      this.busy = true;
      this.clientsService
        .deactivateSociete(id)
        .pipe(takeUntil(this.destroy$), finalize(() => (this.busy = false)))
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('clients.societes.messages.deactivateSuccess'),
              timer: 1200,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('clients.societes.messages.deactivateError'),
            });
          },
        });
    });
  }

  reactivate(s: Societe): void {
    if (s.societeId == null || s.actif !== false) return;
    const id = s.societeId;
    this.busy = true;
    this.clientsService
      .reactivateSociete(id)
      .pipe(takeUntil(this.destroy$), finalize(() => (this.busy = false)))
      .subscribe({
        next: () => {
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('clients.societes.messages.reactivateSuccess'),
            timer: 1200,
            showConfirmButton: false,
          });
          this.load();
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('clients.societes.messages.reactivateError'),
          });
        },
      });
  }

  remove(s: Societe): void {
    if (s.societeId == null) return;
    const id = s.societeId;
    Swal.fire({
      icon: 'warning',
      title: this.i18n.translate('clients.societes.messages.deleteConfirm'),
      text: this.i18n.translate('clients.societes.messages.deleteConfirmText'),
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('clients.societes.actions.delete'),
      cancelButtonText: this.i18n.translate('common.cancel'),
      confirmButtonColor: '#dc3545',
      reverseButtons: true,
    }).then((res) => {
      if (!res.isConfirmed) return;
      this.busy = true;
      this.clientsService
        .deleteSociete(id)
        .pipe(takeUntil(this.destroy$), finalize(() => (this.busy = false)))
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('clients.societes.messages.deleteSuccess'),
              timer: 1200,
              showConfirmButton: false,
            });
            this.load();
          },
          error: (err) => {
            const key = err?.error?.error || 'clients.societes.messages.deleteError';
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate(key),
            });
          },
        });
    });
  }

  get societes(): Societe[] {
    return this.page?.content ?? [];
  }

  get pagesArray(): number[] {
    if (!this.page) return [];
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }
}
