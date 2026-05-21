import {
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';
import { Observable, Subject, combineLatest } from 'rxjs';
import {
  debounceTime,
  distinctUntilChanged,
  map,
  takeUntil,
  withLatestFrom,
} from 'rxjs/operators';

import { Client } from '../../../../clients/models/client.model';
import { ClientsService } from '../../../../clients/services/clients.service';
import {
  STATUT_RESERVATION_BADGE_MAP,
  Reservation,
  StatutReservation,
  statutReservationKey,
} from '../../../../hebergement/models/reservation.model';
import { PosStore } from '../state/pos.store';

/** Onglets de la modale de sélection. */
type Tab = 'reservations' | 'client';

/**
 * Modale de sélection client + réservation pour le POS.
 *
 * Deux onglets :
 *  - **Réservations en cours** : autocomplete client puis affichage des
 *    réservations actives (ARRIVEE / CONFIRMEE en plage de séjour). Clic sur
 *    une résa = sélection client + résa, fermeture modale.
 *  - **Client direct** : autocomplete client uniquement (pas de résa).
 *    Cas : walk-in repas du midi, vente à emporter.
 *
 * Le composant pilote uniquement le `PosStore` ; il ne stocke pas d'état UI
 * autre que l'onglet courant + recherche locale (déjà dans le store).
 */
@Component({
  selector: 'app-pos-client-search',
  templateUrl: './client-search.component.html',
  styleUrls: ['./client-search.component.scss'],
  standalone: false,
})
export class ClientSearchComponent implements OnInit, OnDestroy {
  @Input() open = false;
  @Output() readonly close = new EventEmitter<void>();

  readonly clientSearch$ = this.store.clientSearch$;
  readonly clientResultsLoading$ = this.store.clientResultsLoading$;
  readonly selectedClient$ = this.store.selectedClient$;
  readonly reservationsLoading$ = this.store.reservationsLoading$;
  readonly selectedReservation$ = this.store.selectedReservation$;

  /**
   * Tour 55 — Filtre client-side appliquÃ© au-dessus des rÃ©sultats serveur
   * pour le champ recherche. Couvre prÃ©nom, nom, `prÃ©nom + nom`, nomComplet,
   * email, tÃ©lÃ©phone, numÃ©roClient (case-insensitive).
   *
   * Les rÃ©sultats serveur arrivent triÃ©s par pertinence (`recherche` param,
   * filtre tenant cÃ´tÃ© backend) ; le filtre client est une seconde passe pour
   * gÃ©rer les cas oÃ¹ le serveur ne couvre pas tous les champs (ex. saisie
   * « nom complet »).
   */
  readonly clientResults$: Observable<Client[]> = combineLatest([
    this.store.clientResults$,
    this.store.clientSearch$,
  ]).pipe(
    map(([results, term]) => this.filterClients(results, term)),
  );

  /**
   * RÃ©servations actives du client sÃ©lectionnÃ©. La liste arrive dÃ©jÃ  filtrÃ©e
   * par {@code clientId} cÃ´tÃ© backend ; aucun filtre client-side ici sinon le
   * search term (utilisÃ© pour trouver le CLIENT) masque les rÃ©sas qui ne
   * matchent pas ce terme dans leurs champs.
   */
  readonly activeReservations$: Observable<Reservation[]> = this.store.activeReservations$;

  activeTab: Tab = 'reservations';

  /**
   * Valeur locale synchrone du champ recherche, bindée via `[value]` côté
   * template. NE PAS binder `[value]="(clientSearch$ | async)"` : le store
   * n'est mis à jour qu'après debounce 200ms, donc à chaque keystroke,
   * change detection écrasait `input.value` avec l'ancienne valeur du store
   * et l'utilisateur perdait son caractère (consigne user 2026-05-21
   * « le filtre client n'est pas stable »).
   * Source de vérité durant la saisie = ce champ. Le store est resync via
   * le pipeline debounce ; la subscription dans `ngOnInit` couvre les
   * resets externes (clearClient, ouverture modale, etc.).
   */
  searchTerm = '';

  private readonly searchInput$ = new Subject<string>();
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly store: PosStore,
    private readonly clientsService: ClientsService,
  ) {}

  ngOnInit(): void {
    // Resync local quand le store est mis à jour de l'extérieur
    // (clearClient, ouverture modale, sélection programmatique).
    this.store.clientSearch$
      .pipe(takeUntil(this.destroy$))
      .subscribe((storeTerm) => {
        const safe = storeTerm ?? '';
        if (safe !== this.searchTerm) {
          this.searchTerm = safe;
        }
      });

    this.searchInput$
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        withLatestFrom(this.store.selectedClient$),
        takeUntil(this.destroy$),
      )
      .subscribe(([term, currentClient]) => {
        const trimmed = term.trim();
        // Si l'utilisateur retape alors qu'un client est dÃ©jÃ  sÃ©lectionnÃ©,
        // on dÃ©sÃ©lectionne pour permettre une nouvelle recherche fraÃ®che.
        // Sans Ã§a, l'UI reste bloquÃ©e sur le client prÃ©cÃ©dent (cf. bug
        // "recherche d'un 2e client ne fait rien").
        if (currentClient != null) {
          this.store.deselectClient();
        }
        this.store.setClientSearch(trimmed);
        if (!trimmed) {
          this.store.setClientResults([]);
          return;
        }
        // Charge davantage de rÃ©sultats serveur que ce qui sera affichÃ©, pour
        // donner au filtre client-side une matiÃ¨re plus riche (email, tÃ©lÃ©phone,
        // prÃ©nom + nom concatÃ©nÃ©s) que le seul filtre `recherche` backend.
        const results$ = this.clientsService
          .page({
            page: 0,
            size: 25,
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

  setTab(tab: Tab): void {
    this.activeTab = tab;
  }

  onSearchInput(value: string): void {
    // Maj locale SYNCHRONE → garantit que `[value]="searchTerm"` ne réécrase
    // pas l'input à la prochaine change detection (cf. doc `searchTerm`).
    this.searchTerm = value;
    this.searchInput$.next(value);
  }

  onSelectClient(client: Client, closeAfter: boolean): void {
    this.store.selectClient(client);
    if (client.clientId != null) {
      this.store.loadActiveReservationsForClient(client.clientId);
    }
    if (closeAfter) {
      // Onglet « Client direct » → ferme directement.
      this.close.emit();
    }
  }

  onSelectReservation(reservation: Reservation): void {
    // Si on n'a pas encore de client sélectionné, on le déduit de la résa.
    if (reservation.nomClientPrincipal && reservation.reservationId != null) {
      const stubClient: Client = {
        clientId: reservation.clientPrincipalId,
        nom: reservation.nomClientPrincipal,
        prenom: '',
      } as Client;
      this.store.selectClient(stubClient);
    }
    this.store.selectReservation(reservation);
    this.close.emit();
  }

  onClearClient(): void {
    this.store.clearClient();
  }

  onClose(): void {
    this.close.emit();
  }

  trackByClientId(_index: number, client: Client): number | string {
    return client.clientId ?? client.numeroClient ?? _index;
  }

  trackByReservationId(_index: number, reservation: Reservation): number | string {
    return reservation.reservationId ?? _index;
  }

  statutKey(statut: StatutReservation | undefined): string {
    return statutReservationKey(statut);
  }

  statutBadgeClass(statut: StatutReservation | undefined): string {
    if (!statut) {
      return 'text-bg-secondary';
    }
    return STATUT_RESERVATION_BADGE_MAP[statut] ?? 'text-bg-secondary';
  }

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

  /**
   * Tour 55 — Filtre client-side sur clients. Case-insensitive, accept tout
   * match partiel sur prÃ©nom, nom, `prÃ©nom + ' ' + nom`, nomComplet, email,
   * tÃ©lÃ©phone, numÃ©roClient.
   */
  private filterClients(clients: Client[], rawTerm: string): Client[] {
    const term = (rawTerm ?? '').trim().toLocaleLowerCase();
    if (!term) {
      return clients;
    }
    return clients.filter((c) => {
      const fullName = `${c.prenom ?? ''} ${c.nom ?? ''}`.trim();
      const haystacks = [
        c.prenom,
        c.nom,
        fullName,
        c.nomComplet,
        c.email,
        c.telephone,
        c.numeroClient,
      ];
      return haystacks
        .filter((v): v is string => !!v)
        .some((v) => v.toLocaleLowerCase().includes(term));
    });
  }

}
