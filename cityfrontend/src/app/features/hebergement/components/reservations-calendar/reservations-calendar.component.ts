import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import {
  addMonths,
  eachDayOfInterval,
  endOfMonth,
  endOfWeek,
  format,
  isSameDay,
  isSameMonth,
  parseISO,
  startOfMonth,
  startOfWeek,
  subMonths,
} from 'date-fns';
import { Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';
import { of } from 'rxjs';

import {
  Reservation,
  STATUT_RESERVATION_CHIP_MAP,
  StatutReservation,
} from '../../models/reservation.model';
import { ReservationsService } from '../../services/reservations.service';

type CalendarState = 'loading' | 'ready' | 'error';

interface CalendarCell {
  /** Date complète (Date JS — référence stable pour `track`). */
  date: Date;
  /** Format ISO `yyyy-MM-dd` — utile pour les comparaisons côté template. */
  iso: string;
  /** Numéro du jour 1..31. */
  day: number;
  /** Vrai si la cellule est dans le mois affiché (les autres sont grisées). */
  inCurrentMonth: boolean;
  /** Vrai si la cellule correspond à la date du jour. */
  isToday: boolean;
  /** Réservations dont la période chevauche ce jour. */
  reservations: Reservation[];
}

/**
 * Calendrier visuel des réservations.
 *
 * Approche pragmatique (cf. brief Tour 11) : grille HTML 7 colonnes (Lun→Dim)
 * × 5 ou 6 lignes (semaines), chaque cellule liste les réservations dont la
 * période [`dateArrivee`, `dateDepart`) couvre le jour. Pas de lib FullCalendar
 * (absente de `package.json`) — `date-fns` suffit.
 *
 * Les données sont chargées via `ReservationsService.page()` filtré sur la
 * période du mois affiché. Le clic sur une réservation route vers son détail
 * (`/hebergement/reservations/:id`).
 */
@Component({
  selector: 'app-reservations-calendar',
  templateUrl: './reservations-calendar.component.html',
  styleUrls: ['./reservations-calendar.component.scss'],
  standalone: false,
})
export class ReservationsCalendarComponent implements OnInit, OnDestroy {
  state: CalendarState = 'loading';
  /** Premier jour du mois affiché (ancrage de navigation). */
  monthAnchor: Date = startOfMonth(new Date());
  cells: CalendarCell[] = [];
  /** Libellés Lun..Dim (clés i18n). */
  readonly weekdayKeys: ReadonlyArray<string> = [
    'hebergement.calendar.weekday.mon',
    'hebergement.calendar.weekday.tue',
    'hebergement.calendar.weekday.wed',
    'hebergement.calendar.weekday.thu',
    'hebergement.calendar.weekday.fri',
    'hebergement.calendar.weekday.sat',
    'hebergement.calendar.weekday.sun',
  ];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly reservationsService: ReservationsService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.loadMonth();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Libellé "Mai 2026". */
  get monthLabel(): string {
    return format(this.monthAnchor, 'LLLL yyyy');
  }

  prevMonth(): void {
    this.monthAnchor = subMonths(this.monthAnchor, 1);
    this.loadMonth();
  }

  nextMonth(): void {
    this.monthAnchor = addMonths(this.monthAnchor, 1);
    this.loadMonth();
  }

  goToday(): void {
    this.monthAnchor = startOfMonth(new Date());
    this.loadMonth();
  }

  goToReservation(reservation: Reservation): void {
    if (reservation.reservationId == null) {
      return;
    }
    this.router.navigate(['/hebergement/reservations', reservation.reservationId]);
  }

  /** Classe CSS pour color-coder le statut de réservation. */
  statutClass(statut: StatutReservation | undefined): string {
    return STATUT_RESERVATION_CHIP_MAP[statut ?? StatutReservation.EN_ATTENTE];
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private loadMonth(): void {
    this.state = 'loading';
    const monthStart = startOfMonth(this.monthAnchor);
    const monthEnd = endOfMonth(this.monthAnchor);

    // On charge toutes les réservations dont l'arrivée est dans le mois
    // affiché (heuristique simple — un endpoint dédié `/calendar` viendra
    // probablement dans une itération ultérieure).
    this.reservationsService
      .page(
        {
          dateArriveeDebut: format(monthStart, 'yyyy-MM-dd'),
          dateArriveeFin: format(monthEnd, 'yyyy-MM-dd'),
        },
        0,
        500, // page large : on veut tout afficher
        'dateArrivee',
        'asc',
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
        this.cells = this.buildCells(monthStart, monthEnd, p.content);
        this.state = 'ready';
      });
  }

  private buildCells(
    monthStart: Date,
    monthEnd: Date,
    reservations: Reservation[],
  ): CalendarCell[] {
    const gridStart = startOfWeek(monthStart, { weekStartsOn: 1 });
    const gridEnd = endOfWeek(monthEnd, { weekStartsOn: 1 });
    const today = new Date();

    return eachDayOfInterval({ start: gridStart, end: gridEnd }).map((date) => {
      const reservationsThisDay = reservations.filter((r) =>
        this.dayInReservation(date, r),
      );
      return {
        date,
        iso: format(date, 'yyyy-MM-dd'),
        day: date.getDate(),
        inCurrentMonth: isSameMonth(date, monthStart),
        isToday: isSameDay(date, today),
        reservations: reservationsThisDay,
      };
    });
  }

  /**
   * Une réservation chevauche un jour J si `dateArrivee <= J < dateDepart`.
   * (Le jour de départ matin n'est pas occupé.)
   */
  private dayInReservation(day: Date, reservation: Reservation): boolean {
    if (!reservation.dateArrivee || !reservation.dateDepart) {
      return false;
    }
    try {
      const arrivee = parseISO(reservation.dateArrivee);
      const depart = parseISO(reservation.dateDepart);
      return day >= arrivee && day < depart;
    } catch {
      return false;
    }
  }
}
