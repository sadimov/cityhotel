import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  NgZone,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import $ from 'jquery';
import 'datatables.net';
import 'datatables.net-bs5';
import type { Api as DtApi } from 'datatables.net';

import { TranslationService } from '../../../../services/translation.service';
import { Planning } from '../../models/planning.model';
import { PlanningService } from '../../services/planning.service';
import { dtLang } from './dt-language.helper';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste des plannings de personnel ménage — premier composant du projet
 * à utiliser **DataTables.net direct** (jQuery `$.fn.dataTable`).
 *
 * Choix d'architecture (cf. brief Tour 28) :
 *  - `serverSide: false` — le volume des plannings reste raisonnable
 *    (quelques dizaines de lignes / hôtel / semaine), un filtrage
 *    client suffit. La recherche full-text et le tri sont gérés par
 *    DataTables sur le jeu déjà chargé.
 *  - `pagingType: 'full_numbers'` — exigence brief.
 *  - `language: dtLang(currentLang)` — i18n via le helper local.
 *  - **Filtres custom** (étage / disponibilité) appliqués via
 *    `column().search()` puis `draw()`.
 *
 * NOTE — divergence brief vs spec backend :
 * Le brief décrit des colonnes « date | personnel | etage | secteur |
 * statut ». La spec backend Planning expose plutôt
 * `dateTravail / personnelId / heureDebut / heureFin / disponible`.
 * On affiche ce qui existe réellement (date / personnel / horaires /
 * disponibilité / commentaires) et on garde le filtre « étage » en
 * TODO commenté côté template — à implémenter quand le backend
 * matérialisera ce champ. Le filtre « statut » est rendu sous forme
 * de filtre `disponible` (oui / non / tous), sémantiquement le plus
 * proche.
 *
 * Cycle de vie DataTables (cf. cityfrontend/CLAUDE.md §4.2) :
 *  1. `ngOnInit` charge la donnée (HTTP).
 *  2. `ngAfterViewInit` initialise l'instance DataTables sur la table
 *     (déjà rendue par Angular).
 *  3. Sur changement de filtre, on appelle `dt.draw()` après mise à
 *     jour des `column().search()`.
 *  4. `ngOnDestroy` détruit l'instance pour éviter une fuite mémoire
 *     entre navigations (DataTables conserve sinon un cache global).
 *
 * Important : la table est rendue par `@for` Angular avant l'init
 * DataTables. Pour éviter une race-condition entre Angular CD et
 * DataTables (qui veut un DOM stable), on utilise `runOutsideAngular`
 * sur l'init et un `ChangeDetectorRef.detectChanges()` après le
 * fetch initial.
 */
