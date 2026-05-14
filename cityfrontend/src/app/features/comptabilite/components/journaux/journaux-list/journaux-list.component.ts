import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import { Page } from '../../../../finance/models/page.model';
import { JournalComptableDto, TypeJournal } from '../../../models/journal.model';
import { JournauxService } from '../../../services/journaux.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste paginée des journaux comptables (B7).
 *
 * Actions :
 *  - "Nouveau" → /comptabilite/journaux/new
 *  - "Éditer" sur chaque ligne → /comptabilite/journaux/:id/edit
 *  - "Désactiver" / "Réactiver" selon `actif`
 */
@Component({
  selector: 'app-journaux-list',
  templateUrl: './journaux-list.component.html',
  standalone: false,
})
export class JournauxListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: Page<JournalComptableDto> | null = null;
  request = { page: 0, size: 10, sort: 'code,asc' };
  toggleId: number | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly service: JournauxService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.state = 'loading';
    this.service
      .page(this.request)
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

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) return;
    this.request = { ...this.request, page };
    this.load();
  }

  createNew(): void {
    this.router.navigate(['/comptabilite/journaux/new']);
  }

  edit(j: JournalComptableDto): void {
    this.router.navigate(['/comptabilite/journaux', j.id, 'edit']);
  }

  toggle(j: JournalComptableDto): void {
    const action = j.actif ? 'deactivate' : 'reactivate';
    const titleKey = j.actif
      ? 'comptabilite.journal.messages.deactivateConfirm'
      : 'comptabilite.journal.messages.reactivateConfirm';
    Swal.fire({
      title: this.i18n.translate(titleKey),
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('common.confirm'),
      cancelButtonText: this.i18n.translate('common.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) return;
      this.toggleId = j.id;
      const obs$ = action === 'deactivate'
        ? this.service.deactivate(j.id)
        : this.service.reactivate(j.id);
      obs$
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.toggleId = null)),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('comptabilite.journal.messages.toggleSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: (err) => {
            const key = err?.error?.message || 'error.journal.toggleFailed';
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate(key),
            });
          },
        });
    });
  }

  get journaux(): JournalComptableDto[] {
    return this.page?.content ?? [];
  }

  get pagesArray(): number[] {
    if (!this.page) return [];
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  typeKey(t: TypeJournal): string {
    return `comptabilite.journal.type.${t}`;
  }
}
