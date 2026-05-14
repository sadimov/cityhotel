import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, map, takeUntil } from 'rxjs/operators';

import { Client } from '../../../../clients/models/client.model';
import { ClientsService } from '../../../../clients/services/clients.service';
import {
  Reservation,
  STATUT_RESERVATION_BADGE_MAP,
  StatutReservation,
  statutReservationKey,
} from '../../../../hebergement/models/reservation.model';
import { PosStore } from '../state/pos.store';

/**
 * Recherche client + affichage des réservations actives associées.
 *
 * Pilote le `PosStore` :
 *  - met à jour `clientResults` au fil de la frappe (debounce 300 ms) ;
 *  - sélectionne le client → déclenche `loadActiveReservationsForClient`.
 *
 * Le composant ne stocke aucun état UI propre : tout passe par le store.
 */
@Component({
  selector: 'app-pos-client-search',
  templateUrl: './client-search.component.html',
  styleUrls: ['./client-search.component.scss'],
  standalone: false,
})
export class ClientSearchComponent implements OnInit, OnDestroy {
  readonly clientSearch$ = this.store.clientSearch$;
  readonly clientResults$ = this.store.clientResults$;
  readonly clientResultsLoading$ = this.store.clientResultsLoading$;
  readonly selectedClient$ = this.store.selectedClient$;
  readonly activeReservations$ = this.store.activeReservations$;
  readonly reservationsLoading$ = this.store.reservationsLoading$;
  readonly selectedReservation$ = this.store.selectedReservation$;

  private readonly searchInput$ = new Subject<string>();
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly store: PosStore,
    private readonly clientsService: ClientsService,
  ) {}

  ngOnInit(): void {
    this.searchInput$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        takeUntil(this.destroy$),
      )
      .subscribe((term) => {
        const trimmed = term.trim();
        this.store.setClientSearch(trimmed);
        if (!trimmed) {
          this.store.setClientResults([]);
          return;
        }
        const results$ = this.clientsService
          .page({
            page: 0,
            size: 10,
            sortBy: 'nom',
            sortDir: 'asc',
            recherche: trimmed,
          })
          .pipe(map((p) => p.content));
        this.store.applyClientResults(results$);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSearchInput(value: string): void {
    this.searchInput$.next(value);
  }

  onSelectClient(client: Client): void {
    this.store.selectClient(client);
    if (client.clientId != null) {
      this.store.loadActiveReservationsForClient(client.clientId);
    }
  }

  onClearClient(): void {
    this.store.clearClient();
  }

  onSelectReservation(reservation: Reservation): void {
    this.store.selectReservation(reservation);
  }

  onClearReservation(): void {
    this.store.selectReservation(null);
  }

  trackByClientId(_index: number, client: Client): number | string {
    return client.clientId ?? client.numeroClient ?? _index;
  }

  trackByReservationId(
    _index: number,
    reservation: Reservation,
  ): number | string {
    return reservation.reservationId ?? _index;
  }

  /** Clé i18n du statut réservation (cf. hebergement.statut.*). */
  statutKey(statut: StatutReservation | undefined): string {
    return statutReservationKey(statut);
  }

  /** Classe Bootstrap pour le badge de statut. */
  statutBadgeClass(statut: StatutReservation | undefined): string {
    if (!statut) {
      return 'text-bg-secondary';
    }
    return STATUT_RESERVATION_BADGE_MAP[statut] ?? 'text-bg-secondary';
  }

  /**
   * Concatène les numéros de chambre d'une réservation pour affichage
   * compact (ex. « 101, 102 ») — utile quand la réservation porte sur
   * plusieurs chambres.
   */
  chambreLabel(reservation: Reservation): string {
    const chambres = reservation.chambres;
    if (!chambres || chambres.length === 0) {
      return '';
    }
    const numeros = chambres
      .map((c) => c.numeroChambre)
      .filter((n): n is string => !!n);
    return numeros.join(', ');
  }

  /** Liste les types de chambre distincts (« Standard, Suite »). */
  typeLabel(reservation: Reservation): string {
    const chambres = reservation.chambres;
    if (!chambres || chambres.length === 0) {
      return '';
    }
    const types = Array.from(
      new Set(chambres.map((c) => c.typeNom).filter((t): t is string => !!t)),
    );
    return types.join(', ');
  }
}
