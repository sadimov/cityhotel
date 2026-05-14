import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, map, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { AuthService } from '../../../../services/auth.service';
import { TranslationService } from '../../../../services/translation.service';
import { ClientsService } from '../../../clients/services/clients.service';
import { Client } from '../../../clients/models/client.model';
import { NightAuditResultDto } from '../../models/night-audit.model';
import { Reservation } from '../../models/reservation.model';
import { NightAuditService } from '../../services/night-audit.service';
import { ReservationsService } from '../../services/reservations.service';

type SectionState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Composant — Page Night Audit (Tour 48).
 *
 * Permet à la réception / au NIGHTAUDIT de PRÉPARER la clôture avant de la
 * déclencher : valider les check-in/check-out en retard, traiter les arrivées
 * & départs du jour, puis cliquer "Lancer la clôture".
 *
 * Trois sections empilées :
 *  - Check-ins en retard (danger)   : CONFIRMEE dont arrivée passée
 *  - Arrivées du jour (info)        : CONFIRMEE arrivant aujourd'hui
 *  - Départs du jour (warning)      : ARRIVEE partant aujourd'hui
 *
 * Chaque section a son propre état loading/ready/empty/error et est
 * rafraîchissable individuellement après une action (check-in, check-out,
 * cancel). Un bouton "Rafraîchir" global relance les 3 GETs.
 *
 * Le bouton "Lancer la clôture" reste TOUJOURS accessible — c'est l'admin
 * qui décide de finaliser, même si des items restent dans les listes.
 */
