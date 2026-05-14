import { Component, OnDestroy, OnInit } from '@angular/core';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import { TauxTvaConfigDto, TypeServiceTva } from '../../../models/tva.model';
import { TvaService } from '../../../services/tva.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface EditEntry {
  taux: number;
  actif: boolean;
  libelle: string;
}

/**
 * Liste des configurations TVA (B7) — édition inline par typeService.
 */
@Component({
  selector: 'app-tva-config-list',
  templateUrl: './tva-config-list.component.html',
  standalone: false,
})
export class TvaConfigListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  configs: TauxTvaConfigDto[] = [];
  edits: Record<string, EditEntry> = {};
  savingType: TypeServiceTva | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly service: TvaService,
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
      .listConfig()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((list) => {
        if (!list) return;
        this.configs = list;
        this.edits = {};
        for (const c of list) {
          this.edits[c.typeService] = {
            taux: c.taux,
            actif: c.actif,
            libelle: c.libelle,
          };
        }
        this.state = list.length === 0 ? 'empty' : 'ready';
      });
  }

  save(typeService: TypeServiceTva): void {
    const edit = this.edits[typeService];
    if (!edit) return;
    if (edit.taux < 0 || edit.taux > 99.99) {
      Swal.fire({
        icon: 'warning',
        title: this.i18n.translate('error.tva.taux.invalid'),
      });
      return;
    }
    this.savingType = typeService;
    this.service
      .updateConfig(typeService, {
        taux: edit.taux,
        actif: edit.actif,
        libelle: edit.libelle,
      })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.savingType = null)),
      )
      .subscribe({
        next: (updated) => {
          const idx = this.configs.findIndex(
            (c) => c.typeService === typeService,
          );
          if (idx >= 0) this.configs[idx] = updated;
          this.edits[typeService] = {
            taux: updated.taux,
            actif: updated.actif,
            libelle: updated.libelle,
          };
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('comptabilite.tvaConfig.messages.updateSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
        },
        error: (err) => {
          const key = err?.error?.message || 'error.tva.updateFailed';
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate(key),
          });
        },
      });
  }

  typeKey(t: TypeServiceTva): string {
    return `comptabilite.tvaConfig.type.${t}`;
  }
}
