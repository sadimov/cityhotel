import { Component, OnDestroy, OnInit } from '@angular/core';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import {
  CompteMappingDto,
  TypeEvenementComptable,
} from '../../../models/compte-mapping.model';
import { CompteMappingService } from '../../../services/compte-mapping.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste des mappings comptables (B7) — édition inline du compteCode
 * par typeEvenement.
 *
 * Le défaut codé (`defaut=true`) est affiché en italique ; la saisie
 * d'un compteCode personnalise persiste le mapping côté back.
 */
@Component({
  selector: 'app-compte-mapping-list',
  templateUrl: './compte-mapping-list.component.html',
  standalone: false,
})
export class CompteMappingListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  mappings: CompteMappingDto[] = [];
  edits: Record<string, string> = {};
  savingType: TypeEvenementComptable | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly service: CompteMappingService,
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
      .list()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((list) => {
        if (!list) return;
        this.mappings = list;
        this.edits = {};
        for (const m of list) {
          this.edits[m.typeEvenement] = m.compteCode;
        }
        this.state = list.length === 0 ? 'empty' : 'ready';
      });
  }

  onCompteChange(type: TypeEvenementComptable, value: string): void {
    this.edits[type] = value.trim().toUpperCase();
  }

  save(type: TypeEvenementComptable): void {
    const compteCode = (this.edits[type] || '').trim();
    if (!compteCode) {
      Swal.fire({
        icon: 'warning',
        title: this.i18n.translate('error.mapping.compteCodeRequired'),
      });
      return;
    }
    this.savingType = type;
    this.service
      .update(type, { compteCode })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.savingType = null)),
      )
      .subscribe({
        next: (updated) => {
          const idx = this.mappings.findIndex(
            (m) => m.typeEvenement === type,
          );
          if (idx >= 0) this.mappings[idx] = updated;
          this.edits[type] = updated.compteCode;
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('comptabilite.mapping.messages.updateSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
        },
        error: (err) => {
          const key = err?.error?.message || 'error.mapping.updateFailed';
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate(key),
          });
        },
      });
  }

  typeKey(t: TypeEvenementComptable): string {
    return `comptabilite.mapping.type.${t}`;
  }
}
