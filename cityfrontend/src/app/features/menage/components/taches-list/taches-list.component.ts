import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { Personnel } from '../../models/personnel.model';
import {
  AssignerTacheRequest,
  FiltresTaches,
  PRIORITES_TACHE,
  PrioriteTache,
  Tache,
  TYPES_NETTOYAGE,
  TypeNettoyage,
} from '../../models/tache.model';
import { PersonnelsService } from '../../services/personnels.service';
import { TachesService } from '../../services/taches.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface TachesPageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresTaches;
}

/**
 * Liste paginée des tâches de ménage.
 *
 * Pattern aligné sur les listes des autres modules (Tour 16/19/23) :
 *  - table Bootstrap + pagination maison + états loading/error/empty/ready
 *  - recherche serveur avec debounce 300 ms
 *  - filtres : date, type nettoyage, priorité, raccourcis (en cours,
 *    en retard, non assignées)
 *  - actions ligne workflow : assigner (modal SweetAlert avec sélecteur
 *    personnel), commencer, terminer (modal avec rapport), édition,
 *    suppression
 *
 * Le sélecteur de personnel pour l'assignation utilise la liste des
 * actifs chargée au montage (pas les seuls disponibles : un GERANT
 * peut vouloir forcer l'assignation à un agent en repos pour
 * planification ultérieure).
 */