@Component({
  selector: 'app-night-audit-page',
  templateUrl: './night-audit-page.component.html',
  styleUrls: ['./night-audit-page.component.scss'],
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NightAuditPageComponent implements OnInit, OnDestroy {
  /** Date hôtelière du jour (format `YYYY-MM-DD`). */
  readonly today: string = this.formatToday();

  /** Réservations en retard. */
  retardState: SectionState = 'loading';
  retardItems: Reservation[] = [];

  /** Arrivées du jour. */
  arrivalsState: SectionState = 'loading';
  arrivalsItems: Reservation[] = [];

  /** Départs du jour. */
  departsState: SectionState = 'loading';
  departsItems: Reservation[] = [];

  /** Cache local clientId → Client (résolution prénom + nom). */
  clientsById: Map<number, Client> = new Map();

  /** Loader sur le bouton "Lancer la clôture". */
  launching = false;

  /** Loader par-réservation (évite les double-clics). */
  pending: Record<number, boolean> = {};

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly reservationsService: ReservationsService,
    private readonly nightAuditService: NightAuditService,
    private readonly clientsService: ClientsService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
    private readonly cdr: ChangeDetectorRef,
    private readonly authService: AuthService,
  ) {}

  /**
   * Règle métier hébergement : seul ADMIN/SUPERADMIN peut annuler une
   * réservation. Le bouton "Annuler" des tableaux night audit est masqué
   * pour les autres rôles (GERANT, RECEPTION, RESREC, NIGHTAUDIT...).
   */
  get canCancelReservation(): boolean {
    return this.authService.hasAnyRole(['ADMIN', 'SUPERADMIN']);
  }

  ngOnInit(): void {
    this.loadAll();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Chargement
  // ──────────────────────────────────────────────────────────────────────────

  /** Rafraîchit les 3 listes + cache clients. */
  loadAll(): void {
    this.retardState = 'loading';
    this.arrivalsState = 'loading';
    this.departsState = 'loading';
    this.cdr.markForCheck();

    this.loadRetard();
    this.loadArrivals();
    this.loadDeparts();
    this.loadClients();
  }

  private loadRetard(): void {
    this.reservationsService
      .checkInsEnRetard()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.retardState = 'error';
          this.cdr.markForCheck();
          return of<Reservation[]>([]);
        }),
      )
      .subscribe((list) => {
        if (this.retardState === 'error') return;
        this.retardItems = list ?? [];
        this.retardState = this.retardItems.length === 0 ? 'empty' : 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadArrivals(): void {
    this.reservationsService
      .arriveesToday()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.arrivalsState = 'error';
          this.cdr.markForCheck();
          return of<Reservation[]>([]);
        }),
      )
      .subscribe((list) => {
        if (this.arrivalsState === 'error') return;
        this.arrivalsItems = list ?? [];
        this.arrivalsState = this.arrivalsItems.length === 0 ? 'empty' : 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadDeparts(): void {
    this.reservationsService
      .departsToday()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.departsState = 'error';
          this.cdr.markForCheck();
          return of<Reservation[]>([]);
        }),
      )
      .subscribe((list) => {
        if (this.departsState === 'error') return;
        this.departsItems = list ?? [];
        this.departsState = this.departsItems.length === 0 ? 'empty' : 'ready';
        this.cdr.markForCheck();
      });
  }

  /**
   * Charge la première page de clients pour résoudre les noms. Les vues
   * `arrivees-today`, `departs-today`, `check-ins-retard` portent déjà
   * `nomClientPrincipal` (relation serveur) — on garde le cache en filet
   * de sécurité (clients absents de la projection serveur).
   */
  private loadClients(): void {
    this.clientsService
      .page({ page: 0, size: 200, sortBy: 'nom', sortDir: 'asc' })
      .pipe(
        takeUntil(this.destroy$),
        map((p) => p.content ?? []),
        catchError(() => of<Client[]>([])),
      )
      .subscribe((clients) => {
        const map = new Map<number, Client>();
        for (const c of clients) {
          if (c.clientId != null) {
            map.set(c.clientId, c);
          }
        }
        this.clientsById = map;
        this.cdr.markForCheck();
      });
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Actions ligne
  // ──────────────────────────────────────────────────────────────────────────

  checkIn(reservation: Reservation): void {
    const id = reservation.reservationId;
    if (id == null || this.pending[id]) return;
    this.pending[id] = true;
    this.cdr.markForCheck();
    this.reservationsService
      .checkIn(id)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          delete this.pending[id];
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.toast(
            'success',
            this.i18n.translate(
              'hebergement.calendar.checkInSuccess',
              'Check-in effectué.',
            ),
          );
          this.loadRetard();
          this.loadArrivals();
          this.loadDeparts();
        },
        error: () => {
          this.toast(
            'error',
            this.i18n.translate(
              'hebergement.calendar.checkInError',
              'Impossible d’effectuer le check-in.',
            ),
          );
        },
      });
  }

  checkOut(reservation: Reservation): void {
    const id = reservation.reservationId;
    if (id == null || this.pending[id]) return;
    Swal.fire({
      icon: 'question',
      title: this.i18n.translate(
        'hebergement.calendar.confirmCheckOut',
        'Confirmer le check-out de cette réservation ?',
      ),
      showCancelButton: true,
      confirmButtonText: this.i18n.translate(
        'hebergement.actions.checkOut',
        'Check-out',
      ),
      cancelButtonText: this.i18n.translate(
        'hebergement.actions.close',
        'Fermer',
      ),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) return;
      this.pending[id] = true;
      this.cdr.markForCheck();
      this.reservationsService
        .checkOut(id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            delete this.pending[id];
            this.cdr.markForCheck();
          }),
        )
        .subscribe({
          next: () => {
            this.toast(
              'success',
              this.i18n.translate(
                'hebergement.calendar.checkOutSuccess',
                'Check-out effectué.',
              ),
            );
            this.loadDeparts();
          },
          error: () => {
            this.toast(
              'error',
              this.i18n.translate(
                'hebergement.calendar.checkOutError',
                'Impossible d’effectuer le check-out.',
              ),
            );
          },
        });
    });
  }

  cancel(reservation: Reservation): void {
    const id = reservation.reservationId;
    if (id == null || this.pending[id]) return;
    Swal.fire({
      title: this.i18n.translate(
        'hebergement.messages.cancelConfirm',
        'Annuler cette réservation ?',
      ),
      input: 'text',
      inputLabel: this.i18n.translate(
        'hebergement.messages.cancelMotif',
        'Motif de l’annulation',
      ),
      inputValidator: (value) =>
        value
          ? null
          : this.i18n.translate(
              'hebergement.messages.cancelMotifRequired',
              'Le motif est obligatoire.',
            ),
      showCancelButton: true,
      confirmButtonText: this.i18n.translate(
        'hebergement.actions.cancel',
        'Annuler',
      ),
      cancelButtonText: this.i18n.translate(
        'hebergement.actions.close',
        'Fermer',
      ),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed || !result.value) return;
      this.pending[id] = true;
      this.cdr.markForCheck();
      this.reservationsService
        .annuler(id, String(result.value))
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            delete this.pending[id];
            this.cdr.markForCheck();
          }),
        )
        .subscribe({
          next: () => {
            this.toast(
              'success',
              this.i18n.translate(
                'hebergement.messages.cancelSuccess',
                'Réservation annulée',
              ),
            );
            this.loadRetard();
            this.loadArrivals();
          },
          error: () => {
            this.toast(
              'error',
              this.i18n.translate(
                'hebergement.messages.cancelError',
                'Impossible d’annuler la réservation.',
              ),
            );
          },
        });
    });
  }

  /** "Reporter" = retire la ligne du tableau local (sans appel backend). */
  report(reservation: Reservation): void {
    const id = reservation.reservationId;
    if (id == null) return;
    this.arrivalsItems = this.arrivalsItems.filter(
      (r) => r.reservationId !== id,
    );
    if (this.arrivalsItems.length === 0) {
      this.arrivalsState = 'empty';
    }
    this.toast(
      'info',
      this.i18n.translate(
        'hebergement.nightAudit.page.reportToast',
        'Reporté pour traitement ultérieur',
      ),
    );
    this.cdr.markForCheck();
  }

  /** "Prolonger" — édition de la date de départ via le form réservation. */
  prolong(reservation: Reservation): void {
    if (reservation.reservationId == null) return;
    this.router.navigate([
      '/hebergement/reservations',
      reservation.reservationId,
    ]);
  }

  /**
   * "Payer & sortir" — redirige vers le calendrier en ouvrant directement
   * la modale paiement de la réservation via query param `openPayments`.
   */
  payAndCheckout(reservation: Reservation): void {
    if (reservation.reservationId == null) return;
    this.router.navigate(['/hebergement/calendar'], {
      queryParams: { openPayments: reservation.reservationId },
    });
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Bouton "Lancer la clôture"
  // ──────────────────────────────────────────────────────────────────────────

  launch(): void {
    if (this.launching) return;
    Swal.fire({
      icon: 'warning',
      title: this.i18n.translate(
        'hebergement.nightAudit.page.launchConfirm.title',
        'Lancer la clôture ?',
      ),
      text: this.i18n.translate(
        'hebergement.nightAudit.page.launchConfirm.text',
        'Cette action marquera les réservations non check-in en retard comme NO_SHOW et générera les nuitées manquantes.',
      ),
      showCancelButton: true,
      confirmButtonColor: '#dc3545',
      confirmButtonText: this.i18n.translate(
        'hebergement.nightAudit.page.launchConfirm.confirm',
        'Lancer',
      ),
      cancelButtonText: this.i18n.translate(
        'hebergement.actions.close',
        'Fermer',
      ),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) return;
      this.launching = true;
      this.cdr.markForCheck();
      this.nightAuditService
        .run()
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.launching = false;
            this.cdr.markForCheck();
          }),
        )
        .subscribe({
          next: (dto) => this.showRunResult(dto),
          error: () =>
            this.toast(
              'error',
              this.i18n.translate(
                'hebergement.nightAudit.page.error',
                'Erreur lors du night audit.',
              ),
            ),
        });
    });
  }

  back(): void {
    this.router.navigate(['/hebergement/calendar']);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Helpers d'affichage (template)
  // ──────────────────────────────────────────────────────────────────────────

  clientName(reservation: Reservation): string {
    if (reservation.nomClientPrincipal) return reservation.nomClientPrincipal;
    const c = this.clientsById.get(reservation.clientPrincipalId);
    if (!c) return '-';
    const full = `${c.prenom ?? ''} ${c.nom ?? ''}`.trim();
    return full || c.nomComplet || '-';
  }

  /**
   * Concatène les numéros de chambre (résolus serveur via `chambres[].numeroChambre`).
   * Fallback "-" si la projection serveur n'inclut pas le détail.
   */
  chambres(reservation: Reservation): string {
    if (!reservation.chambres || reservation.chambres.length === 0) {
      const n = reservation.nombreChambres ?? 0;
      return n > 0 ? `${n}` : '-';
    }
    return reservation.chambres
      .map((c) => c.numeroChambre || c.nomCompletChambre || '?')
      .join(', ');
  }

  isPending(reservation: Reservation): boolean {
    return reservation.reservationId != null
      ? this.pending[reservation.reservationId] === true
      : false;
  }

  trackByReservationId(_idx: number, r: Reservation): number {
    return r.reservationId ?? -1;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Internes
  // ──────────────────────────────────────────────────────────────────────────

  private showRunResult(dto: NightAuditResultDto): void {
    // `translate(key, params)` — `params` est un Record<string, unknown>
    // qui sera passé à ngx-translate pour l'interpolation `{{nbNoShow}}` etc.
    const summary = this.i18n.translate(
      'hebergement.nightAudit.page.result.text',
      {
        nbNoShow: dto.nbReservationsMarkedNoShow,
        nbNuitees: dto.nbNuiteesManquantesGenerees,
      },
    );
    // Fallback hardcodé si la clé n'existe pas (ngx-translate renvoie la clé
    // brute dans ce cas).
    const safeText =
      summary && summary !== 'hebergement.nightAudit.page.result.text'
        ? summary
        : `${dto.nbReservationsMarkedNoShow} réservation(s) NO_SHOW · ${dto.nbNuiteesManquantesGenerees} nuitée(s) générée(s)`;
    Swal.fire({
      icon: 'success',
      title: this.i18n.translate(
        'hebergement.nightAudit.page.result.title',
        'Clôture effectuée',
      ),
      text: safeText,
      confirmButtonText: this.i18n.translate('common.close', 'Fermer'),
    }).then(() => {
      this.router.navigate(['/hebergement/calendar']);
    });
  }

  private toast(
    icon: 'success' | 'error' | 'info' | 'warning',
    text: string,
  ): void {
    Swal.fire({
      toast: true,
      position: 'top-end',
      icon,
      title: text,
      timer: 2500,
      showConfirmButton: false,
      timerProgressBar: true,
    });
  }

  private formatToday(): string {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