@Component({
  selector: 'app-planning-list',
  templateUrl: './planning-list.component.html',
  styleUrls: ['./planning-list.component.scss'],
  standalone: false,
})
export class PlanningListComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('planningTable', { static: false })
  tableRef!: ElementRef<HTMLTableElement>;

  state: ListState = 'loading';
  plannings: Planning[] = [];
  // Filtres UI : « disponible » est un proxy du statut tant que le
  // découpage statut/étage n'est pas formalisé côté backend.
  filterEtage = '';
  filterDisponible: '' | 'true' | 'false' = '';

  /** Inventaire des étages connus, dérivé des données chargées. */
  etagesConnus: string[] = [];

  // Instance DataTables — typée via `@types/datatables.net`.
  private dataTableInstance: DtApi | null = null;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly planningService: PlanningService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngAfterViewInit(): void {
    // L'init est déclenchée à chaque rechargement (load()) — ici on ne
    // fait rien : la table n'a pas encore de lignes au tout premier
    // ngAfterViewInit (`load()` est asynchrone).
  }

  ngOnDestroy(): void {
    this.destroyDataTable();
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.state = 'loading';
    this.destroyDataTable();
    this.planningService
      .page({}, 0, 200)
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
        this.plannings = p.content;
        this.etagesConnus = this.computeEtagesConnus(this.plannings);
        this.state = this.plannings.length === 0 ? 'empty' : 'ready';
        // S'assurer que le DOM est rendu avant init DataTables.
        this.cdr.detectChanges();
        if (this.state === 'ready') {
          this.initDataTable();
        }
      });
  }

  onFilterEtageChange(value: string): void {
    this.filterEtage = value;
    this.applyFilters();
  }

  onFilterDisponibleChange(value: string): void {
    if (value === 'true' || value === 'false' || value === '') {
      this.filterDisponible = value;
      this.applyFilters();
    }
  }

  createNew(): void {
    this.router.navigate(['/menage/planning/new']);
  }

  edit(planning: Planning): void {
    if (planning.planningId == null) {
      return;
    }
    this.router.navigate(['/menage/planning', planning.planningId]);
  }

  remove(planning: Planning): void {
    if (planning.planningId == null) {
      return;
    }
    const id = planning.planningId;
    Swal.fire({
      title: this.i18n.translate('menage.planning.messages.deleteConfirm'),
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('menage.planning.actions.delete'),
      cancelButtonText: this.i18n.translate('menage.actions.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.planningService
        .delete(id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('menage.planning.messages.deleteSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('menage.planning.messages.deleteError'),
            });
          },
        });
    });
  }

  // ────────────────────────────────────────────────────────────────────────
  // DataTables — privé
  // ────────────────────────────────────────────────────────────────────────

  /**
   * Initialise (ou ré-initialise) l'instance DataTables.
   * Doit être appelée après que le `@for` Angular a peuplé `<tbody>`.
   */
  private initDataTable(): void {
    if (!this.tableRef?.nativeElement) {
      return;
    }
    // Détruire l'instance existante avant ré-init pour éviter
    // l'erreur DataTables « Cannot reinitialise table ».
    this.destroyDataTable();

    const lang = this.i18n.getCurrentLanguage();
    const tableEl = this.tableRef.nativeElement;

    // L'init DataTables manipule le DOM directement — on la sort de
    // la zone Angular pour ne pas déclencher de change detection inutile.
    this.zone.runOutsideAngular(() => {
      this.dataTableInstance = $(tableEl).DataTable({
        pagingType: 'full_numbers',
        pageLength: 10,
        lengthMenu: [5, 10, 25, 50],
        order: [[0, 'desc']],
        language: dtLang(lang),
        // Pas d'ajax : on alimente via le DOM rendu par Angular.
        // Pas de columns: on s'appuie sur le markup statique.
        // `autoWidth: false` évite des recalculs Bootstrap décoratifs.
        autoWidth: false,
        // Désactiver le tri sur la dernière colonne (actions).
        columnDefs: [{ orderable: false, targets: -1 }],
      });
    });
  }

  private destroyDataTable(): void {
    if (this.dataTableInstance) {
      this.zone.runOutsideAngular(() => {
        this.dataTableInstance?.destroy();
      });
      this.dataTableInstance = null;
    }
  }

  /**
   * Recalcule les filtres DataTables et déclenche un redraw.
   *
   * - Filtre étage : column().search() sur la colonne étage (index 2).
   *   Comme le champ « étage » n'est pas encore matérialisé côté
   *   backend, on utilise actuellement la chaîne vide → filtre inerte
   *   tant que le data model n'expose pas cet attribut.
   * - Filtre disponible : column().search() sur la colonne 5
   *   (« Disponible »).
   */
  private applyFilters(): void {
    if (!this.dataTableInstance) {
      return;
    }
    this.zone.runOutsideAngular(() => {
      // Indices à maintenir cohérents avec l'ordre des <th> dans le HTML.
      // 0: Date | 1: Personnel | 2: Étage (placeholder) | 3: Secteur
      // (placeholder) | 4: Horaire | 5: Disponible | 6: Commentaires | 7: Actions
      this.dataTableInstance!.column(2)
        .search(this.filterEtage)
        .column(5)
        .search(
          this.filterDisponible === ''
            ? ''
            : this.formatDisponible(this.filterDisponible === 'true'),
        )
        .draw();
    });
  }

  // ────────────────────────────────────────────────────────────────────────
  // Helpers de présentation
  // ────────────────────────────────────────────────────────────────────────

  formatHoraire(planning: Planning): string {
    const debut = planning.heureDebut?.slice(0, 5) ?? '';
    const fin = planning.heureFin?.slice(0, 5) ?? '';
    return debut && fin ? `${debut} → ${fin}` : '';
  }

  formatDisponible(disponible: boolean | undefined): string {
    if (disponible === undefined) {
      return '';
    }
    return this.i18n.translate(
      disponible ? 'menage.planning.statut.disponible' : 'menage.planning.statut.indisponible',
    );
  }

  badgeDisponibleClass(disponible: boolean | undefined): string {
    if (disponible === undefined) return 'text-bg-secondary';
    return disponible ? 'text-bg-success' : 'text-bg-secondary';
  }

  /**
   * Heuristique : tant que `etage` n'est pas matérialisé sur Planning,
   * cette liste reste vide. Lorsque le backend ajoutera l'attribut,
   * basculer ici sur `[...new Set(plannings.map(p => p.etage))]`.
   */
  private computeEtagesConnus(_plannings: Planning[]): string[] {
    return [];
  }
}