@Component({
  selector: 'app-taches-list',
  templateUrl: './taches-list.component.html',
  styleUrls: ['./taches-list.component.scss'],
  standalone: false,
})
export class TachesListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<Tache> | null = null;
  request: TachesPageRequest = {
    page: 0,
    size: 10,
    sortBy: 'datePlanifiee',
    sortDir: 'desc',
    filtres: {},
  };
  searchTerm = '';
  personnels: Personnel[] = [];
  readonly typesNettoyage: ReadonlyArray<TypeNettoyage> = TYPES_NETTOYAGE;
  readonly priorites: ReadonlyArray<PrioriteTache> = PRIORITES_TACHE;

  // Indicateurs de loading pour actions ligne
  workingId: number | null = null;
  deleting = false;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly tachesService: TachesService,
    private readonly personnelsService: PersonnelsService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.loadPersonnels();
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
    this.tachesService
      .page(
        this.request.filtres,
        this.request.page,
        this.request.size,
        this.request.sortBy,
        this.request.sortDir,
      )
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((p) => {
        if (!p) {
          return;
        }
        this.page = p;
        this.state = p.numberOfElements === 0 ? 'empty' : 'ready';
      });
  }

  onSearchChange(value: string): void {
    this.searchTerm = value;
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => {
      this.request = {
        ...this.request,
        page: 0,
        filtres: { ...this.request.filtres, search: value.trim() || undefined },
      };
      this.load();
    }, 300);
  }

  onDateFilterChange(value: string): void {
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, date: value || undefined },
    };
    this.load();
  }

  onTypeFilterChange(value: string): void {
    const typeNettoyage = (value as TypeNettoyage) || undefined;
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, typeNettoyage },
    };
    this.load();
  }

  onPrioriteFilterChange(value: string): void {
    let priorite: PrioriteTache | undefined;
    if (value === '1' || value === '2' || value === '3') {
      priorite = Number(value) as PrioriteTache;
    }
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, priorite },
    };
    this.load();
  }

  onShortcutChange(value: string): void {
    const filtres: FiltresTaches = {
      ...this.request.filtres,
      enCours: false,
      enRetard: false,
      nonAssignees: false,
    };
    if (value === 'enCours') {
      filtres.enCours = true;
    } else if (value === 'enRetard') {
      filtres.enRetard = true;
    } else if (value === 'nonAssignees') {
      filtres.nonAssignees = true;
    }
    this.request = { ...this.request, page: 0, filtres };
    this.load();
  }

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) {
      return;
    }
    this.request = { ...this.request, page };
    this.load();
  }

  createNew(): void {
    this.router.navigate(['/menage/taches/new']);
  }

  edit(tache: Tache): void {
    if (tache.tacheId == null) {
      return;
    }
    this.router.navigate(['/menage/taches', tache.tacheId]);
  }

  /**
   * Workflow : assignation à un personnel via SweetAlert2 + select.
   * Pas de modal NgBootstrap dédiée pour rester léger sur ce tour.
   */
  async assigner(tache: Tache): Promise<void> {
    if (tache.tacheId == null) {
      return;
    }
    const id = tache.tacheId;

    // Construire les options du select à partir des personnels actifs
    const options: Record<string, string> = {};
    for (const p of this.personnels) {
      if (p.personnelId != null) {
        options[String(p.personnelId)] = `${p.prenom} ${p.nom} (${p.numeroEmploye})`;
      }
    }

    const result = await Swal.fire({
      title: this.i18n.translate('menage.tache.messages.assignerTitle'),
      input: 'select',
      inputOptions: options,
      inputPlaceholder: this.i18n.translate('menage.tache.messages.assignerSelect'),
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('menage.tache.actions.assigner'),
      cancelButtonText: this.i18n.translate('menage.actions.cancel'),
      reverseButtons: true,
      inputValidator: (value: string) => {
        return value
          ? null
          : this.i18n.translate('menage.tache.errors.personnelRequired');
      },
    });

    if (!result.isConfirmed || !result.value) {
      return;
    }

    const payload: AssignerTacheRequest = {
      personnelId: Number(result.value),
    };

    this.workingId = id;
    this.tachesService
      .assigner(id, payload)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.workingId = null;
        }),
      )
      .subscribe({
        next: () => {
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('menage.tache.messages.assignerSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
          this.load();
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('menage.tache.messages.assignerError'),
          });
        },
      });
  }

  commencer(tache: Tache): void {
    if (tache.tacheId == null) {
      return;
    }
    const id = tache.tacheId;
    this.workingId = id;
    this.tachesService
      .commencer(id)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.workingId = null;
        }),
      )
      .subscribe({
        next: () => {
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('menage.tache.messages.commencerSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
          this.load();
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('menage.tache.messages.commencerError'),
          });
        },
      });
  }

  /**
   * Termine la tâche avec rapport simple (commentaires + note qualité).
   * Le rapport plus complet (problèmes, matériel) est géré dans le
   * formulaire d'édition pour ne pas surcharger ce flow rapide.
   */
  async terminer(tache: Tache): Promise<void> {
    if (tache.tacheId == null) {
      return;
    }
    const id = tache.tacheId;

    const result = await Swal.fire({
      title: this.i18n.translate('menage.tache.messages.terminerTitle'),
      html: `
        <textarea id="swal-commentaires" class="swal2-textarea"
          placeholder="${this.i18n.translate('menage.tache.fields.commentaires')}"></textarea>
        <input id="swal-note" type="number" min="1" max="5" class="swal2-input"
          placeholder="${this.i18n.translate('menage.tache.fields.noteQualite')} (1-5)" />
      `,
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('menage.tache.actions.terminer'),
      cancelButtonText: this.i18n.translate('menage.actions.cancel'),
      reverseButtons: true,
      preConfirm: () => {
        const cmtEl = document.getElementById('swal-commentaires') as HTMLTextAreaElement | null;
        const noteEl = document.getElementById('swal-note') as HTMLInputElement | null;
        const commentaires = cmtEl?.value?.trim() || undefined;
        const noteRaw = noteEl?.value?.trim();
        const noteQualite = noteRaw ? Number(noteRaw) : undefined;
        if (noteQualite != null && (noteQualite < 1 || noteQualite > 5)) {
          Swal.showValidationMessage(
            this.i18n.translate('menage.tache.errors.noteRange'),
          );
          return undefined;
        }
        return { commentaires, noteQualite };
      },
    });

    if (!result.isConfirmed || !result.value) {
      return;
    }

    this.workingId = id;
    this.tachesService
      .terminer(id, result.value)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.workingId = null;
        }),
      )
      .subscribe({
        next: () => {
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('menage.tache.messages.terminerSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
          this.load();
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('menage.tache.messages.terminerError'),
          });
        },
      });
  }

  remove(tache: Tache): void {
    if (tache.tacheId == null) {
      return;
    }
    const id = tache.tacheId;
    Swal.fire({
      title: this.i18n.translate('menage.tache.messages.deleteConfirm'),
      text: tache.numeroChambre
        ? `${this.i18n.translate('menage.tache.fields.chambre')} ${tache.numeroChambre}`
        : '',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('menage.tache.actions.delete'),
      cancelButtonText: this.i18n.translate('menage.actions.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.deleting = true;
      this.tachesService
        .delete(id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.deleting = false;
          }),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('menage.tache.messages.deleteSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('menage.tache.messages.deleteError'),
            });
          },
        });
    });
  }

  get pagesArray(): number[] {
    if (!this.page) {
      return [];
    }
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  /**
   * Détermine si la tâche peut être démarrée (statut planifié/assigné
   * et pas encore démarrée). On se base sur les drapeaux dénormalisés
   * exposés par le backend (`enCours`, `terminee`).
   */
  canCommencer(t: Tache): boolean {
    return !t.enCours && !t.terminee && t.personnelId != null;
  }

  canTerminer(t: Tache): boolean {
    return t.enCours === true && !t.terminee;
  }

  canAssigner(t: Tache): boolean {
    return !t.terminee;
  }

  prioriteBadgeClass(p?: PrioriteTache): string {
    if (p === 3) return 'text-bg-danger';
    if (p === 2) return 'text-bg-warning';
    return 'text-bg-secondary';
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

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
