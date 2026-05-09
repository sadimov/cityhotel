import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Personnel } from '../../models/personnel.model';
import {
  AssignerTacheRequest,
  PrioriteTache,
  Tache,
  TerminerTacheRequest,
} from '../../models/tache.model';
import { PersonnelsService } from '../../services/personnels.service';
import { TachesService } from '../../services/taches.service';

type DetailState = 'loading' | 'ready' | 'error';

/**
 * Vue lecture seule détaillée d'une tâche de ménage + actions workflow
 * (assigner / commencer / terminer).
 *
 * Pour l'édition complète d'une tâche, utiliser `tache-form/`
 * (route `/menage/taches/:id`). Cette vue est invocable :
 *  - via le bouton « voir » d'une ligne `taches-list/` (route
 *    `/menage/taches/:id/detail`)
 *  - via deep link partagé (URL collable)
 *
 * Les actions ouvrent une modal :
 *  - **Assigner** : composant maison `<app-assignation-personnel>`
 *    (modal embarquée — émet `(assigned)` et c'est ce composant
 *    parent qui appelle `tachesService.assigner()`).
 *  - **Commencer** : confirmation SweetAlert simple.
 *  - **Terminer** : SweetAlert avec rapport (commentaires + note qualité).
 *
 * L'historique de la tâche (`Historique` côté backend) sera intégré
 * lorsqu'un service `HistoriqueService.findByTacheId(id)` sera scaffolé
 * — cf. TODO ci-dessous, hors scope Tour 28.
 */
@Component({
  selector: 'app-tache-detail',
  templateUrl: './tache-detail.component.html',
  styleUrls: ['./tache-detail.component.scss'],
  standalone: false,
})
export class TacheDetailComponent implements OnInit, OnDestroy {
  state: DetailState = 'loading';
  tache: Tache | null = null;
  personnels: Personnel[] = [];
  assignationModalOpen = false;
  workInProgress = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly tachesService: TachesService,
    private readonly personnelsService: PersonnelsService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      this.state = 'error';
      return;
    }
    const id = Number(idParam);
    if (!Number.isFinite(id)) {
      this.state = 'error';
      return;
    }
    this.loadTache(id);
    this.loadPersonnels();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ────────────────────────────────────────────────────────────────────────
  // Actions UI
  // ────────────────────────────────────────────────────────────────────────

  back(): void {
    this.router.navigate(['/menage/taches']);
  }

  edit(): void {
    if (!this.tache?.tacheId) {
      return;
    }
    this.router.navigate(['/menage/taches', this.tache.tacheId]);
  }

  openAssignation(): void {
    if (!this.canAssigner()) {
      return;
    }
    this.assignationModalOpen = true;
  }

  closeAssignation(): void {
    this.assignationModalOpen = false;
  }

  /**
   * Reçu depuis `<app-assignation-personnel>`.
   * Le composant enfant ne fait que la sélection ; l'appel HTTP est
   * effectué ici pour conserver la logique HTTP au plus près du
   * détenteur de l'état (pattern container / presentational).
   */
  onPersonnelAssigned(payload: { tacheId: number; personnelId: number }): void {
    if (!this.tache?.tacheId || this.tache.tacheId !== payload.tacheId) {
      return;
    }
    const request: AssignerTacheRequest = { personnelId: payload.personnelId };
    this.workInProgress = true;
    this.tachesService
      .assigner(payload.tacheId, request)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.workInProgress = false;
          this.assignationModalOpen = false;
        }),
      )
      .subscribe({
        next: (updated) => {
          this.tache = updated;
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('menage.tache.messages.assignerSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('menage.tache.messages.assignerError'),
          });
        },
      });
  }

  commencer(): void {
    if (!this.tache?.tacheId || !this.canCommencer()) {
      return;
    }
    const id = this.tache.tacheId;
    this.workInProgress = true;
    this.tachesService
      .commencer(id)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.workInProgress = false)),
      )
      .subscribe({
        next: (updated) => {
          this.tache = updated;
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('menage.tache.messages.commencerSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('menage.tache.messages.commencerError'),
          });
        },
      });
  }

  async terminer(): Promise<void> {
    if (!this.tache?.tacheId || !this.canTerminer()) {
      return;
    }
    const id = this.tache.tacheId;
    const result = await Swal.fire({
      title: this.i18n.translate('menage.tache.messages.terminerTitle'),
      html: `
        <textarea id="swal-d-commentaires" class="swal2-textarea"
          placeholder="${this.i18n.translate('menage.tache.fields.commentaires')}"></textarea>
        <input id="swal-d-note" type="number" min="1" max="5" class="swal2-input"
          placeholder="${this.i18n.translate('menage.tache.fields.noteQualite')} (1-5)" />
      `,
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('menage.tache.actions.terminer'),
      cancelButtonText: this.i18n.translate('menage.actions.cancel'),
      reverseButtons: true,
      preConfirm: () => {
        const cmtEl = document.getElementById('swal-d-commentaires') as HTMLTextAreaElement | null;
        const noteEl = document.getElementById('swal-d-note') as HTMLInputElement | null;
        const commentaires = cmtEl?.value?.trim() || undefined;
        const noteRaw = noteEl?.value?.trim();
        const noteQualite = noteRaw ? Number(noteRaw) : undefined;
        if (noteQualite != null && (noteQualite < 1 || noteQualite > 5)) {
          Swal.showValidationMessage(
            this.i18n.translate('menage.tache.errors.noteRange'),
          );
          return undefined;
        }
        const payload: TerminerTacheRequest = { commentaires, noteQualite };
        return payload;
      },
    });

    if (!result.isConfirmed || !result.value) {
      return;
    }
    this.workInProgress = true;
    this.tachesService
      .terminer(id, result.value as TerminerTacheRequest)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.workInProgress = false)),
      )
      .subscribe({
        next: (updated) => {
          this.tache = updated;
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('menage.tache.messages.terminerSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('menage.tache.messages.terminerError'),
          });
        },
      });
  }

  // ────────────────────────────────────────────────────────────────────────
  // Helpers de présentation
  // ────────────────────────────────────────────────────────────────────────

  prioriteBadgeClass(p?: PrioriteTache): string {
    if (p === 3) return 'text-bg-danger';
    if (p === 2) return 'text-bg-warning';
    return 'text-bg-secondary';
  }

  canAssigner(): boolean {
    return !!this.tache && !this.tache.terminee;
  }

  canCommencer(): boolean {
    return !!this.tache && !this.tache.enCours && !this.tache.terminee && this.tache.personnelId != null;
  }

  canTerminer(): boolean {
    return !!this.tache && this.tache.enCours === true && !this.tache.terminee;
  }

  /** Désérialise le JSON `'["aspirateur","detergent"]'` en liste affichable. */
  parseMateriel(json: string | undefined): string[] {
    if (!json) {
      return [];
    }
    try {
      const parsed: unknown = JSON.parse(json);
      if (Array.isArray(parsed)) {
        return parsed.filter((x): x is string => typeof x === 'string');
      }
    } catch {
      return [json];
    }
    return [];
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private loadTache(id: number): void {
    this.tachesService
      .findById(id)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((t) => {
        if (!t) {
          return;
        }
        this.tache = t;
        this.state = 'ready';
      });
  }

  private loadPersonnels(): void {
    this.personnelsService
      .findActifs()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of([] as Personnel[])),
      )
      .subscribe((list) => {
        this.personnels = list;
      });
  }
}
