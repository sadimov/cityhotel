import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import {
  addDays,
  addMonths,
  differenceInCalendarDays,
  endOfMonth,
  format,
  isAfter,
  isBefore,
  isSameDay,
  isSameMonth,
  parseISO,
  startOfMonth,
  startOfWeek,
  subMonths,
} from 'date-fns';
import { EMPTY, forkJoin, Observable, of, Subject } from 'rxjs';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  map,
  switchMap,
  takeUntil,
} from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { AuthService } from '../../../../services/auth.service';
import { ClientsService } from '../../../clients/services/clients.service';
import { Client, ClientCreate } from '../../../clients/models/client.model';
import { Societe, SocieteCreate } from '../../../clients/models/societe.model';
import { Chambre } from '../../models/chambre.model';
import {
  CalendarEventDto,
  CalendarEventType,
} from '../../models/calendar-event.model';
import {
  NuiteeModificationDto,
  NuiteeMontantUpdate,
} from '../../models/nuitee-modification.model';
import {
  LigneFactureRecapDto,
  ModePaiement,
  RecapPaiementsReservationDto,
  STATUT_FACTURE_BADGE_MAP,
  STATUT_PAIEMENT_BADGE_MAP,
} from '../../models/paiements-recap.model';
import {
  PaiementGlobalRequest,
  PaiementLignesRequest,
  TransfererLignesRequest,
} from '../../models/paiement-lignes.model';
import { FolioDto } from '../../models/folio.model';
import {
  CreerReservationRequest,
  ModifierReservationRequest,
  Reservation,
  STATUT_RESERVATION_BADGE_MAP,
  STATUT_RESERVATION_CHIP_MAP,
  StatutReservation,
} from '../../models/reservation.model';
import {
  MontantCalculDto,
  MontantCalculOrigine,
} from '../../models/tarif-chambre.model';
import {
  CATEGORIE_ESPACE_DEFAULT,
  CategorieEspace,
  TypeChambre,
} from '../../models/type-chambre.model';
import { CalendarLiveService } from '../../services/calendar-live.service';
import { ChambresService } from '../../services/chambres.service';
import { FolioService } from '../../services/folio.service';
import { NuiteesModifService } from '../../services/nuitees-modif.service';
import { PaiementsService } from '../../services/paiements.service';
import { PaiementsRecapService } from '../../services/paiements-recap.service';
import { ReservationsService } from '../../services/reservations.service';
import { TarifChambreService } from '../../services/tarif-chambre.service';
import { TypesChambreService } from '../../services/types-chambre.service';

type CalendarState = 'loading' | 'ready' | 'error';

interface RoomGroup {
  type: TypeChambre;
  chambres: Chambre[];
}

interface ContextMenuState {
  visible: boolean;
  x: number;
  y: number;
  reservation: Reservation | null;
}

interface SelectionState {
  active: boolean;
  chambreId: number | null;
  startDate: Date | null;
  endDate: Date | null;
}

/** État de l'appel API de tarification (Phase 2). */
interface TarifState {
  loading: boolean;
  data: MontantCalculDto | null;
  /** Visible toggle du bloc détail jour-par-jour. */
  detailVisible: boolean;
}

/** État de la modale Paiements (Phase 2 + Tour 45 + Tour 46). */
interface PaymentsModalState {
  reservationId: number | null;
  reservation: Reservation | null;
  reservationLabel: string;
  loading: boolean;
  data: RecapPaiementsReservationDto | null;
  /**
   * Lignes facture chargées via
   * `GET /api/finance/factures/lignes-by-reservation/{id}` (Tour 45 fix dette
   * technique). Tour 46 — désormais affichées en lecture seule, sans
   * checkbox : le paiement se fait globalement via le formulaire en bas de
   * modale, le backend défalque automatiquement sur les lignes.
   */
  lignes: LigneFactureRecapDto[];
  error: boolean;
  /**
   * Tour 46 — Folio client (DEBIT/CREDIT) pour la période de séjour de la
   * réservation. Chargé en parallèle du recap + lignes via forkJoin.
   */
  folio: FolioDto | null;
  folioLoading: boolean;
  folioError: boolean;
  /** Tour 46 — Sous-formulaire "Transférer lignes" visible (actions avancées). */
  transferPanelVisible: boolean;
  /** Tour 46 — Sous-formulaire "Check-out express" visible (actions avancées). */
  checkoutExpressPanelVisible: boolean;
  /** Tour 46 — Toggle visibilité de la section "Actions avancées". */
  advancedActionsVisible: boolean;
}

/** État de la modale Modification de nuitées (Tour 45). */
interface ModifyNuiteesState {
  reservationId: number | null;
  reservationLabel: string;
  loading: boolean;
  saving: boolean;
  /** Données initiales — sert de référence pour calculer impact + reset. */
  initial: NuiteeModificationDto[];
  /** Map nuiteeId → montant courant édité dans l'UI. */
  edited: Map<number, number>;
  error: boolean;
}

/** Lignes facture extraites du recap pour l'affichage tableau Tour 45. */
interface PaymentLigneRow {
  ligneFactureId: number;
  factureId: number;
  factureNumero: string;
  description: string;
  /** ISO `yyyy-MM-dd` (jour de la facture). */
  date: string;
  sousTotal: number;
  paye: number;
  reste: number;
}


/**
 * Calendrier des réservations — page d'accueil du module hebergement.
 *
 * Refonte Tour 43 (2026-05-09) puis Tour 44 Phase 2 (2026-05-11).
 *
 * **Tour 43** : grille temporelle (colonnes = jours, lignes = chambres
 * groupées par type), drag manuel, modales Bootstrap natives.
 *
 * **Tour 44 Phase 2 — ajouts** :
 *  - Quick-create client embarqué dans la modale création (panel inline,
 *    pas de sous-modal pour éviter les conflits z-index).
 *  - Tarification réelle via `TarifChambreService.getCalcul` (debounce 300ms
 *    sur changement chambre/dates), avec détail jour-par-jour collapsible.
 *  - Modale "Paiements" : récap factures + paiements pour la réservation
 *    sélectionnée, ouvert via menu contextuel.
 *  - Changement de chambre dans la modale modification (PATCH dédié, confirm
 *    SweetAlert2).
 *  - Refresh temps réel SSE via `CalendarLiveService` : événements
 *    `reservation.created` / `updated` / `deleted` mettent à jour la Map
 *    indexée sans rechargement complet.
 *  - Mapping des codes d'erreur backend vers clés i18n.
 */
@Component({
  selector: 'app-reservations-calendar',
  templateUrl: './reservations-calendar.component.html',
  styleUrls: ['./reservations-calendar.component.scss'],
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservationsCalendarComponent
  implements OnInit, AfterViewInit, OnDestroy
{
  // ── État composant ─────────────────────────────────────────────────────
  state: CalendarState = 'loading';
  saving = false;

  /** Première date affichée (par défaut J-2). */
  viewStart: Date = addDays(new Date(), -2);
  /** Dernière date affichée (par défaut J+27). */
  viewEnd: Date = addDays(new Date(), 27);

  // === Date range picker (en-tête colonne chambres) ===
  pickerOpen = false;
  /** true = on attend le clic date début, false = la date fin */
  pickerSelectingStart = true;
  pickerStartDraft: Date | null = null;
  pickerEndDraft: Date | null = null;
  pickerLeftMonth: Date = startOfMonth(new Date());
  pickerRightMonth: Date = addMonths(startOfMonth(new Date()), 1);
  /** Position fixed du popup (calculée au moment de l'ouverture). */
  pickerX = 0;
  pickerY = 0;

  /** Ordre lun→dim pour les en-têtes des mini-calendriers du picker. */
  readonly pickerWeekdayKeys: readonly string[] = [
    'hebergement.calendar.weekday.mon',
    'hebergement.calendar.weekday.tue',
    'hebergement.calendar.weekday.wed',
    'hebergement.calendar.weekday.thu',
    'hebergement.calendar.weekday.fri',
    'hebergement.calendar.weekday.sat',
    'hebergement.calendar.weekday.sun',
  ];

  days: Date[] = [];
  private dayIndex = new Map<string, number>();

  rooms: Chambre[] = [];
  types: TypeChambre[] = [];
  groups: RoomGroup[] = [];

  /** Liste des clients pour le select (chargée à l'init + après quick-create). */
  clients: Client[] = [];
  /** Index `clientId → Client` recalculé après chaque mutation de {@link clients}. */
  private clientsById = new Map<number, Client>();

  /** Liste des sociétés pour le select (chargée à l'init + après quick-create). */
  societes: Societe[] = [];
  /** Index `societeId → Societe`. */
  private societesById = new Map<number, Societe>();

  reservations: Reservation[] = [];
  private reservationsByRoom = new Map<number, Reservation[]>();

  selection: SelectionState = {
    active: false,
    chambreId: null,
    startDate: null,
    endDate: null,
  };

  contextMenu: ContextMenuState = {
    visible: false,
    x: 0,
    y: 0,
    reservation: null,
  };

  consultReservation: Reservation | null = null;
  editingReservation: Reservation | null = null;

  /** Tarification en cours dans la modale création / modification. */
  tarif: TarifState = { loading: false, data: null, detailVisible: false };

  /** État modale paiements (Tour 46 — refonte 3-sections + folio + global). */
  paymentsModal: PaymentsModalState = {
    reservationId: null,
    reservation: null,
    reservationLabel: '',
    loading: false,
    data: null,
    lignes: [],
    error: false,
    folio: null,
    folioLoading: false,
    folioError: false,
    transferPanelVisible: false,
    checkoutExpressPanelVisible: false,
    advancedActionsVisible: false,
  };

  /**
   * Tour 46 — Index `chambreId → Chambre` reconstruit après chargement
   * des chambres. Sert au libellé de chambre dans la section "Détails
   * réservation" de la modale paiements.
   */
  private roomsById = new Map<number, Chambre>();

  /** État modale modification de nuitées (Tour 45). */
  modifyNuiteesModal: ModifyNuiteesState = {
    reservationId: null,
    reservationLabel: '',
    loading: false,
    saving: false,
    initial: [],
    edited: new Map<number, number>(),
    error: false,
  };

  /** Panel quick-create client visible dans une modale ? */
  quickCreateClientVisible = false;

  /** Panel quick-create société visible dans une modale ? */
  quickCreateSocieteVisible = false;

  /**
   * Tour 49 — Contexte d'ouverture des panels quick-create. Permet à
   * `submitQuickCreate*` de patcher le bon formulaire (`createForm` si on
   * vient de la modale création, `editForm` si on vient de la modale édition).
   * `null` quand aucun panel n'est ouvert.
   */
  private quickCreateContext: 'create' | 'edit' | null = null;

  /** Modes de paiement disponibles (alignés sur backend, cf. modes_paiements.txt). */
  readonly modesPaiement: readonly ModePaiement[] = [
    'ESPECES',
    'CHEQUE',
    'BANKILY',
    'CARTE_BANCAIRE',
    'MASRIVI',
    'SEDAD',
    'CLICK',
    'AMANETY',
    'BFI_CASH',
    'MOOV_MONEY',
    'GAZAPAY',
    'VIREMENT',
  ];

  // ── Modales : refs DOM (matchent les #templateRef du HTML) ─────────────
  @ViewChild('createModal', { static: true }) createModalRef?: ElementRef<HTMLDivElement>;
  @ViewChild('consultModal', { static: true }) consultModalRef?: ElementRef<HTMLDivElement>;
  @ViewChild('editModal', { static: true }) editModalRef?: ElementRef<HTMLDivElement>;
  @ViewChild('paymentsModalEl', { static: true }) paymentsModalRef?: ElementRef<HTMLDivElement>;
  @ViewChild('modifyNuiteesModalEl', { static: true }) modifyNuiteesModalRef?: ElementRef<HTMLDivElement>;

  private createModalInstance: BootstrapModal | null = null;
  private consultModalInstance: BootstrapModal | null = null;
  private editModalInstance: BootstrapModal | null = null;
  private paymentsModalInstance: BootstrapModal | null = null;
  private modifyNuiteesModalInstance: BootstrapModal | null = null;

  // ── Formulaires ────────────────────────────────────────────────────────
  createForm!: FormGroup;
  editForm!: FormGroup;
  clientQuickCreateForm!: FormGroup;
  societeQuickCreateForm!: FormGroup;
  /**
   * Tour 46 — Formulaire principal "Encaisser un paiement" (paiement global
   * pour la réservation). Remplace l'ancien `payerForm` Tour 45 qui était
   * lié à la sélection de lignes.
   */
  payerGlobalForm!: FormGroup;
  /** Formulaire du sous-panel "Transférer lignes" (actions avancées Tour 46). */
  transferForm!: FormGroup;
  /** Formulaire du sous-panel "Check-out express" (sélection société). */
  checkoutExpressForm!: FormGroup;
  createTargetRoom: Chambre | null = null;
  createDateDebut: Date | null = null;
  createDateFin: Date | null = null;

  /** Énum exposé pour le template. */
  readonly StatutReservation = StatutReservation;

  /** Helpers de lookup pour la modale Paiements — évitent un index typé strict
   *  dans le template (Angular strictTemplates). */
  factureBadgeClass(statut: string): string {
    return (
      (STATUT_FACTURE_BADGE_MAP as Record<string, string>)[statut] ??
      'text-bg-light'
    );
  }
  paiementBadgeClass(statut: string): string {
    return (
      (STATUT_PAIEMENT_BADGE_MAP as Record<string, string>)[statut] ??
      'text-bg-light'
    );
  }

  /** Trigger debounced pour la tarification (Phase 2). */
  private readonly tarifTrigger$ = new Subject<{
    typeChambreId: number;
    dateDebut: string;
    dateFin: string;
  }>();
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly cdr: ChangeDetectorRef,
    private readonly router: Router,
    private readonly reservationsService: ReservationsService,
    private readonly chambresService: ChambresService,
    private readonly typesChambreService: TypesChambreService,
    private readonly clientsService: ClientsService,
    private readonly tarifChambreService: TarifChambreService,
    private readonly paiementsRecapService: PaiementsRecapService,
    private readonly paiementsService: PaiementsService,
    private readonly nuiteesModifService: NuiteesModifService,
    private readonly folioService: FolioService,
    private readonly calendarLive: CalendarLiveService,
    private readonly i18n: TranslationService,
    private readonly authService: AuthService,
  ) {}

  ngOnInit(): void {
    this.buildForms();
    this.generateDays();
    this.loadAll();
    this.subscribeTarifTrigger();
    this.subscribeLiveEvents();
  }

  ngAfterViewInit(): void {
    // Les instances Bootstrap sont créées paresseusement dans `showModal`.
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.calendarLive.disconnect();
    this.createModalInstance?.dispose?.();
    this.consultModalInstance?.dispose?.();
    this.editModalInstance?.dispose?.();
    this.paymentsModalInstance?.dispose?.();
    this.modifyNuiteesModalInstance?.dispose?.();
  }

  // ── Navigation ─────────────────────────────────────────────────────────

  prevPeriod(): void {
    this.viewStart = subMonths(this.viewStart, 1);
    this.viewEnd = subMonths(this.viewEnd, 1);
    this.generateDays();
    this.loadReservations();
  }

  nextPeriod(): void {
    this.viewStart = addMonths(this.viewStart, 1);
    this.viewEnd = addMonths(this.viewEnd, 1);
    this.generateDays();
    this.loadReservations();
  }

  goToday(): void {
    this.viewStart = addDays(new Date(), -2);
    this.viewEnd = addDays(new Date(), 27);
    this.generateDays();
    this.loadReservations();
  }

  goCurrentMonth(): void {
    this.viewStart = startOfMonth(new Date());
    this.viewEnd = endOfMonth(new Date());
    this.generateDays();
    this.loadReservations();
  }

  // ── Date range picker (en-tête colonne chambres) ───────────────────────

  togglePicker(event: MouseEvent): void {
    event.stopPropagation();
    if (this.pickerOpen) {
      this.closePicker();
      return;
    }
    const btn = event.currentTarget as HTMLElement;
    const rect = btn.getBoundingClientRect();
    this.pickerX = rect.left;
    this.pickerY = rect.bottom + 4;
    this.pickerStartDraft = this.viewStart;
    this.pickerEndDraft = this.viewEnd;
    this.pickerSelectingStart = true;
    this.pickerLeftMonth = startOfMonth(this.viewStart);
    this.pickerRightMonth = addMonths(this.pickerLeftMonth, 1);
    this.pickerOpen = true;
    this.cdr.markForCheck();
  }

  closePicker(): void {
    if (!this.pickerOpen) return;
    this.pickerOpen = false;
    this.cdr.markForCheck();
  }

  /** Génère la grille 6×7 = 42 jours d'un mini-calendrier (semaine lun→dim). */
  pickerDaysFor(month: Date): Date[] {
    const firstCell = startOfWeek(startOfMonth(month), { weekStartsOn: 1 });
    return Array.from({ length: 42 }, (_, i) => addDays(firstCell, i));
  }

  pickerSelectDay(day: Date): void {
    if (this.pickerSelectingStart) {
      this.pickerStartDraft = day;
      this.pickerEndDraft = null;
      this.pickerSelectingStart = false;
    } else {
      if (this.pickerStartDraft && isBefore(day, this.pickerStartDraft)) {
        // Si l'utilisateur clique avant la date début, on inverse.
        this.pickerEndDraft = this.pickerStartDraft;
        this.pickerStartDraft = day;
      } else {
        this.pickerEndDraft = day;
      }
      this.applyPickerRange();
    }
    this.cdr.markForCheck();
  }

  pickerPrevMonth(): void {
    this.pickerLeftMonth = subMonths(this.pickerLeftMonth, 1);
    this.pickerRightMonth = subMonths(this.pickerRightMonth, 1);
    this.cdr.markForCheck();
  }

  pickerNextMonth(): void {
    this.pickerLeftMonth = addMonths(this.pickerLeftMonth, 1);
    this.pickerRightMonth = addMonths(this.pickerRightMonth, 1);
    this.cdr.markForCheck();
  }

  pickerIsStart(day: Date): boolean {
    return !!this.pickerStartDraft && isSameDay(day, this.pickerStartDraft);
  }

  pickerIsEnd(day: Date): boolean {
    return !!this.pickerEndDraft && isSameDay(day, this.pickerEndDraft);
  }

  pickerIsInRange(day: Date): boolean {
    if (!this.pickerStartDraft || !this.pickerEndDraft) return false;
    return isAfter(day, this.pickerStartDraft) && isBefore(day, this.pickerEndDraft);
  }

  pickerIsOtherMonth(day: Date, refMonth: Date): boolean {
    return !isSameMonth(day, refMonth);
  }

  pickerIsToday(day: Date): boolean {
    return isSameDay(day, new Date());
  }

  private applyPickerRange(): void {
    if (!this.pickerStartDraft || !this.pickerEndDraft) return;
    this.viewStart = this.pickerStartDraft;
    this.viewEnd = this.pickerEndDraft;
    this.generateDays();
    this.loadReservations();
    this.pickerOpen = false;
  }

  // ── Helpers grille ─────────────────────────────────────────────────────

  trackByDate = (_: number, date: Date): string => format(date, 'yyyy-MM-dd');

  isWeekend(date: Date): boolean {
    const d = date.getDay();
    return d === 0 || d === 6;
  }

  isToday(date: Date): boolean {
    return isSameDay(date, new Date());
  }

  isPast(date: Date): boolean {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return isBefore(date, today);
  }

  weekdayKey(date: Date): string {
    const keys = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];
    return `hebergement.calendar.weekday.${keys[date.getDay()]}`;
  }

  dayNumber(date: Date): number {
    return date.getDate();
  }

  reservationStartingHere(chambreId: number, date: Date): Reservation | null {
    const list = this.reservationsByRoom.get(chambreId);
    if (!list) return null;
    return (
      list.find(
        (r) =>
          r.statut !== StatutReservation.ANNULEE &&
          r.dateArrivee &&
          this.toDate(r.dateArrivee) &&
          isSameDay(this.toDate(r.dateArrivee)!, date),
      ) ?? null
    );
  }

  isCellOccupied(chambreId: number, date: Date): boolean {
    const list = this.reservationsByRoom.get(chambreId);
    if (!list) return false;
    return list.some((r) => {
      if (r.statut === StatutReservation.ANNULEE) return false;
      const start = this.toDate(r.dateArrivee);
      const end = this.toDate(r.dateDepart);
      if (!start || !end) return false;
      return !isBefore(date, start) && isBefore(date, end);
    });
  }

  reservationSpan(reservation: Reservation): number {
    const start = this.toDate(reservation.dateArrivee);
    const end = this.toDate(reservation.dateDepart);
    if (!start || !end) return 1;
    const visibleStart = isBefore(start, this.viewStart) ? this.viewStart : start;
    const visibleEnd = isAfter(end, this.viewEnd) ? this.viewEnd : end;
    const span = differenceInCalendarDays(visibleEnd, visibleStart);
    return Math.max(1, span);
  }

  renderedDays(chambreId: number): Date[] {
    const out: Date[] = [];
    let i = 0;
    while (i < this.days.length) {
      const day = this.days[i];
      const res = this.reservationStartingHere(chambreId, day);
      if (res) {
        out.push(day);
        i += this.reservationSpan(res);
      } else {
        out.push(day);
        i += 1;
      }
    }
    return out;
  }

  isCellSelected(chambreId: number, date: Date): boolean {
    if (!this.selection.active || this.selection.chambreId !== chambreId) {
      return false;
    }
    const start = this.selection.startDate;
    const end = this.selection.endDate;
    if (!start || !end) return false;
    const lo = isBefore(start, end) ? start : end;
    const hi = isAfter(end, start) ? end : start;
    return !isBefore(date, lo) && !isAfter(date, hi);
  }

  statutChipClass(statut: StatutReservation | undefined): string {
    return STATUT_RESERVATION_CHIP_MAP[statut ?? StatutReservation.CONFIRMEE];
  }

  isStartNear(reservation: Reservation): boolean {
    const d = this.toDate(reservation.dateArrivee);
    if (!d) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const tomorrow = addDays(today, 1);
    return isSameDay(d, today) || isSameDay(d, tomorrow);
  }

  isEndNear(reservation: Reservation): boolean {
    const d = this.toDate(reservation.dateDepart);
    if (!d) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const tomorrow = addDays(today, 1);
    return isSameDay(d, today) || isSameDay(d, tomorrow);
  }

  // ── Drag & drop manuel ─────────────────────────────────────────────────

  startDrag(chambre: Chambre, date: Date, event: MouseEvent): void {
    if (event.button !== 0) return;
    if (this.isPast(date)) return;
    if (chambre.chambreId == null) return;
    if (this.isCellOccupied(chambre.chambreId, date)) return;
    event.preventDefault();
    this.selection = {
      active: true,
      chambreId: chambre.chambreId,
      startDate: date,
      endDate: date,
    };
    this.cdr.markForCheck();
  }

  onDragOver(chambre: Chambre, date: Date): void {
    if (!this.selection.active) return;
    if (chambre.chambreId !== this.selection.chambreId) return;
    if (this.isPast(date)) return;
    if (this.isCellOccupied(chambre.chambreId, date)) return;
    this.selection.endDate = date;
    this.cdr.markForCheck();
  }

  endDrag(chambre: Chambre, date: Date): void {
    if (!this.selection.active) return;
    if (chambre.chambreId !== this.selection.chambreId) {
      this.cancelSelection();
      return;
    }
    const start = this.selection.startDate;
    const end = this.selection.endDate ?? date;
    this.selection.active = false;
    if (!start || !end) {
      this.cancelSelection();
      return;
    }
    const lo = isBefore(start, end) ? start : end;
    const hi = isAfter(end, start) ? end : start;
    const dateArrivee = lo;
    const dateDepart = addDays(hi, 1);
    this.openCreateModal(chambre, dateArrivee, dateDepart);
  }

  cancelSelection(): void {
    this.selection = {
      active: false,
      chambreId: null,
      startDate: null,
      endDate: null,
    };
    this.cdr.markForCheck();
  }

  // ── Menu contextuel (clic droit sur réservation) ───────────────────────

  openContextMenu(event: MouseEvent, reservation: Reservation): void {
    event.preventDefault();
    event.stopPropagation();
    if (reservation.statut === StatutReservation.ANNULEE) return;
    this.contextMenu = {
      visible: true,
      x: event.clientX,
      y: event.clientY,
      reservation,
    };
    this.cdr.markForCheck();
  }

  closeContextMenu(): void {
    if (!this.contextMenu.visible) return;
    this.contextMenu = { visible: false, x: 0, y: 0, reservation: null };
    this.cdr.markForCheck();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    this.closeContextMenu();
    // Ne pas fermer le picker si le click vient de l'intérieur (popup ou trigger).
    const target = event.target as HTMLElement | null;
    if (
      this.pickerOpen &&
      !target?.closest('.reservations-calendar__daterange-picker') &&
      !target?.closest('.reservations-calendar__daterange-input')
    ) {
      this.closePicker();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeContextMenu();
    if (this.pickerOpen) this.closePicker();
    if (this.selection.active) this.cancelSelection();
  }

  // ── Actions menu contextuel ────────────────────────────────────────────

  ctxConsult(): void {
    const r = this.contextMenu.reservation;
    this.closeContextMenu();
    if (r) this.openConsultModal(r);
  }

  ctxModify(): void {
    const r = this.contextMenu.reservation;
    this.closeContextMenu();
    if (r) this.openEditModal(r);
  }

  ctxCheckIn(): void {
    const r = this.contextMenu.reservation;
    this.closeContextMenu();
    if (!r || r.reservationId == null) return;
    this.translateAlertConfirm('hebergement.calendar.confirmCheckIn').then(
      (ok) => {
        if (!ok) return;
        this.saving = true;
        this.cdr.markForCheck();
        this.reservationsService
          .checkIn(r.reservationId!)
          .pipe(
            takeUntil(this.destroy$),
            finalize(() => {
              this.saving = false;
              this.cdr.markForCheck();
            }),
          )
          .subscribe({
            next: () => {
              this.toastSuccess('hebergement.calendar.checkInSuccess');
              this.loadReservations();
            },
            error: (err: HttpErrorResponse) =>
              this.toastError(this.mapBackendError(err)),
          });
      },
    );
  }

  ctxCheckOut(): void {
    const r = this.contextMenu.reservation;
    this.closeContextMenu();
    if (!r || r.reservationId == null) return;
    this.translateAlertConfirm('hebergement.calendar.confirmCheckOut').then(
      (ok) => {
        if (!ok) return;
        this.saving = true;
        this.cdr.markForCheck();
        this.reservationsService
          .checkOut(r.reservationId!)
          .pipe(
            takeUntil(this.destroy$),
            finalize(() => {
              this.saving = false;
              this.cdr.markForCheck();
            }),
          )
          .subscribe({
            next: () => {
              this.toastSuccess('hebergement.calendar.checkOutSuccess');
              this.loadReservations();
            },
            error: (err: HttpErrorResponse) =>
              this.toastError(this.mapBackendError(err)),
          });
      },
    );
  }

  ctxPayments(): void {
    const r = this.contextMenu.reservation;
    this.closeContextMenu();
    if (!r || r.reservationId == null) return;
    this.openPaymentsModal(r);
  }

  /**
   * Tour 45 F3 — Annulation depuis le menu contextuel.
   * Demande un motif via SweetAlert2 (input texte obligatoire) puis appelle
   * `reservationsService.annuler` (URL backend `/cancel`).
   */
  ctxCancel(): void {
    const r = this.contextMenu.reservation;
    this.closeContextMenu();
    if (!r || r.reservationId == null) return;
    if (this.isReservationTerminated(r)) {
      this.toastError('error.reservation.cancel.alreadyTerminated');
      return;
    }
    Swal.fire({
      title: this.i18n.translate('hebergement.calendar.cancel.title'),
      input: 'textarea',
      inputPlaceholder: this.i18n.translate(
        'hebergement.calendar.cancel.motifPlaceholder',
      ),
      inputAttributes: { 'aria-label': 'cancel motif' },
      showCancelButton: true,
      confirmButtonText: this.i18n.translate(
        'hebergement.calendar.cancel.confirm',
      ),
      cancelButtonText: this.i18n.translate('hebergement.actions.cancel'),
      inputValidator: (value) =>
        value && value.trim().length > 0
          ? null
          : this.i18n.translate('hebergement.calendar.cancel.motifRequired'),
    }).then((res) => {
      if (!res.isConfirmed) return;
      const motif = String(res.value || '').trim();
      this.saving = true;
      this.cdr.markForCheck();
      this.reservationsService
        .annuler(r.reservationId!, motif)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.saving = false;
            this.cdr.markForCheck();
          }),
        )
        .subscribe({
          next: () => {
            this.toastSuccess('hebergement.calendar.cancel.success');
            this.loadReservations();
          },
          error: (err: HttpErrorResponse) =>
            this.toastError(this.mapBackendError(err)),
        });
    });
  }

  /** Tour 45 F6 — Ouverture modale "Modifier nuitées" via menu contextuel. */
  ctxModifyNuitees(): void {
    const r = this.contextMenu.reservation;
    this.closeContextMenu();
    if (!r || r.reservationId == null) return;
    this.openModifyNuiteesModal(r);
  }

  /** True si la réservation est dans un état où l'annulation est impossible. */
  isReservationTerminated(r: Reservation): boolean {
    return (
      r.statut === StatutReservation.PARTIE ||
      r.statut === StatutReservation.ANNULEE ||
      r.statut === StatutReservation.NO_SHOW
    );
  }

  /**
   * Règle métier hébergement : seul ADMIN/SUPERADMIN peut annuler une
   * réservation. Le menu contextuel masque l'item "Annuler" pour les
   * autres rôles (GERANT, RECEPTION, RESREC...). Double check côté
   * backend via @PreAuthorize sur /cancel et DELETE.
   */
  get canCancelReservation(): boolean {
    return this.authService.hasAnyRole(['ADMIN', 'SUPERADMIN']);
  }

  /**
   * True si la modification des nuitées est interdite (facture déjà émise
   * ou réservation terminée). Sert pour griser le bouton du menu contextuel.
   */
  canModifyNuitees(r: Reservation): boolean {
    return !this.isReservationTerminated(r);
  }

  // ── Modale Création ────────────────────────────────────────────────────

  openCreateModal(chambre: Chambre, dateArrivee: Date, dateDepart: Date): void {
    this.createTargetRoom = chambre;
    this.createDateDebut = dateArrivee;
    this.createDateFin = dateDepart;
    this.createForm.reset({
      clientPrincipalId: null,
      societeId: null,
      nbAdultes: 1,
      nbEnfants: 0,
      reductionPourcentage: 0,
      motifSejour: '',
      commentaires: '',
      montantTotal: 0,
    });
    this.quickCreateClientVisible = false;
    this.tarif = { loading: false, data: null, detailVisible: false };
    this.cdr.markForCheck();
    queueMicrotask(() => this.showModal('create'));
    // Déclenche un calcul initial.
    this.requestTarif(chambre, dateArrivee, dateDepart);
  }

  closeCreateModal(): void {
    this.hideModal('create');
    this.cancelSelection();
    this.createTargetRoom = null;
    this.createDateDebut = null;
    this.createDateFin = null;
    this.tarif = { loading: false, data: null, detailVisible: false };
    this.quickCreateClientVisible = false;
  }

  get createPeriodNights(): number {
    if (!this.createDateDebut || !this.createDateFin) return 0;
    return Math.max(
      0,
      differenceInCalendarDays(this.createDateFin, this.createDateDebut),
    );
  }

  /** Total TTC après application de la réduction (%). */
  get createTotalAfterReduction(): number {
    const base = this.tarif.data?.montantTtc ?? 0;
    const reduction = Number(this.createForm?.value?.reductionPourcentage ?? 0);
    if (reduction <= 0) return base;
    return base - (base * reduction) / 100;
  }

  /** Total TTC pour la modale d'édition (basé sur les mêmes données tarif). */
  get editTotalAfterReduction(): number {
    const base = this.tarif.data?.montantTtc ?? 0;
    const reduction = Number(this.editForm?.value?.reductionPourcentage ?? 0);
    if (reduction <= 0) return base;
    return base - (base * reduction) / 100;
  }

  /** Recalcul à chaque changement de dates dans la modale création. */
  onCreateDateChange(field: 'debut' | 'fin', value: string): void {
    if (!value) return;
    try {
      const d = parseISO(value);
      if (field === 'debut') {
        this.createDateDebut = d;
      } else {
        this.createDateFin = d;
      }
    } catch {
      return;
    }
    if (this.createTargetRoom && this.createDateDebut && this.createDateFin) {
      this.requestTarif(
        this.createTargetRoom,
        this.createDateDebut,
        this.createDateFin,
      );
    }
  }

  toggleTarifDetail(): void {
    this.tarif.detailVisible = !this.tarif.detailVisible;
    this.cdr.markForCheck();
  }

  tarifOriginKey(origine: MontantCalculOrigine): string {
    return `hebergement.calendar.tarif.origin.${origine}`;
  }

  submitCreate(): void {
    if (
      this.createForm.invalid ||
      !this.createTargetRoom ||
      !this.createDateDebut ||
      !this.createDateFin
    ) {
      this.createForm.markAllAsTouched();
      return;
    }
    const v = this.createForm.value;
    const prixNuit = this.estimatedNightPrice(this.createTargetRoom);
    const dto: CreerReservationRequest = {
      clientPrincipalId: Number(v.clientPrincipalId),
      societeId: v.societeId ? Number(v.societeId) : undefined,
      dateArrivee: format(this.createDateDebut, 'yyyy-MM-dd'),
      dateDepart: format(this.createDateFin, 'yyyy-MM-dd'),
      nbAdultes: Number(v.nbAdultes ?? 1),
      nbEnfants: Number(v.nbEnfants ?? 0),
      motifSejour: v.motifSejour || undefined,
      commentaires: v.commentaires || undefined,
      reductionPourcentage: Number(v.reductionPourcentage ?? 0),
      chambres: [
        {
          chambreId: this.createTargetRoom.chambreId!,
          dateDebut: format(this.createDateDebut, 'yyyy-MM-dd'),
          dateFin: format(this.createDateFin, 'yyyy-MM-dd'),
          prixNuit,
        },
      ],
    };
    this.saving = true;
    this.cdr.markForCheck();
    this.reservationsService
      .create(dto)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.toastSuccess('hebergement.messages.createSuccess');
          this.closeCreateModal();
          this.loadReservations();
        },
        error: (err: HttpErrorResponse) =>
          this.toastError(this.mapBackendError(err)),
      });
  }

  /** Prix estimé (palier 1) — `TypeChambre.prixBase` ; le backend recalculera. */
  estimatedNightPrice(chambre: Chambre): number {
    const t = this.types.find((x) => x.typeId === chambre.typeId);
    return t?.prixBase ?? 0;
  }

  // ── Quick-create client (Phase 2) ──────────────────────────────────────

  /**
   * Tour 49 — Le panel est utilisé depuis la modale création **et** la
   * modale édition. Le contexte est mémorisé pour que `submitQuickCreate*`
   * patche le bon formulaire après la création.
   */
  openQuickCreateClient(context: 'create' | 'edit' = 'create'): void {
    this.clientQuickCreateForm.reset({
      prenom: '',
      nom: '',
      telephone: '',
      email: '',
      cni: '',
    });
    this.quickCreateContext = context;
    this.quickCreateClientVisible = true;
    this.cdr.markForCheck();
  }

  cancelQuickCreateClient(): void {
    this.quickCreateClientVisible = false;
    this.quickCreateContext = null;
    this.cdr.markForCheck();
  }

  submitQuickCreateClient(): void {
    if (this.clientQuickCreateForm.invalid) {
      this.clientQuickCreateForm.markAllAsTouched();
      return;
    }
    const v = this.clientQuickCreateForm.value;
    // Le backend accepte un body minimal {prenom, nom, telephone?, email?,
    // cni?, adresse?}. Le modèle TypeScript local mappe `cni` →
    // `numeroIdentification` (déjà la convention DTO).
    const dto: ClientCreate = {
      prenom: String(v.prenom ?? '').trim(),
      nom: String(v.nom ?? '').trim(),
      telephone: v.telephone ? String(v.telephone).trim() : undefined,
      email: v.email ? String(v.email).trim() : undefined,
      numeroIdentification: v.cni ? String(v.cni).trim() : undefined,
    };
    this.saving = true;
    this.cdr.markForCheck();
    this.clientsService
      .create(dto)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (created) => {
          if (created.clientId != null) {
            this.clients = [created, ...this.clients];
            this.reindexClients();
            // Tour 49 — patch sur le formulaire correspondant au contexte
            // d'ouverture du panel (création vs édition).
            const targetForm =
              this.quickCreateContext === 'edit' ? this.editForm : this.createForm;
            targetForm.patchValue({ clientPrincipalId: created.clientId });
          }
          this.quickCreateClientVisible = false;
          this.quickCreateContext = null;
          this.toastSuccess('hebergement.calendar.client.quickCreate.success');
        },
        error: (err: HttpErrorResponse) =>
          this.toastError(this.mapBackendError(err)),
      });
  }

  // ── Tour 45 F4 — Quick-create société dans la modale création ─────────

  openQuickCreateSociete(context: 'create' | 'edit' = 'create'): void {
    this.societeQuickCreateForm.reset({
      nom: '',
      telephone: '',
      nif: '',
      adresse: '',
    });
    this.quickCreateContext = context;
    this.quickCreateSocieteVisible = true;
    this.cdr.markForCheck();
  }

  cancelQuickCreateSociete(): void {
    this.quickCreateSocieteVisible = false;
    this.quickCreateContext = null;
    this.cdr.markForCheck();
  }

  submitQuickCreateSociete(): void {
    if (this.societeQuickCreateForm.invalid) {
      this.societeQuickCreateForm.markAllAsTouched();
      return;
    }
    const v = this.societeQuickCreateForm.value;
    // Le backend Phase A Tour 45 accepte `{nom, telephone?, nif?, adresse?}`.
    // Le modèle Societe local utilise `societeNom` + `siret` — on aligne les
    // deux conventions en envoyant les deux champs ; le backend ignore les
    // surplus.
    const nom = String(v.nom ?? '').trim();
    const nif = v.nif ? String(v.nif).trim() : undefined;
    const dto: SocieteCreate = {
      societeNom: nom,
      siret: nif,
      telephone: v.telephone ? String(v.telephone).trim() : undefined,
      adresse: v.adresse ? String(v.adresse).trim() : undefined,
    };
    // Couche additionnelle pour le contrat backend Phase A : champs `nom` +
    // `nif` envoyés en parallèle (sans forcer le modèle TS à les déclarer).
    const payload = { ...dto, nom, nif } as SocieteCreate & {
      nom: string;
      nif?: string;
    };
    this.saving = true;
    this.cdr.markForCheck();
    this.clientsService
      .createSociete(payload)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (created) => {
          if (created.societeId != null) {
            this.societes = [created, ...this.societes];
            this.reindexSocietes();
            // Tour 49 — patch sur la modale d'origine (create ou edit).
            const targetForm =
              this.quickCreateContext === 'edit' ? this.editForm : this.createForm;
            targetForm.patchValue({ societeId: created.societeId });
          }
          this.quickCreateSocieteVisible = false;
          this.quickCreateContext = null;
          this.toastSuccess('hebergement.calendar.societe.quickCreate.success');
        },
        error: (err: HttpErrorResponse) =>
          this.toastError(this.mapBackendError(err)),
      });
  }

  // ── Tarification réelle (Phase 2) ──────────────────────────────────────

  private subscribeTarifTrigger(): void {
    this.tarifTrigger$
      .pipe(
        takeUntil(this.destroy$),
        debounceTime(300),
        distinctUntilChanged(
          (a, b) =>
            a.typeChambreId === b.typeChambreId &&
            a.dateDebut === b.dateDebut &&
            a.dateFin === b.dateFin,
        ),
        switchMap((req) => {
          this.tarif = { ...this.tarif, loading: true };
          this.cdr.markForCheck();
          return this.tarifChambreService
            .getCalcul(req.typeChambreId, req.dateDebut, req.dateFin)
            .pipe(
              catchError((err: HttpErrorResponse) => {
                this.tarif = { loading: false, data: null, detailVisible: false };
                this.cdr.markForCheck();
                this.toastError(this.mapBackendError(err));
                return EMPTY;
              }),
            );
        }),
      )
      .subscribe((data) => {
        this.tarif = { loading: false, data, detailVisible: false };
        // Synchronise le champ caché `montantTotal` du formulaire — sert au
        // calcul réduction et au submit (le backend recalcule de toute façon).
        this.createForm?.patchValue({ montantTotal: data.montantTtc });
        this.editForm?.patchValue({ montantTotal: data.montantTtc });
        this.cdr.markForCheck();
      });
  }

  private requestTarif(chambre: Chambre, dateDebut: Date, dateFin: Date): void {
    if (chambre.typeId == null) return;
    if (!isBefore(dateDebut, dateFin)) return;
    this.tarifTrigger$.next({
      typeChambreId: chambre.typeId,
      dateDebut: format(dateDebut, 'yyyy-MM-dd'),
      dateFin: format(dateFin, 'yyyy-MM-dd'),
    });
  }

  // ── Modale Consultation ────────────────────────────────────────────────

  openConsultModal(reservation: Reservation): void {
    this.consultReservation = reservation;
    if (reservation.reservationId != null) {
      this.reservationsService
        .findById(reservation.reservationId)
        .pipe(
          takeUntil(this.destroy$),
          catchError(() => of(null)),
        )
        .subscribe((full) => {
          if (full) {
            this.consultReservation = full;
            this.cdr.markForCheck();
          }
        });
    }
    this.cdr.markForCheck();
    queueMicrotask(() => this.showModal('consult'));
  }

  closeConsultModal(): void {
    this.hideModal('consult');
    this.consultReservation = null;
  }

  consultGoToFullDetail(): void {
    const id = this.consultReservation?.reservationId;
    if (id == null) return;
    this.closeConsultModal();
    this.router.navigate(['/hebergement/reservations', id]);
  }

  // ── Modale Modification ────────────────────────────────────────────────

  openEditModal(reservation: Reservation): void {
    this.editingReservation = reservation;
    // Tour 49 — Ferme tout panel quick-create resté ouvert d'une session
    // précédente (création → édition sans fermer).
    this.quickCreateClientVisible = false;
    this.quickCreateSocieteVisible = false;
    // Pré-remplit `nouvelleChambreId` avec la chambre actuelle si une seule.
    const currentRoomId =
      reservation.chambres?.[0]?.chambreId ?? null;
    this.editForm.reset({
      clientPrincipalId: reservation.clientPrincipalId ?? null,
      societeId: reservation.societeId ?? null,
      dateArrivee: reservation.dateArrivee ?? '',
      dateDepart: reservation.dateDepart ?? '',
      reductionPourcentage: reservation.reductionPourcentage ?? 0,
      motifSejour: reservation.motifSejour ?? '',
      commentaires: reservation.commentaires ?? '',
      nouvelleChambreId: currentRoomId,
      raisonChangementChambre: '',
      montantTotal: reservation.montantTotal ?? 0,
    });
    this.tarif = { loading: false, data: null, detailVisible: false };
    // Recalcule le tarif courant pour affichage.
    if (
      reservation.dateArrivee &&
      reservation.dateDepart &&
      currentRoomId != null
    ) {
      const room = this.rooms.find((c) => c.chambreId === currentRoomId);
      if (room) {
        this.requestTarif(
          room,
          parseISO(reservation.dateArrivee),
          parseISO(reservation.dateDepart),
        );
      }
    }
    this.cdr.markForCheck();
    queueMicrotask(() => this.showModal('edit'));
  }

  closeEditModal(): void {
    this.hideModal('edit');
    this.editingReservation = null;
    this.tarif = { loading: false, data: null, detailVisible: false };
  }

  /** Recalcul tarif sur changement de dates en mode édition. */
  onEditDateChange(): void {
    if (!this.editingReservation) return;
    const v = this.editForm.value;
    if (!v.dateArrivee || !v.dateDepart) return;
    const roomId =
      v.nouvelleChambreId ?? this.editingReservation.chambres?.[0]?.chambreId;
    const room = this.rooms.find((c) => c.chambreId === roomId);
    if (room) {
      try {
        this.requestTarif(room, parseISO(v.dateArrivee), parseISO(v.dateDepart));
      } catch {
        // ignore
      }
    }
  }

  submitEdit(): void {
    if (this.editForm.invalid || !this.editingReservation?.reservationId) {
      this.editForm.markAllAsTouched();
      return;
    }
    const v = this.editForm.value;
    // Tour 49 — `clientPrincipalId` et `societeId` n'apparaissent dans le
    // payload que s'ils ont effectivement changé par rapport à la réservation
    // courante (évite un PATCH inutile et limite le risque de réveiller une
    // validation backend en cas d'erreur transitoire de chargement).
    const currentClientId = this.editingReservation.clientPrincipalId;
    const currentSocieteId = this.editingReservation.societeId ?? null;
    const nextClientId = v.clientPrincipalId != null ? Number(v.clientPrincipalId) : null;
    const nextSocieteId = v.societeId != null ? Number(v.societeId) : null;
    const dto: ModifierReservationRequest = {
      dateArrivee: v.dateArrivee || undefined,
      dateDepart: v.dateDepart || undefined,
      reductionPourcentage:
        v.reductionPourcentage != null ? Number(v.reductionPourcentage) : undefined,
      motifSejour: v.motifSejour || undefined,
      commentaires: v.commentaires || undefined,
      clientPrincipalId:
        nextClientId != null && nextClientId !== currentClientId
          ? nextClientId
          : undefined,
      societeId:
        nextSocieteId !== currentSocieteId
          ? (nextSocieteId ?? undefined)
          : undefined,
    };
    this.saving = true;
    this.cdr.markForCheck();
    this.reservationsService
      .update(this.editingReservation.reservationId, dto)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.toastSuccess('hebergement.messages.updateSuccess');
          this.closeEditModal();
          this.loadReservations();
        },
        error: (err: HttpErrorResponse) =>
          this.toastError(this.mapBackendError(err)),
      });
  }

  /**
   * Changement de chambre en mode édition — bouton séparé du submit principal.
   * PATCH `/api/hebergement/reservations/{id}/chambre`.
   */
  submitChangeRoom(): void {
    const r = this.editingReservation;
    if (!r?.reservationId) return;
    const v = this.editForm.value;
    const nouvelleChambreId = v.nouvelleChambreId
      ? Number(v.nouvelleChambreId)
      : null;
    if (!nouvelleChambreId) {
      this.editForm.get('nouvelleChambreId')?.markAsTouched();
      return;
    }
    const ancienneChambreId = r.chambres?.[0]?.chambreId;
    if (ancienneChambreId === nouvelleChambreId) {
      this.toastError('error.reservation.changerChambre.identique');
      return;
    }
    this.translateAlertConfirm(
      'hebergement.calendar.changeRoom.confirm.message',
      'hebergement.calendar.changeRoom.confirm.title',
    ).then((ok) => {
      if (!ok) return;
      this.saving = true;
      this.cdr.markForCheck();
      this.reservationsService
        .changerChambre(r.reservationId!, {
          ancienneChambreId,
          nouvelleChambreId,
          raison: v.raisonChangementChambre || undefined,
        })
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.saving = false;
            this.cdr.markForCheck();
          }),
        )
        .subscribe({
          next: (updated) => {
            this.toastSuccess('hebergement.calendar.changeRoom.success');
            this.editingReservation = updated;
            this.loadReservations();
          },
          error: (err: HttpErrorResponse) =>
            this.toastError(this.mapBackendError(err)),
        });
    });
  }

  // ── Modale Paiements (Phase 2) ─────────────────────────────────────────

  openPaymentsModal(reservation: Reservation): void {
    if (reservation.reservationId == null) return;
    this.paymentsModal = {
      reservationId: reservation.reservationId,
      reservation,
      reservationLabel:
        reservation.numeroReservation ?? '#' + reservation.reservationId,
      loading: true,
      data: null,
      lignes: [],
      error: false,
      folio: null,
      folioLoading: true,
      folioError: false,
      transferPanelVisible: false,
      checkoutExpressPanelVisible: false,
      advancedActionsVisible: false,
    };
    // Tour 46 — Pré-remplit le formulaire d'encaissement global avec un
    // montant initial à 0 (sera ajusté quand le recap arrive avec resteGlobal).
    this.payerGlobalForm.reset({
      montant: 0,
      modePaiement: 'ESPECES',
      motif: '',
      description: '',
    });
    this.cdr.markForCheck();
    queueMicrotask(() => this.showModal('payments'));
    this.loadPaymentsRecap(reservation.reservationId);
  }

  /**
   * Tour 46 — Recharge le récap paiements + lignes facture + folio de la
   * modale en parallèle (forkJoin). Appelé à l'ouverture et après chaque
   * payer/transférer pour rafraîchir les 3 sections.
   *
   * Le folio est chargé pour la période complète de la réservation
   * (dateArrivee → dateDepart). Une erreur folio est non-bloquante : on
   * conserve les autres données et affiche un état "folioError" dédié.
   */
  private loadPaymentsRecap(reservationId: number): void {
    this.paymentsModal = {
      ...this.paymentsModal,
      loading: true,
    };
    this.cdr.markForCheck();
    // Le « folio » de la modale Tour 46 = liste des factures liées à la
    // réservation (déjà présente dans `recap.factures`). Pas d'appel séparé
    // au folio compte client (gardé côté backend pour usage futur).
    forkJoin({
      recap: this.paiementsRecapService.getRecapForReservation(reservationId),
      lignes:
        this.paiementsRecapService.getLignesByReservation(reservationId),
    })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.paymentsModal = {
            ...this.paymentsModal,
            loading: false,
          };
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: ({ recap, lignes }) => {
          this.paymentsModal = {
            ...this.paymentsModal,
            data: recap,
            lignes,
            error: false,
          };
          // Tour 46 — pré-remplit le formulaire global avec le reste dû.
          if (recap && recap.resteGlobal > 0) {
            this.payerGlobalForm.patchValue({ montant: recap.resteGlobal });
          }
          this.cdr.markForCheck();
        },
        error: (err: HttpErrorResponse) => {
          this.paymentsModal = {
            ...this.paymentsModal,
            error: true,
            lignes: [],
          };
          this.toastError(this.mapBackendError(err));
        },
      });
  }

  /**
   * Tour 46 — Construit l'observable de chargement folio pour la réservation
   * courante. Retourne un Observable<null> si la réservation n'a pas de
   * `clientPrincipalId` ou de dates valides (cas dégénéré — pas d'appel HTTP).
   */
  private buildFolioRequest(
    reservation: Reservation | null,
  ): Observable<FolioDto | null> {
    if (
      !reservation ||
      reservation.clientPrincipalId == null ||
      !reservation.dateArrivee ||
      !reservation.dateDepart
    ) {
      return of<FolioDto | null>(null);
    }
    return this.folioService.getFolioForReservation(
      reservation.clientPrincipalId,
      reservation.dateArrivee,
      reservation.dateDepart,
    );
  }

  /**
   * Lignes-facture chargées pour la modale paiements (Tour 45 fix dette).
   *
   * Source : `GET /api/finance/factures/lignes-by-reservation/{id}` — les
   * `ligneFactureId` retournés sont les VRAIS identifiants côté backend, donc
   * `POST /paiement-lignes` reçoit des `ligneFactureId` valides.
   *
   * Le paramètre `recap` est conservé pour préserver la signature consommée
   * par le template (binding `lignesFromRecap(paymentsModal.data)`) mais n'est
   * plus utilisé : les lignes proviennent désormais de `paymentsModal.lignes`.
   */
  lignesFromRecap(
    _recap?: RecapPaiementsReservationDto | null,
  ): PaymentLigneRow[] {
    return this.paymentsModal.lignes.map((l) => ({
      ligneFactureId: l.ligneFactureId,
      factureId: l.factureId,
      factureNumero: l.factureNumero,
      description: l.description,
      date: l.dateLigne ?? '',
      sousTotal: l.montantTtc,
      paye: l.montantPaye,
      reste: l.reste,
    }));
  }

  closePaymentsModal(): void {
    this.hideModal('payments');
    this.paymentsModal = {
      reservationId: null,
      reservation: null,
      reservationLabel: '',
      loading: false,
      data: null,
      lignes: [],
      error: false,
      folio: null,
      folioLoading: false,
      folioError: false,
      transferPanelVisible: false,
      checkoutExpressPanelVisible: false,
      advancedActionsVisible: false,
    };
    this.paymentSelectedLigneIds = new Set<number>();
  }

  // ── Tour 46 — Section "Détails réservation" (helpers template) ────────

  /**
   * Tour 46 — Libellé d'une chambre (numero) via index `roomsById`. Utilisé
   * dans la section "Détails de la réservation" de la modale paiements.
   * Fallback : `numeroChambre` exposé par le DTO ReservationChambre, sinon
   * `#chambreId`.
   */
  roomLabel(chambreId: number, fallbackNumero?: string): string {
    const chambre = this.roomsById.get(chambreId);
    if (chambre?.numeroChambre) return chambre.numeroChambre;
    if (fallbackNumero) return fallbackNumero;
    return '#' + chambreId;
  }

  /**
   * Tour 46 — Société liée à la réservation courante (modale paiements).
   * Retourne null si la réservation n'a pas de société associée.
   */
  paymentsSocieteName(reservation: Reservation | null): string | null {
    if (!reservation || reservation.societeId == null) return null;
    const societe = this.societesById.get(reservation.societeId);
    if (societe?.societeNom) return societe.societeNom;
    return reservation.nomSociete ?? null;
  }

  /**
   * Tour 46 — Classe badge Bootstrap pour le statut de la réservation
   * (section "Détails"). Délègue au lookup centralisé Tour 40ter.
   */
  reservationBadgeClass(statut: StatutReservation | undefined): string {
    if (!statut) return 'text-bg-secondary';
    return STATUT_RESERVATION_BADGE_MAP[statut] ?? 'text-bg-secondary';
  }

  // ── Tour 46 — Section "Paiement global" (formulaire principal) ────────

  /** Reste à payer global pour la réservation. */
  get globalResteAPayer(): number {
    return this.paymentsModal.data?.resteGlobal ?? 0;
  }

  /** Montant courant saisi dans le formulaire global. */
  get globalMontantSaisi(): number {
    const v = this.payerGlobalForm?.value?.montant;
    return Number(v) || 0;
  }

  // ── Tour 56 — Sélection de lignes pour paiement individuel/partiel ────
  // (Restauration : checkboxes par ligne supprimées au Tour 46.)

  /**
   * Set des `ligneFactureId` sélectionnés dans la section "Lignes facture"
   * de la modale paiements. Re-initialisé à chaque chargement du recap.
   */
  paymentSelectedLigneIds = new Set<number>();

  /** Toggle d'une ligne dans la sélection paiement individuel. */
  togglePaymentLigne(ligneId: number): void {
    const next = new Set(this.paymentSelectedLigneIds);
    if (next.has(ligneId)) next.delete(ligneId);
    else next.add(ligneId);
    this.paymentSelectedLigneIds = next;
    this.cdr.markForCheck();
  }

  isPaymentLigneSelected(ligneId: number): boolean {
    return this.paymentSelectedLigneIds.has(ligneId);
  }

  /** Toggle "tout sélectionner" — ne sélectionne que les lignes avec reste > 0. */
  togglePaymentAllLignes(): void {
    const lignes = this.paymentsModal.lignes ?? [];
    const payables = lignes.filter((l) => Number(l.reste ?? 0) > 0);
    if (this.paymentLignesAllSelected) {
      this.paymentSelectedLigneIds = new Set<number>();
    } else {
      const next = new Set<number>();
      for (const l of payables) next.add(l.ligneFactureId);
      this.paymentSelectedLigneIds = next;
    }
    this.cdr.markForCheck();
  }

  get paymentLignesAllSelected(): boolean {
    const payables = (this.paymentsModal.lignes ?? []).filter(
      (l) => Number(l.reste ?? 0) > 0,
    );
    if (payables.length === 0) return false;
    return payables.every((l) =>
      this.paymentSelectedLigneIds.has(l.ligneFactureId),
    );
  }

  get paymentLignesIndeterminate(): boolean {
    const payables = (this.paymentsModal.lignes ?? []).filter(
      (l) => Number(l.reste ?? 0) > 0,
    );
    if (payables.length === 0) return false;
    const count = payables.filter((l) =>
      this.paymentSelectedLigneIds.has(l.ligneFactureId),
    ).length;
    return count > 0 && count < payables.length;
  }

  /** Somme des restes des lignes cochées (= montant à payer pour la sélection). */
  get paymentSelectedSum(): number {
    return (this.paymentsModal.lignes ?? []).reduce((sum, l) => {
      if (!this.paymentSelectedLigneIds.has(l.ligneFactureId)) return sum;
      const reste = Number(l.reste ?? 0);
      return sum + (Number.isFinite(reste) ? reste : 0);
    }, 0);
  }

  get paymentSelectedCount(): number {
    return this.paymentSelectedLigneIds.size;
  }

  /**
   * Pré-remplit le formulaire de paiement global avec le montant total des
   * lignes sélectionnées (raccourci utilisateur "payer juste ce que j'ai
   * coché"). Le bouton "Payer" du form global reste utilisé tel quel.
   */
  applySelectionToGlobalForm(): void {
    const sum = this.paymentSelectedSum;
    if (sum <= 0) return;
    this.payerGlobalForm.patchValue({ montant: sum });
    this.payerGlobalForm.get('montant')?.updateValueAndValidity();
    this.cdr.markForCheck();
  }

  /**
   * Encaisse uniquement les lignes sélectionnées via `payerLignes` (ciblage
   * back par `lignesIds`). Reprend `modePaiement` et `motif` du formulaire
   * global pour ne pas dupliquer le formulaire.
   */
  submitPayerSelection(): void {
    const lignesIds = Array.from(this.paymentSelectedLigneIds);
    if (lignesIds.length === 0) return;
    const reservation = this.paymentsModal.reservation;
    if (!reservation?.reservationId || reservation.clientPrincipalId == null) {
      this.toastError('hebergement.messages.saveError');
      return;
    }
    const v = this.payerGlobalForm.value;
    const montant = this.paymentSelectedSum;
    const dto: PaiementLignesRequest = {
      lignesIds,
      montant,
      modePaiement: v.modePaiement as ModePaiement,
      motif: v.motif || undefined,
      description: v.description || undefined,
      idClient: reservation.clientPrincipalId,
      idCompteClient: 0,
    };
    this.saving = true;
    this.cdr.markForCheck();
    this.paiementsService
      .payerLignes(dto)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.toastSuccess('hebergement.calendar.payments.selection.success');
          this.paymentSelectedLigneIds = new Set<number>();
          this.payerGlobalForm.reset({
            montant: 0,
            modePaiement: 'ESPECES',
            motif: '',
            description: '',
          });
          if (this.paymentsModal.reservationId != null) {
            this.loadPaymentsRecap(this.paymentsModal.reservationId);
          }
          this.loadReservations();
        },
        error: (err: HttpErrorResponse) =>
          this.toastError(this.mapBackendError(err)),
      });
  }

  /**
   * Tour 46 — Indicateur visuel du type de paiement (partiel / complet /
   * excédent) en fonction du montant saisi vs. reste à payer.
   */
  get globalPayerStatus(): 'partial' | 'full' | 'excess' | 'none' {
    const reste = this.globalResteAPayer;
    const saisi = this.globalMontantSaisi;
    if (saisi <= 0) return 'none';
    if (Math.abs(saisi - reste) < 0.005) return 'full';
    if (saisi > reste) return 'excess';
    return 'partial';
  }

  /** Montant qui restera dû après application du paiement saisi (partiel). */
  get globalPartialReste(): number {
    return Math.max(0, this.globalResteAPayer - this.globalMontantSaisi);
  }

  /** Excédent qui sera crédité au compte client (paiement supérieur au reste). */
  get globalExcedent(): number {
    return Math.max(0, this.globalMontantSaisi - this.globalResteAPayer);
  }

  /**
   * Tour 46 — Soumission du formulaire de paiement global. Confirmation
   * SweetAlert2 avec récapitulatif puis POST `/paiement-global`. Le backend
   * défalque automatiquement sur les lignes et crédite l'excédent éventuel.
   */
  submitPayerGlobal(): void {
    if (this.payerGlobalForm.invalid) {
      this.payerGlobalForm.markAllAsTouched();
      return;
    }
    const reservation = this.paymentsModal.reservation;
    if (!reservation?.reservationId || reservation.clientPrincipalId == null) {
      this.toastError('hebergement.messages.saveError');
      return;
    }
    const v = this.payerGlobalForm.value;
    const montant = Number(v.montant);
    const dueReste = this.globalResteAPayer;
    const dto: PaiementGlobalRequest = {
      reservationId: reservation.reservationId,
      montant,
      modePaiement: v.modePaiement as ModePaiement,
      motif: v.motif || undefined,
      description: v.description || undefined,
      idClient: reservation.clientPrincipalId,
      idCompteClient: 0,
    };
    const confirmText = this.buildPayerGlobalConfirmText(montant, dueReste);
    Swal.fire({
      icon: 'question',
      title: this.i18n.translate('hebergement.calendar.payments.global.confirm'),
      html: confirmText,
      showCancelButton: true,
      confirmButtonText: this.i18n.translate(
        'hebergement.calendar.payments.global.action',
      ),
      cancelButtonText: this.i18n.translate('hebergement.actions.cancel'),
    }).then((res) => {
      if (!res.isConfirmed) return;
      this.doPayerGlobal(dto, montant, dueReste);
    });
  }

  private buildPayerGlobalConfirmText(
    montant: number,
    dueReste: number,
  ): string {
    const mru = ' MRU';
    const amount =
      '<strong>' + montant.toFixed(2) + mru + '</strong>';
    if (Math.abs(montant - dueReste) < 0.005) {
      return (
        amount + ' — ' + this.i18n.translate('hebergement.calendar.payments.global.full')
      );
    }
    if (montant > dueReste) {
      const exc = (montant - dueReste).toFixed(2);
      return (
        amount +
        ' — ' +
        this.i18n
          .translate('hebergement.calendar.payments.global.excess')
          .replace('{amount}', exc) +
        ' ' +
        mru
      );
    }
    const remaining = (dueReste - montant).toFixed(2);
    return (
      amount +
      ' — ' +
      this.i18n
        .translate('hebergement.calendar.payments.global.partial')
        .replace('{amount}', remaining) +
      ' ' +
      mru
    );
  }

  private doPayerGlobal(
    dto: PaiementGlobalRequest,
    montant: number,
    dueReste: number,
  ): void {
    this.saving = true;
    this.cdr.markForCheck();
    this.paiementsService
      .payerGlobal(dto)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (result) => {
          if (result.excedent > 0) {
            this.toastSuccessRaw(
              this.i18n
                .translate('hebergement.calendar.payments.global.successExcess')
                .replace('{amount}', result.excedent.toFixed(2)),
            );
          } else if (Math.abs(montant - dueReste) < 0.005) {
            this.toastSuccess('hebergement.calendar.payments.global.successFull');
          } else {
            this.toastSuccess('hebergement.calendar.payments.global.success');
          }
          // Reset du formulaire + close transfert/checkout panels + refresh.
          this.payerGlobalForm.reset({
            montant: 0,
            modePaiement: 'ESPECES',
            motif: '',
            description: '',
          });
          this.paymentsModal = {
            ...this.paymentsModal,
            transferPanelVisible: false,
            checkoutExpressPanelVisible: false,
          };
          if (this.paymentsModal.reservationId != null) {
            this.loadPaymentsRecap(this.paymentsModal.reservationId);
          }
          this.loadReservations();
        },
        error: (err: HttpErrorResponse) =>
          this.toastError(this.mapBackendError(err)),
      });
  }

  // ── Tour 46 — Section "Actions avancées" (transfert / check-out express) ──

  /** Toggle de la section "Actions avancées" (collapse Bootstrap). */
  toggleAdvancedActions(): void {
    this.paymentsModal = {
      ...this.paymentsModal,
      advancedActionsVisible: !this.paymentsModal.advancedActionsVisible,
    };
    this.cdr.markForCheck();
  }

  openTransferPanel(): void {
    this.transferForm.reset({
      factureCibleId: null,
      creerNouvelleFacture: false,
      lignesIds: [],
    });
    this.paymentsModal = {
      ...this.paymentsModal,
      transferPanelVisible: true,
      checkoutExpressPanelVisible: false,
    };
    this.cdr.markForCheck();
  }

  closeTransferPanel(): void {
    this.paymentsModal = { ...this.paymentsModal, transferPanelVisible: false };
    this.cdr.markForCheck();
  }

  /** Liste des factures candidates (toutes les factures de la réservation). */
  get factureCiblesOptions(): { id: number; numero: string }[] {
    const data = this.paymentsModal.data;
    if (!data) return [];
    return data.factures.map((f) => ({ id: f.factureId, numero: f.numero }));
  }

  /**
   * Tour 46 — Toggle d'une ligne dans le formulaire de transfert. Garde la
   * sélection LOCALE au panel transfert (pas un état modal-wide comme Tour 45).
   */
  toggleTransferLigne(ligneId: number, checked: boolean): void {
    const current = (this.transferForm.value.lignesIds as number[]) ?? [];
    const next = checked
      ? [...current, ligneId]
      : current.filter((id) => id !== ligneId);
    this.transferForm.patchValue({ lignesIds: next });
    this.cdr.markForCheck();
  }

  isTransferLigneSelected(ligneId: number): boolean {
    const current = (this.transferForm?.value?.lignesIds as number[]) ?? [];
    return current.includes(ligneId);
  }

  submitTransfertLignes(): void {
    const v = this.transferForm.value;
    const lignesIds = (v.lignesIds as number[]) ?? [];
    if (lignesIds.length === 0) {
      this.transferForm.get('lignesIds')?.markAsTouched();
      return;
    }
    let factureCibleId: number | null = v.factureCibleId
      ? Number(v.factureCibleId)
      : null;
    if (v.creerNouvelleFacture) factureCibleId = -1;
    if (factureCibleId == null) {
      this.transferForm.markAllAsTouched();
      return;
    }
    const dto: TransfererLignesRequest = {
      lignesIds,
      factureCibleId,
    };
    this.saving = true;
    this.cdr.markForCheck();
    this.paiementsService
      .transfererLignes(dto)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.toastSuccess('hebergement.calendar.payments.transferer.success');
          this.closeTransferPanel();
          if (this.paymentsModal.reservationId != null) {
            this.loadPaymentsRecap(this.paymentsModal.reservationId);
          }
        },
        error: (err: HttpErrorResponse) =>
          this.toastError(this.mapBackendError(err)),
      });
  }

  // ── Tour 45 F5 — Check-out express ─────────────────────────────────────

  openCheckoutExpressPanel(): void {
    const r = this.paymentsModal.reservation;
    if (!r || r.reservationId == null) return;
    // Cas 1 : société déjà associée → confirmation directe.
    if (r.societeId != null) {
      const societe = this.societesById.get(r.societeId);
      const nom = societe?.societeNom ?? '#' + r.societeId;
      this.confirmAndDoCheckoutExpress(r, r.societeId, nom);
      return;
    }
    // Cas 2 : pas de société → afficher le panel de sélection.
    this.checkoutExpressForm.reset({ societeId: null });
    this.paymentsModal = {
      ...this.paymentsModal,
      checkoutExpressPanelVisible: true,
      transferPanelVisible: false,
    };
    this.cdr.markForCheck();
  }

  closeCheckoutExpressPanel(): void {
    this.paymentsModal = {
      ...this.paymentsModal,
      checkoutExpressPanelVisible: false,
    };
    this.cdr.markForCheck();
  }

  submitCheckoutExpress(): void {
    const r = this.paymentsModal.reservation;
    if (!r || r.reservationId == null) return;
    if (this.checkoutExpressForm.invalid) {
      this.checkoutExpressForm.markAllAsTouched();
      return;
    }
    const v = this.checkoutExpressForm.value;
    const societeId = Number(v.societeId);
    const societe = this.societesById.get(societeId);
    const nom = societe?.societeNom ?? '#' + societeId;
    this.confirmAndDoCheckoutExpress(r, societeId, nom);
  }

  private confirmAndDoCheckoutExpress(
    r: Reservation,
    societeId: number,
    societeNom: string,
  ): void {
    Swal.fire({
      icon: 'question',
      title: this.i18n.translate(
        'hebergement.calendar.payments.checkoutExpress.title',
      ),
      text:
        this.i18n.translate(
          'hebergement.calendar.payments.checkoutExpress.confirm',
        ) +
        ' (' +
        societeNom +
        ')',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('hebergement.actions.save'),
      cancelButtonText: this.i18n.translate('hebergement.actions.cancel'),
    }).then((res) => {
      if (!res.isConfirmed) return;
      this.saving = true;
      this.cdr.markForCheck();
      this.reservationsService
        .checkOutExpress(r.reservationId!, {
          societeId,
          clientId: r.clientPrincipalId,
        })
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.saving = false;
            this.cdr.markForCheck();
          }),
        )
        .subscribe({
          next: () => {
            this.toastSuccess('hebergement.calendar.payments.checkoutExpress.success');
            this.closeCheckoutExpressPanel();
            this.closePaymentsModal();
            this.loadReservations();
          },
          error: (err: HttpErrorResponse) =>
            this.toastError(this.mapBackendError(err)),
        });
    });
  }

  /** Navigation vers le détail facture (module finance). */
  goToFactureDetail(factureId: number): void {
    if (this.paymentsModal.reservationId == null) return;
    const resId = this.paymentsModal.reservationId;
    this.closePaymentsModal();
    this.router.navigate(['/finance/factures', factureId], {
      queryParams: { reservationId: resId },
    });
  }

  // ── Tour 45 F6 — Modale Modification de nuitées ────────────────────────

  /** Bouton "Modifier nuitées" dans la modale consultation. */
  consultModifyNuitees(): void {
    const r = this.consultReservation;
    if (!r) return;
    this.closeConsultModal();
    this.openModifyNuiteesModal(r);
  }

  openModifyNuiteesModal(reservation: Reservation): void {
    if (reservation.reservationId == null) return;
    this.modifyNuiteesModal = {
      reservationId: reservation.reservationId,
      reservationLabel:
        reservation.numeroReservation ?? '#' + reservation.reservationId,
      loading: true,
      saving: false,
      initial: [],
      edited: new Map<number, number>(),
      error: false,
    };
    this.cdr.markForCheck();
    queueMicrotask(() => this.showModal('modifyNuitees'));
    this.nuiteesModifService
      .getProvisoires(reservation.reservationId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.modifyNuiteesModal = {
            ...this.modifyNuiteesModal,
            loading: false,
          };
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (rows) => {
          const edited = new Map<number, number>();
          for (const r of rows) edited.set(r.nuiteeId, r.montantLigneFacture);
          this.modifyNuiteesModal = {
            ...this.modifyNuiteesModal,
            initial: rows,
            edited,
            error: false,
          };
          this.cdr.markForCheck();
        },
        error: (err: HttpErrorResponse) => {
          this.modifyNuiteesModal = { ...this.modifyNuiteesModal, error: true };
          this.toastError(this.mapBackendError(err));
        },
      });
  }

  closeModifyNuiteesModal(): void {
    this.hideModal('modifyNuitees');
    this.modifyNuiteesModal = {
      reservationId: null,
      reservationLabel: '',
      loading: false,
      saving: false,
      initial: [],
      edited: new Map<number, number>(),
      error: false,
    };
    this.cdr.markForCheck();
  }

  /** Valeur courante affichée dans l'input pour une nuitée. */
  nuiteeMontant(nuiteeId: number): number {
    return this.modifyNuiteesModal.edited.get(nuiteeId) ?? 0;
  }

  /** Mise à jour de la valeur courante (binding manuel — pas de FormArray). */
  onNuiteeMontantChange(nuiteeId: number, value: string | number): void {
    const next = Number(value);
    if (Number.isNaN(next)) return;
    const edited = new Map(this.modifyNuiteesModal.edited);
    edited.set(nuiteeId, next);
    this.modifyNuiteesModal = { ...this.modifyNuiteesModal, edited };
    this.cdr.markForCheck();
  }

  /** Reset d'une nuitée à son montant d'origine. */
  resetNuiteeMontant(row: NuiteeModificationDto): void {
    const edited = new Map(this.modifyNuiteesModal.edited);
    edited.set(row.nuiteeId, row.montantLigneFacture);
    this.modifyNuiteesModal = { ...this.modifyNuiteesModal, edited };
    this.cdr.markForCheck();
  }

  /** True si la ligne est verrouillée (statut C). */
  isNuiteeLocked(row: NuiteeModificationDto): boolean {
    return row.statutLigne === 'C';
  }

  /** Total avant modification. */
  get nuiteesTotalAvant(): number {
    let sum = 0;
    for (const row of this.modifyNuiteesModal.initial) {
      sum += row.montantLigneFacture;
    }
    return sum;
  }

  /** Total après modification (lignes verrouillées conservent leur valeur d'origine). */
  get nuiteesTotalApres(): number {
    let sum = 0;
    for (const row of this.modifyNuiteesModal.initial) {
      const val = this.isNuiteeLocked(row)
        ? row.montantLigneFacture
        : this.modifyNuiteesModal.edited.get(row.nuiteeId) ??
          row.montantLigneFacture;
      sum += val;
    }
    return sum;
  }

  get nuiteesImpact(): number {
    return this.nuiteesTotalApres - this.nuiteesTotalAvant;
  }

  submitModifyNuitees(): void {
    const updates: NuiteeMontantUpdate[] = [];
    for (const row of this.modifyNuiteesModal.initial) {
      if (this.isNuiteeLocked(row)) continue;
      const nouveau = this.modifyNuiteesModal.edited.get(row.nuiteeId);
      if (nouveau == null) continue;
      if (Math.abs(nouveau - row.montantLigneFacture) < 0.005) continue;
      updates.push({
        nuiteeId: row.nuiteeId,
        nouveauMontant: nouveau,
        ligneFactureId: row.ligneFactureId,
        operationCompteId: row.operationCompteId,
      });
    }
    if (updates.length === 0) {
      this.toastInfo('hebergement.calendar.nuitees.modify.noChanges');
      return;
    }
    const impact = this.nuiteesImpact;
    const confirmKey =
      impact === 0
        ? null
        : 'hebergement.calendar.nuitees.modify.confirm';
    const proceed = () => {
      this.modifyNuiteesModal = { ...this.modifyNuiteesModal, saving: true };
      this.cdr.markForCheck();
      this.nuiteesModifService
        .updateMontants(updates)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.modifyNuiteesModal = {
              ...this.modifyNuiteesModal,
              saving: false,
            };
            this.cdr.markForCheck();
          }),
        )
        .subscribe({
          next: () => {
            this.toastSuccess('hebergement.calendar.nuitees.modify.success');
            this.closeModifyNuiteesModal();
            if (this.paymentsModal.reservationId != null) {
              this.loadPaymentsRecap(this.paymentsModal.reservationId);
            }
            this.loadReservations();
          },
          error: (err: HttpErrorResponse) =>
            this.toastError(this.mapBackendError(err)),
        });
    };
    if (confirmKey) {
      this.translateAlertConfirm(confirmKey).then((ok) => {
        if (ok) proceed();
      });
    } else {
      proceed();
    }
  }

  // ── SSE temps réel (Phase 2) ───────────────────────────────────────────

  private subscribeLiveEvents(): void {
    this.calendarLive
      .connect()
      .pipe(takeUntil(this.destroy$))
      .subscribe((event) => this.handleLiveEvent(event));
  }

  private handleLiveEvent(event: CalendarEventDto): void {
    if (event.type === 'DELETED') {
      this.removeReservationFromIndex(event.reservationId);
      this.cdr.markForCheck();
      this.toastInfo('hebergement.calendar.live.syncToast');
      return;
    }
    if (event.type === 'CREATED' || event.type === 'UPDATED') {
      this.reservationsService
        .findById(event.reservationId)
        .pipe(
          takeUntil(this.destroy$),
          catchError(() => of(null)),
        )
        .subscribe((res) => {
          if (!res) return;
          this.upsertReservationInIndex(res);
          this.cdr.markForCheck();
          this.toastInfo('hebergement.calendar.live.syncToast');
        });
    }
  }

  private removeReservationFromIndex(reservationId: number): void {
    this.reservations = this.reservations.filter(
      (r) => r.reservationId !== reservationId,
    );
    for (const [chambreId, list] of this.reservationsByRoom) {
      const filtered = list.filter((r) => r.reservationId !== reservationId);
      if (filtered.length === 0) {
        this.reservationsByRoom.delete(chambreId);
      } else {
        this.reservationsByRoom.set(chambreId, filtered);
      }
    }
  }

  private upsertReservationInIndex(res: Reservation): void {
    if (res.reservationId == null) return;
    this.removeReservationFromIndex(res.reservationId);
    this.reservations = [...this.reservations, res];
    const chambres = res.chambres ?? [];
    for (const rc of chambres) {
      const existing = this.reservationsByRoom.get(rc.chambreId) ?? [];
      existing.push(res);
      existing.sort((a, b) =>
        (a.dateArrivee ?? '').localeCompare(b.dateArrivee ?? ''),
      );
      this.reservationsByRoom.set(rc.chambreId, existing);
    }
  }

  // ── Chargement données ─────────────────────────────────────────────────

  retry(): void {
    this.loadAll();
  }

  /** Recharge types + chambres + clients + sociétés + réservations. */
  loadAll(): void {
    this.state = 'loading';
    this.cdr.markForCheck();
    forkJoin({
      types: this.typesChambreService.findActifs().pipe(catchError(() => of<TypeChambre[]>([]))),
      rooms: this.chambresService.findActives().pipe(catchError(() => of<Chambre[]>([]))),
      clients: this.clientsService
        .page({ page: 0, size: 200, sortBy: 'nom', sortDir: 'asc' })
        .pipe(
          map((p) => p.content ?? []),
          catchError(() => of<Client[]>([])),
        ),
      societes: this.clientsService
        .pageSocietes({ page: 0, size: 200, sortBy: 'societeNom', sortDir: 'asc' })
        .pipe(
          map((p) => p.content ?? []),
          catchError(() => of<Societe[]>([])),
        ),
      reservations: this.fetchReservations(),
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ({ types, rooms, clients, societes, reservations }) => {
          this.types = types;
          this.rooms = rooms;
          this.clients = clients;
          this.societes = societes;
          this.reindexClients();
          this.reindexSocietes();
          this.reindexRooms();
          this.groupRooms();
          this.indexReservations(reservations);
          this.state = 'ready';
          this.cdr.markForCheck();
        },
        error: () => {
          this.state = 'error';
          this.cdr.markForCheck();
        },
      });
  }

  private loadReservations(): void {
    this.fetchReservations()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (reservations) => {
          this.indexReservations(reservations);
          this.cdr.markForCheck();
        },
        error: () => {
          this.toastError('hebergement.calendar.error');
        },
      });
  }

  private fetchReservations(): Observable<Reservation[]> {
    const debut = format(addDays(this.viewStart, -30), 'yyyy-MM-dd');
    const fin = format(addDays(this.viewEnd, 30), 'yyyy-MM-dd');
    return this.reservationsService
      .page(
        { dateArriveeDebut: debut, dateArriveeFin: fin },
        0,
        500,
        'dateArrivee',
        'asc',
      )
      .pipe(
        takeUntil(this.destroy$),
        map((page) => page.content ?? []),
        catchError(() => of([] as Reservation[])),
      );
  }

  // ── Privé : utilitaires ────────────────────────────────────────────────

  private buildForms(): void {
    this.createForm = this.fb.group({
      clientPrincipalId: [null, [Validators.required]],
      societeId: [null],
      nbAdultes: [1, [Validators.required, Validators.min(1)]],
      nbEnfants: [0, [Validators.min(0)]],
      reductionPourcentage: [0, [Validators.min(0), Validators.max(100)]],
      motifSejour: [''],
      commentaires: [''],
      // Champ technique synchronisé avec le résultat du calcul tarif —
      // permet au template d'afficher montants et de garder cohérence form.
      montantTotal: [0],
    });
    this.editForm = this.fb.group({
      // Tour 49 — Permettre le changement de client / société depuis la modale
      // édition. `clientPrincipalId` reste required côté front, même si le
      // backend tolère l'absence du champ pour ne pas casser les payloads
      // partiels (`ModifierReservationRequest.clientPrincipalId?: number`).
      clientPrincipalId: [null, [Validators.required]],
      societeId: [null],
      dateArrivee: ['', [Validators.required]],
      dateDepart: ['', [Validators.required]],
      reductionPourcentage: [0, [Validators.min(0), Validators.max(100)]],
      motifSejour: [''],
      commentaires: [''],
      // Phase 2 — changement de chambre
      nouvelleChambreId: [null],
      raisonChangementChambre: [''],
      montantTotal: [0],
    });
    this.clientQuickCreateForm = this.fb.group({
      prenom: ['', [Validators.required, Validators.maxLength(80)]],
      nom: ['', [Validators.required, Validators.maxLength(80)]],
      telephone: ['', [Validators.maxLength(40)]],
      email: ['', [Validators.email, Validators.maxLength(120)]],
      cni: ['', [Validators.maxLength(40)]],
    });
    // Tour 45 — F4 quick-create société
    this.societeQuickCreateForm = this.fb.group({
      nom: ['', [Validators.required, Validators.maxLength(160)]],
      telephone: ['', [Validators.maxLength(40)]],
      nif: ['', [Validators.maxLength(40)]],
      adresse: ['', [Validators.maxLength(255)]],
    });
    // Tour 46 — Formulaire principal "Encaisser un paiement" (paiement global)
    this.payerGlobalForm = this.fb.group({
      montant: [0, [Validators.required, Validators.min(0.01)]],
      modePaiement: ['ESPECES' as ModePaiement, [Validators.required]],
      motif: ['', [Validators.maxLength(255)]],
      description: ['', [Validators.maxLength(500)]],
    });
    // Tour 46 — Sous-formulaire "Transférer lignes" (actions avancées).
    // La sélection de lignes vit dans le formulaire (lignesIds[]) — plus
    // d'état modal-wide comme Tour 45 (paiement global ne dépend pas d'une
    // sélection).
    this.transferForm = this.fb.group({
      factureCibleId: [null as number | null],
      creerNouvelleFacture: [false],
      lignesIds: [[] as number[]],
    });
    // Tour 45 — F5 sous-formulaire "Check-out express" (sélection société si absente)
    this.checkoutExpressForm = this.fb.group({
      societeId: [null as number | null, [Validators.required]],
    });
  }

  private generateDays(): void {
    this.days = [];
    this.dayIndex.clear();
    const cursor = new Date(
      this.viewStart.getFullYear(),
      this.viewStart.getMonth(),
      this.viewStart.getDate(),
    );
    const end = new Date(
      this.viewEnd.getFullYear(),
      this.viewEnd.getMonth(),
      this.viewEnd.getDate(),
    );
    while (cursor <= end) {
      const copy = new Date(cursor);
      this.dayIndex.set(format(copy, 'yyyy-MM-dd'), this.days.length);
      this.days.push(copy);
      cursor.setDate(cursor.getDate() + 1);
    }
  }

  /**
   * Recalcule l'index `clientsById`. À appeler après chaque mutation de
   * {@link clients} (chargement initial, quick-create).
   */
  private reindexClients(): void {
    this.clientsById.clear();
    for (const c of this.clients) {
      if (c.clientId != null) this.clientsById.set(c.clientId, c);
    }
  }

  /** Recalcule l'index `societesById` (Tour 45). */
  private reindexSocietes(): void {
    this.societesById.clear();
    for (const s of this.societes) {
      if (s.societeId != null) this.societesById.set(s.societeId, s);
    }
  }

  /**
   * Tour 46 — Recalcule l'index `roomsById`. À appeler après chaque mutation
   * de {@link rooms}. Utilisé par la section "Détails réservation" de la
   * modale paiements pour afficher les numéros de chambre.
   */
  private reindexRooms(): void {
    this.roomsById.clear();
    for (const r of this.rooms) {
      if (r.chambreId != null) this.roomsById.set(r.chambreId, r);
    }
  }

  /** Résout le label d'un client (réutilisé par template + tooltip). */
  consultClientLabel(res: Reservation | null): string {
    if (!res) return '';
    return this.clientLabel(res);
  }

  /** Téléphone résolu via index local — utilisé par la modale consultation. */
  consultClientPhone(res: Reservation | null): string | null {
    if (!res || res.clientPrincipalId == null) return res?.telephoneClient ?? null;
    const c = this.clientsById.get(res.clientPrincipalId);
    return c?.telephone ?? res.telephoneClient ?? null;
  }

  /** Email résolu via index local — utilisé par la modale consultation. */
  consultClientEmail(res: Reservation | null): string | null {
    if (!res || res.clientPrincipalId == null) return res?.emailClient ?? null;
    const c = this.clientsById.get(res.clientPrincipalId);
    return c?.email ?? res.emailClient ?? null;
  }

  /**
   * Libellé à afficher dans le rectangle de réservation (template).
   * Préférence : nom du client principal résolu via l'index local, puis
   * `nomClientPrincipal` éventuel sur le DTO (jour où le backend l'exposera),
   * puis fallback sur le numéro de réservation.
   */
  clientLabel(res: Reservation): string {
    const client = res.clientPrincipalId != null
      ? this.clientsById.get(res.clientPrincipalId)
      : undefined;
    if (client) {
      const nomComplet = (client.nomComplet ?? '').trim();
      if (nomComplet) return nomComplet;
      const prenom = (client.prenom ?? '').trim();
      const nom = (client.nom ?? '').trim();
      const composed = `${prenom} ${nom}`.trim();
      if (composed) return composed;
    }
    return (res as unknown as { nomClientPrincipal?: string }).nomClientPrincipal
      || res.numeroReservation
      || '';
  }

  /**
   * Tour 49 — Catégorie effective d'un type (fallback CHAMBRE si non
   * renseignée par le backend). Centralisé ici pour rester utilisable côté
   * template via {@link typeCategorie}.
   */
  private resolveCategorie(t: TypeChambre): CategorieEspace {
    return t.categorie ?? CATEGORIE_ESPACE_DEFAULT;
  }

  /** Exposé au template pour l'affichage conditionnel d'icônes. */
  typeCategorie(t: TypeChambre): CategorieEspace {
    return this.resolveCategorie(t);
  }

  /**
   * `true` si le groupe à `index` est le premier groupe de catégorie
   * `SALLE` (utilisé pour afficher un séparateur visuel discret).
   */
  isFirstSalleGroup(index: number): boolean {
    if (index <= 0 || index >= this.groups.length) return false;
    const current = this.resolveCategorie(this.groups[index].type);
    if (current !== 'SALLE') return false;
    const previous = this.resolveCategorie(this.groups[index - 1].type);
    return previous !== 'SALLE';
  }

  /**
   * Tour 49 — Groupement des espaces dans le calendrier.
   *
   * Tri en deux étages :
   *  1) Catégorie : `CHAMBRE` d'abord, `SALLE` ensuite (ordre stable).
   *  2) À l'intérieur de chaque catégorie : tri alpha sur `typeNom`.
   *
   * Les chambres elles-mêmes restent triées par `numeroChambre` (asc).
   */
  private groupRooms(): void {
    const byType = new Map<number, Chambre[]>();
    for (const room of this.rooms) {
      if (room.typeId == null) continue;
      const list = byType.get(room.typeId) ?? [];
      list.push(room);
      byType.set(room.typeId, list);
    }
    const categoryRank: Record<CategorieEspace, number> = {
      CHAMBRE: 0,
      SALLE: 1,
    };
    const sortedTypes = [...this.types].sort((a, b) => {
      const rankA = categoryRank[this.resolveCategorie(a)];
      const rankB = categoryRank[this.resolveCategorie(b)];
      if (rankA !== rankB) return rankA - rankB;
      return (a.typeNom || '').localeCompare(b.typeNom || '');
    });
    this.groups = sortedTypes
      .filter((t) => t.typeId != null && byType.has(t.typeId))
      .map((t) => ({
        type: t,
        chambres: (byType.get(t.typeId!) ?? []).sort((a, b) =>
          (a.numeroChambre || '').localeCompare(b.numeroChambre || ''),
        ),
      }));
  }

  private indexReservations(reservations: Reservation[]): void {
    this.reservations = reservations;
    this.reservationsByRoom.clear();
    for (const r of reservations) {
      const chambres = r.chambres ?? [];
      for (const rc of chambres) {
        const existing = this.reservationsByRoom.get(rc.chambreId) ?? [];
        existing.push(r);
        this.reservationsByRoom.set(rc.chambreId, existing);
      }
    }
    for (const list of this.reservationsByRoom.values()) {
      list.sort((a, b) => (a.dateArrivee ?? '').localeCompare(b.dateArrivee ?? ''));
    }
  }

  private toDate(iso: string | undefined): Date | null {
    if (!iso) return null;
    try {
      return parseISO(iso);
    } catch {
      return null;
    }
  }

  // ── Mapping erreurs backend → clés i18n (Phase 2) ──────────────────────

  /**
   * Mappe un `HttpErrorResponse` vers une clé i18n affichable.
   * Le backend renvoie un payload `ApiResponse<T>` avec champs `error` (clé
   * i18n unique) ou `message`. On accepte aussi un format `{errors:[...]}`
   * (variante admin) en fallback.
   */
  private mapBackendError(err: HttpErrorResponse): string {
    const known = new Set<string>([
      'error.reservation.chambre.conflict',
      'error.reservation.changerChambre.terminated',
      'error.reservation.changerChambre.identique',
      'error.reservation.changerChambre.ancienneChambre.notFound',
      'error.reservation.changerChambre.ancienneChambre.required',
      'error.reservation.changerChambre.aucuneChambre',
      'error.reservation.changerChambre.nouvelleChambre.required',
      'error.reservation.changerChambre.raison.tooLong',
      'error.reservation.cancel.alreadyTerminated',
      'error.tarifChambre.calcul.dates.invalid',
      'error.tarifChambre.calcul.dates.required',
      'error.tarifChambre.dateFin.beforeDebut',
      'error.facture.reservation.dejaFacturee',
      'error.facture.reservation.aucuneNuitee',
      // Tour 45 — nouveaux codes mappés
      'error.nuitee.facture.payee',
      'error.nuitee.statut.closed',
      'error.facture.transfert.payee',
      'error.facture.transfert.factureSourceTerminated',
      'error.facture.transfert.factureCibleTerminated',
      'error.checkoutExpress.statut.invalid',
      'error.checkoutExpress.societe.required',
      // Tour 46 — paiement global
      'error.paiement.global.reservationRequired',
      'error.paiement.global.aucuneLigne',
      'error.paiement.global.clientRequired',
      'error.paiement.global.montantInvalid',
      'error.client.notFound',
      // Tour 49 — changement client/société en modal édit réservation
      'error.client.inactif',
      'error.societe.notFound',
      'error.societe.inactive',
    ]);
    const body = err.error as
      | { error?: string; message?: string; errors?: string[] }
      | undefined;
    const candidates: string[] = [];
    if (body?.error) candidates.push(body.error);
    if (body?.message) candidates.push(body.message);
    if (Array.isArray(body?.errors)) candidates.push(...body.errors);
    for (const c of candidates) {
      if (known.has(c)) return c;
      // Le backend peut renvoyer une clé non listée mais qui matche le pattern.
      if (typeof c === 'string' && c.startsWith('error.')) return c;
    }
    if (err.status === 409) return 'error.reservation.chambre.conflict';
    return 'hebergement.messages.saveError';
  }

  // ── Modales (Bootstrap natif) ─────────────────────────────────────────

  private showModal(kind: ModalKind): void {
    const ref = this.modalRef(kind);
    if (!ref?.nativeElement) return;
    let inst = this.modalInstance(kind);
    if (!inst) {
      inst = this.createModalInstanceFor(ref.nativeElement);
      this.setModalInstance(kind, inst);
    }
    inst?.show?.();
  }

  private hideModal(kind: ModalKind): void {
    this.modalInstance(kind)?.hide?.();
  }

  private modalRef(kind: ModalKind): ElementRef<HTMLDivElement> | undefined {
    switch (kind) {
      case 'create':
        return this.createModalRef;
      case 'consult':
        return this.consultModalRef;
      case 'edit':
        return this.editModalRef;
      case 'payments':
        return this.paymentsModalRef;
      case 'modifyNuitees':
        return this.modifyNuiteesModalRef;
    }
  }

  private modalInstance(kind: ModalKind): BootstrapModal | null {
    switch (kind) {
      case 'create':
        return this.createModalInstance;
      case 'consult':
        return this.consultModalInstance;
      case 'edit':
        return this.editModalInstance;
      case 'payments':
        return this.paymentsModalInstance;
      case 'modifyNuitees':
        return this.modifyNuiteesModalInstance;
    }
  }

  private setModalInstance(kind: ModalKind, inst: BootstrapModal | null): void {
    if (kind === 'create') this.createModalInstance = inst;
    else if (kind === 'consult') this.consultModalInstance = inst;
    else if (kind === 'edit') this.editModalInstance = inst;
    else if (kind === 'payments') this.paymentsModalInstance = inst;
    else this.modifyNuiteesModalInstance = inst;
  }

  private createModalInstanceFor(el: HTMLElement): BootstrapModal | null {
    const bs = (window as unknown as { bootstrap?: { Modal?: BootstrapModalCtor } }).bootstrap;
    if (!bs?.Modal) return null;
    return new bs.Modal(el, { backdrop: 'static', keyboard: false });
  }

  // ── Notifications utilisateur ─────────────────────────────────────────

  private toastSuccess(key: string): void {
    Swal.fire({
      toast: true,
      position: 'top-end',
      icon: 'success',
      title: this.i18n.translate(key),
      timer: 2500,
      showConfirmButton: false,
    });
  }

  /** Variant pour afficher un message déjà traduit (avec interpolation manuelle). */
  private toastSuccessRaw(title: string): void {
    Swal.fire({
      toast: true,
      position: 'top-end',
      icon: 'success',
      title,
      timer: 3500,
      showConfirmButton: false,
    });
  }

  private toastError(key: string): void {
    Swal.fire({
      toast: true,
      position: 'top-end',
      icon: 'error',
      title: this.i18n.translate(key),
      timer: 3000,
      showConfirmButton: false,
    });
  }

  private toastInfo(key: string): void {
    Swal.fire({
      toast: true,
      position: 'bottom-end',
      icon: 'info',
      title: this.i18n.translate(key),
      timer: 2000,
      showConfirmButton: false,
    });
  }

  private translateAlertConfirm(
    messageKey: string,
    titleKey?: string,
  ): Promise<boolean> {
    return Swal.fire({
      icon: 'question',
      title: titleKey ? this.i18n.translate(titleKey) : undefined,
      text: this.i18n.translate(messageKey),
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('hebergement.actions.save'),
      cancelButtonText: this.i18n.translate('hebergement.actions.cancel'),
    }).then((res) => res.isConfirmed === true);
  }
}

// ── Types Bootstrap (typage minimal — bootstrap.bundle est chargé global) ──

type ModalKind = 'create' | 'consult' | 'edit' | 'payments' | 'modifyNuitees';

interface BootstrapModal {
  show?: () => void;
  hide?: () => void;
  dispose?: () => void;
}

interface BootstrapModalCtor {
  new (el: HTMLElement, opts?: { backdrop?: boolean | 'static'; keyboard?: boolean }): BootstrapModal;
}
