import { Injectable } from '@angular/core';
import { ComponentStore } from '@ngrx/component-store';
import { EMPTY, Observable, forkJoin, of } from 'rxjs';
import {
  catchError,
  map,
  switchMap,
  tap,
  withLatestFrom,
} from 'rxjs/operators';

import { Client } from '../../../../clients/models/client.model';
import {
  Reservation,
  StatutReservation,
} from '../../../../hebergement/models/reservation.model';
import { ReservationsService } from '../../../../hebergement/services/reservations.service';
import { ServiceHotelier } from '../../../../inventory/models/service-hotelier.model';
import {
  AjouterLigneServiceRequest,
  LigneServiceService,
} from '../../../../inventory/services/ligne-service.service';
import { ServicesHoteliersService } from '../../../../inventory/services/services-hoteliers.service';
import { ArticleMenu } from '../../../models/article-menu.model';
import { CategorieMenu } from '../../../models/categorie-menu.model';
import {
  Commande,
  CreerCommandeRequest,
  CreerLigneCommandeRequest,
  EncaissementCommandeRequest,
  LigneCommande,
  ModeReglement,
} from '../../../models/commande.model';
import { TicketDto } from '../../../models/ticket.model';
import { ArticlesMenusService } from '../../../services/articles-menus.service';
import { CategoriesMenusService } from '../../../services/categories-menus.service';
import { CommandesService } from '../../../services/commandes.service';
import { TicketsService } from '../../../services/tickets.service';

/**
 * Ã‰tapes du workflow POS â€” pilote l'affichage de la zone active.
 *
 *  - `SELECT_CLIENT` : recherche/sÃ©lection client + (optionnel) rÃ©servation.
 *  - `SELECT_ARTICLES` : grille articles + Ã©dition panier.
 *  - `PAYMENT` : modal de paiement ouvert.
 */
export enum PosStep {
  SELECT_CLIENT = 'SELECT_CLIENT',
  SELECT_ARTICLES = 'SELECT_ARTICLES',
  PAYMENT = 'PAYMENT',
}

/**
 * Mode de la grille POS (Tour 55) — bascule entre catalogue restaurant et
 * services hôteliers. Le mode est UI-only : il pilote uniquement quelle
 * collection (`articles` vs `services`) est affichée par la grille. Les deux
 * collections coexistent au panier.
 */
export type PosCatalogMode = 'ARTICLES' | 'SERVICES';

/**
 * Ã‰tat du POS â€” un seul `ComponentStore` local au composant `PosComponent`,
 * dÃ©truit avec lui (pas de pollution du store global NgRx).
 *
 * Conventions :
 *  - immutabilitÃ© stricte (toujours retourner un nouvel objet dans les
 *    updaters â€” runtimeChecks NgRx hÃ©ritÃ©s du store global le vÃ©rifient
 *    en dev).
 *  - `error` / `lastSuccessMessage` sont des **clÃ©s i18n** ; le composant
 *    les passe Ã  `TranslateService.instant()` pour le rendu visuel.
 */
export interface PosState {
  step: PosStep;

  // Catalogue
  /** Mode actif de la grille (Tour 55) — `ARTICLES` (par dÃ©faut) ou `SERVICES`. */
  mode: PosCatalogMode;
  categories: CategorieMenu[];
  selectedCategorieId: number | null;
  articles: ArticleMenu[];
  articlesLoading: boolean;
  articleSearch: string;

  // Services hÃ´teliers (Tour 55)
  services: ServiceHotelier[];
  servicesLoading: boolean;
  servicesLoaded: boolean;

  // Client + rÃ©servation
  clientSearch: string;
  clientResults: Client[];
  clientResultsLoading: boolean;
  selectedClient: Client | null;
  activeReservations: Reservation[];
  reservationsLoading: boolean;
  selectedReservation: Reservation | null;

  // Panier
  cart: LigneCommande[];

  // Paiement
  modeReglement: ModeReglement;

  // Ã‰tats globaux
  submitting: boolean;
  /** ClÃ© i18n d'erreur courante (ex. `restaurant.pos.errors.cartEmpty`). */
  error: string | null;
  /** ClÃ© i18n de succÃ¨s derniÃ¨re action. */
  lastSuccessMessage: string | null;
  /** DerniÃ¨re commande crÃ©Ã©e (utilisÃ©e pour navigation post-paiement). */
  lastCommande: Commande | null;
  /** Vrai pendant l'impression d'un ticket (caisse / cuisine). */
  printingTicket: boolean;
  /** Dernier ticket Ã©ditÃ© (si l'utilisateur souhaite re-tÃ©lÃ©charger). */
  lastTicket: TicketDto | null;
}

const INITIAL_STATE: PosState = {
  step: PosStep.SELECT_CLIENT,
  mode: 'ARTICLES',
  categories: [],
  selectedCategorieId: null,
  articles: [],
  articlesLoading: false,
  articleSearch: '',
  services: [],
  servicesLoading: false,
  servicesLoaded: false,
  clientSearch: '',
  clientResults: [],
  clientResultsLoading: false,
  selectedClient: null,
  activeReservations: [],
  reservationsLoading: false,
  selectedReservation: null,
  cart: [],
  modeReglement: ModeReglement.COMPTANT,
  submitting: false,
  error: null,
  lastSuccessMessage: null,
  lastCommande: null,
  printingTicket: false,
  lastTicket: null,
};

/**
 * GÃ©nÃ¨re un identifiant local pour une ligne panier. Pas besoin de
 * cryptographie â€” un compteur monotone suffit (le store est local et
 * dÃ©truit avec le composant).
 */
let cartLineCounter = 0;
function nextCartLineId(): string {
  cartLineCounter += 1;
  return `cl_${cartLineCounter}`;
}

/**
 * DÃ©termine si une rÃ©servation est "active" pour le POS (le client peut
 * imputer la commande dessus). CritÃ¨res :
 *  - statut ARRIVEE (client sÃ©journe actuellement)
 *  - OU statut CONFIRMEE (rÃ©servation confirmÃ©e, mÃªme pour une arrivÃ©e future
 *    â€” cas vente Ã  emporter avant check-in, repas d'attente, etc.)
 *
 * Sont exclues : PARTIE (check-out fait), ANNULEE, NO_SHOW.
 */
function isActiveForPos(reservation: Reservation): boolean {
  return (
    reservation.statut === StatutReservation.ARRIVEE ||
    reservation.statut === StatutReservation.CONFIRMEE
  );
}

/**
 * NgRx Component Store local du POS Restaurant.
 *
 * Provided au niveau du composant `PosComponent` (cf. `providers: [PosStore]`)
 * pour que son cycle de vie soit liÃ© Ã  celui du composant.
 */
@Injectable()
export class PosStore extends ComponentStore<PosState> {
  constructor(
    private readonly categoriesService: CategoriesMenusService,
    private readonly articlesService: ArticlesMenusService,
    private readonly reservationsService: ReservationsService,
    private readonly commandesService: CommandesService,
    private readonly ticketsService: TicketsService,
    private readonly servicesHoteliersService: ServicesHoteliersService,
    private readonly ligneServiceService: LigneServiceService,
  ) {
    super(INITIAL_STATE);
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Selectors
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  readonly step$ = this.select((s) => s.step);
  readonly mode$ = this.select((s) => s.mode);
  readonly categories$ = this.select((s) => s.categories);
  readonly selectedCategorieId$ = this.select((s) => s.selectedCategorieId);
  readonly articles$ = this.select((s) => s.articles);
  readonly articlesLoading$ = this.select((s) => s.articlesLoading);
  readonly articleSearch$ = this.select((s) => s.articleSearch);

  // Services hÃ´teliers (Tour 55)
  readonly services$ = this.select((s) => s.services);
  readonly servicesLoading$ = this.select((s) => s.servicesLoading);
  readonly servicesLoaded$ = this.select((s) => s.servicesLoaded);

  readonly clientSearch$ = this.select((s) => s.clientSearch);
  readonly clientResults$ = this.select((s) => s.clientResults);
  readonly clientResultsLoading$ = this.select((s) => s.clientResultsLoading);
  readonly selectedClient$ = this.select((s) => s.selectedClient);
  readonly activeReservations$ = this.select((s) => s.activeReservations);
  readonly reservationsLoading$ = this.select((s) => s.reservationsLoading);
  readonly selectedReservation$ = this.select((s) => s.selectedReservation);

  readonly cart$ = this.select((s) => s.cart);
  readonly modeReglement$ = this.select((s) => s.modeReglement);
  readonly submitting$ = this.select((s) => s.submitting);
  readonly error$ = this.select((s) => s.error);
  readonly lastSuccessMessage$ = this.select((s) => s.lastSuccessMessage);
  readonly lastCommande$ = this.select((s) => s.lastCommande);
  readonly printingTicket$ = this.select((s) => s.printingTicket);
  readonly lastTicket$ = this.select((s) => s.lastTicket);

  /** Total panier en MRU (somme des sous-totaux toutes lignes confondues). */
  readonly total$ = this.select(this.cart$, (cart) =>
    cart.reduce((sum, line) => sum + line.sousTotal, 0),
  );

  /**
   * Total panier limitÃ© aux lignes ARTICLE (Tour 55) — utilisÃ© pour
   * `montantPaye` de l'encaissement comptant (les SERVICE ne sont pas
   * encaissables ici, ils sont facturÃ©s Ã  la chambre).
   */
  readonly articleTotal$ = this.select(this.cart$, (cart) =>
    cart
      .filter((l) => (l.type ?? 'ARTICLE') === 'ARTICLE')
      .reduce((sum, line) => sum + line.sousTotal, 0),
  );

  /** Total des lignes SERVICE (Tour 55). */
  readonly serviceTotal$ = this.select(this.cart$, (cart) =>
    cart
      .filter((l) => l.type === 'SERVICE')
      .reduce((sum, line) => sum + line.sousTotal, 0),
  );

  /** Nombre total d'articles (somme des quantitÃ©s). */
  readonly itemsCount$ = this.select(this.cart$, (cart) =>
    cart.reduce((sum, line) => sum + line.quantite, 0),
  );

  /**
   * Vrai si le panier est vide.
   */
  readonly isCartEmpty$ = this.select(this.cart$, (cart) => cart.length === 0);

  /**
   * Vrai si le panier contient au moins une ligne ARTICLE (Tour 55).
   */
  readonly hasArticlesInCart$ = this.select(this.cart$, (cart) =>
    cart.some((l) => (l.type ?? 'ARTICLE') === 'ARTICLE'),
  );

  /**
   * Vrai si le panier contient au moins une ligne SERVICE (Tour 55).
   */
  readonly hasServicesInCart$ = this.select(this.cart$, (cart) =>
    cart.some((l) => l.type === 'SERVICE'),
  );

  /**
   * Vrai si la commande peut Ãªtre encaissÃ©e comptant. Conditions :
   *  - au moins une ligne (ARTICLE ou SERVICE) ;
   *  - client sÃ©lectionnÃ© (un POS sans client est interdit en V1).
   *
   * Note : depuis l'ouverture POS aux services en comptant, on accepte aussi
   * les paniers 100% SERVICE. Le push sur facture chambre reste possible via
   * `canPushServices$` quand une rÃ©servation est sÃ©lectionnÃ©e.
   */
  readonly canCheckoutComptant$ = this.select(
    this.cart$,
    this.selectedClient$,
    this.submitting$,
    (cart, client, submitting) =>
      cart.length > 0 && client != null && !submitting,
  );

  /**
   * Vrai si la commande peut Ãªtre reportÃ©e sur chambre. Conditions :
   *  - au moins une ligne ARTICLE ;
   *  - client sÃ©lectionnÃ© ;
   *  - rÃ©servation sÃ©lectionnÃ©e ET en statut {@code ARRIVEE} (check-in fait).
   *
   * Le backend exige {@code statut == ARRIVEE} pour valider le report :
   * une consommation ne peut pas Ãªtre facturÃ©e Ã  une chambre dont le
   * client n'est pas encore arrivÃ© (CommandeServiceImpl.java:156).
   */
  readonly canReportChambre$ = this.select(
    this.cart$,
    this.selectedClient$,
    this.selectedReservation$,
    this.submitting$,
    (cart, client, reservation, submitting) =>
      cart.some((l) => (l.type ?? 'ARTICLE') === 'ARTICLE') &&
      client != null &&
      reservation != null &&
      reservation.reservationId != null &&
      reservation.statut === StatutReservation.ARRIVEE &&
      !submitting,
  );

  /**
   * Vrai si on peut pousser des services sur la facture chambre (Tour 55).
   * Conditions : au moins une ligne SERVICE + rÃ©servation sÃ©lectionnÃ©e.
   */
  readonly canPushServices$ = this.select(
    this.cart$,
    this.selectedReservation$,
    this.submitting$,
    (cart, reservation, submitting) =>
      cart.some((l) => l.type === 'SERVICE') &&
      reservation != null &&
      reservation.reservationId != null &&
      !submitting,
  );

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Updaters (synchrones)
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  readonly setStep = this.updater<PosStep>((state, step) => ({
    ...state,
    step,
  }));

  /** (Tour 55) Bascule entre la grille articles et la grille services. */
  readonly setMode = this.updater<PosCatalogMode>((state, mode) => ({
    ...state,
    mode,
    // Reset recherche locale en basculant pour Ã©viter de filtrer un catalogue
    // avec un terme appartenant Ã  l'autre.
    articleSearch: '',
  }));

  readonly setServices = this.updater<ServiceHotelier[]>((state, services) => ({
    ...state,
    services,
    servicesLoaded: true,
  }));

  readonly setServicesLoading = this.updater<boolean>((state, loading) => ({
    ...state,
    servicesLoading: loading,
  }));

  readonly setCategories = this.updater<CategorieMenu[]>((state, categories) => ({
    ...state,
    categories,
  }));

  readonly setSelectedCategorie = this.updater<number | null>(
    (state, selectedCategorieId) => ({
      ...state,
      selectedCategorieId,
    }),
  );

  readonly setArticles = this.updater<ArticleMenu[]>((state, articles) => ({
    ...state,
    articles,
  }));

  readonly setArticlesLoading = this.updater<boolean>((state, loading) => ({
    ...state,
    articlesLoading: loading,
  }));

  readonly setArticleSearch = this.updater<string>((state, articleSearch) => ({
    ...state,
    articleSearch,
  }));

  readonly setClientSearch = this.updater<string>((state, clientSearch) => ({
    ...state,
    clientSearch,
  }));

  readonly setClientResults = this.updater<Client[]>((state, clientResults) => ({
    ...state,
    clientResults,
  }));

  readonly setClientResultsLoading = this.updater<boolean>((state, loading) => ({
    ...state,
    clientResultsLoading: loading,
  }));

  readonly selectClient = this.updater<Client>((state, client) => ({
    ...state,
    selectedClient: client,
    selectedReservation: null,
    activeReservations: [],
    error: null,
  }));

  readonly clearClient = this.updater((state) => ({
    ...state,
    selectedClient: null,
    selectedReservation: null,
    activeReservations: [],
    clientResults: [],
    clientSearch: '',
  }));

  /**
   * Variante "soft" : retire la sÃ©lection client/rÃ©sa courante sans toucher
   * au champ recherche ni aux rÃ©sultats. UtilisÃ©e quand l'utilisateur
   * retape dans le champ recherche alors qu'un client est dÃ©jÃ  sÃ©lectionnÃ©
   * (reset de la sÃ©lection sans interrompre la frappe en cours).
   */
  readonly deselectClient = this.updater((state) => ({
    ...state,
    selectedClient: null,
    selectedReservation: null,
    activeReservations: [],
  }));

  readonly setActiveReservations = this.updater<Reservation[]>(
    (state, activeReservations) => ({
      ...state,
      activeReservations,
    }),
  );

  readonly setReservationsLoading = this.updater<boolean>((state, loading) => ({
    ...state,
    reservationsLoading: loading,
  }));

  readonly selectReservation = this.updater<Reservation | null>(
    (state, selectedReservation) => ({
      ...state,
      selectedReservation,
      modeReglement:
        selectedReservation != null
          ? ModeReglement.REPORTE_CHAMBRE
          : ModeReglement.COMPTANT,
    }),
  );

  readonly setModeReglement = this.updater<ModeReglement>(
    (state, modeReglement) => ({
      ...state,
      modeReglement,
    }),
  );

  /**
   * Ajoute un article au panier. Si une ligne existe dÃ©jÃ  pour cet article
   * sans note de cuisine (cas standard), on incrÃ©mente sa quantitÃ©.
   * Sinon on crÃ©e une nouvelle ligne.
   */
  readonly addArticle = this.updater<ArticleMenu>((state, article) => {
    if (article.articleId == null || article.prix == null || article.prix < 0) {
      return { ...state, error: 'restaurant.pos.errors.invalidArticle' };
    }
    const existingIndex = state.cart.findIndex(
      (l) =>
        (l.type ?? 'ARTICLE') === 'ARTICLE' &&
        l.articleId === article.articleId &&
        !l.notes,
    );
    if (existingIndex >= 0) {
      const existing = state.cart[existingIndex];
      const newQuantite = existing.quantite + 1;
      const updated: LigneCommande = {
        ...existing,
        quantite: newQuantite,
        sousTotal: newQuantite * existing.prixUnitaire,
      };
      const newCart = [...state.cart];
      newCart[existingIndex] = updated;
      return { ...state, cart: newCart, error: null };
    }
    const newLine: LigneCommande = {
      cartLineId: nextCartLineId(),
      type: 'ARTICLE',
      articleId: article.articleId,
      libelle: article.nom,
      quantite: 1,
      prixUnitaire: article.prix,
      sousTotal: article.prix,
    };
    return { ...state, cart: [...state.cart, newLine], error: null };
  });

  /**
   * Ajoute un service hÃ´telier au panier (Tour 55). Comportement aligne sur
   * `addArticle` : si la ligne existe dÃ©jÃ  (mÃªme `serviceId`, pas de notes),
   * on incrÃ©mente la quantitÃ©. Sinon nouvelle ligne typÃ©e `SERVICE`.
   */
  readonly addService = this.updater<ServiceHotelier>((state, service) => {
    if (
      service.serviceId == null ||
      service.prixUnitaire == null ||
      service.prixUnitaire < 0
    ) {
      return { ...state, error: 'restaurant.pos.errors.invalidService' };
    }
    const existingIndex = state.cart.findIndex(
      (l) =>
        l.type === 'SERVICE' &&
        l.serviceId === service.serviceId &&
        !l.notes,
    );
    if (existingIndex >= 0) {
      const existing = state.cart[existingIndex];
      const newQuantite = existing.quantite + 1;
      const updated: LigneCommande = {
        ...existing,
        quantite: newQuantite,
        sousTotal: newQuantite * existing.prixUnitaire,
      };
      const newCart = [...state.cart];
      newCart[existingIndex] = updated;
      return { ...state, cart: newCart, error: null };
    }
    const newLine: LigneCommande = {
      cartLineId: nextCartLineId(),
      type: 'SERVICE',
      serviceId: service.serviceId,
      libelle: service.nomService,
      quantite: 1,
      prixUnitaire: service.prixUnitaire,
      sousTotal: service.prixUnitaire,
      unite: service.uniteMesure,
    };
    return { ...state, cart: [...state.cart, newLine], error: null };
  });

  /**
   * Retire la ligne identifiÃ©e par son `cartLineId` local.
   */
  readonly removeLine = this.updater<string>((state, cartLineId) => ({
    ...state,
    cart: state.cart.filter((l) => l.cartLineId !== cartLineId),
  }));

  /**
   * Met Ã  jour la quantitÃ© d'une ligne. QuantitÃ© â‰¤ 0 retire la ligne.
   */
  readonly updateLineQuantity = this.updater<{
    cartLineId: string;
    quantite: number;
  }>((state, { cartLineId, quantite }) => {
    if (quantite <= 0) {
      return {
        ...state,
        cart: state.cart.filter((l) => l.cartLineId !== cartLineId),
      };
    }
    const cart = state.cart.map((l) =>
      l.cartLineId === cartLineId
        ? { ...l, quantite, sousTotal: quantite * l.prixUnitaire }
        : l,
    );
    return { ...state, cart };
  });

  /**
   * Met Ã  jour la note cuisine d'une ligne.
   */
  readonly updateLineNotes = this.updater<{
    cartLineId: string;
    notes: string;
  }>((state, { cartLineId, notes }) => {
    const cart = state.cart.map((l) =>
      l.cartLineId === cartLineId ? { ...l, notes: notes || undefined } : l,
    );
    return { ...state, cart };
  });

  readonly clearCart = this.updater((state) => ({
    ...state,
    cart: [],
  }));

  readonly setError = this.updater<string | null>((state, error) => ({
    ...state,
    error,
  }));

  readonly setSuccess = this.updater<string | null>(
    (state, lastSuccessMessage) => ({
      ...state,
      lastSuccessMessage,
    }),
  );

  readonly setSubmitting = this.updater<boolean>((state, submitting) => ({
    ...state,
    submitting,
  }));

  readonly setLastCommande = this.updater<Commande | null>(
    (state, lastCommande) => ({
      ...state,
      lastCommande,
    }),
  );

  readonly setPrintingTicket = this.updater<boolean>((state, printingTicket) => ({
    ...state,
    printingTicket,
  }));

  readonly setLastTicket = this.updater<TicketDto | null>((state, lastTicket) => ({
    ...state,
    lastTicket,
  }));

  /**
   * Vide le panier + dÃ©sÃ©lectionne client/rÃ©servation aprÃ¨s checkout, MAIS
   * conserve `lastCommande` + `lastTicket` visibles pour autoriser l'impression
   * du ticket caisse / cuisine sur la commande qui vient d'Ãªtre encaissÃ©e.
   *
   * Le reset complet (effacement de `lastCommande`) se fait via
   * `startNewOrder()` â€” dÃ©clenchÃ© par le bouton Â« Nouvelle commande Â».
   */
  readonly resetAfterCheckout = this.updater((state) => ({
    ...INITIAL_STATE,
    categories: state.categories,
    // (Tour 55) Conserve les services dÃ©jÃ  chargÃ©s pour Ã©viter un rechargement.
    services: state.services,
    servicesLoaded: state.servicesLoaded,
    lastCommande: state.lastCommande,
    lastTicket: state.lastTicket,
  }));

  /**
   * Reset total : utilisÃ© pour dÃ©marrer une nouvelle commande aprÃ¨s avoir
   * imprimÃ© ou consultÃ© le ticket de la commande prÃ©cÃ©dente.
   */
  readonly startNewOrder = this.updater((state) => ({
    ...INITIAL_STATE,
    categories: state.categories,
    services: state.services,
    servicesLoaded: state.servicesLoaded,
  }));

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Effects (asynchrones)
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  /**
   * Charge les catÃ©gories actives. AppelÃ© au dÃ©marrage du POS.
   */
  readonly loadCategories = this.effect<void>((trigger$) =>
    trigger$.pipe(
      switchMap(() =>
        this.categoriesService.findActives().pipe(
          tap((categories) => this.setCategories(categories)),
          catchError(() => {
            this.setError('restaurant.pos.errors.loadCategories');
            return EMPTY;
          }),
        ),
      ),
    ),
  );

  /**
   * Charge les articles disponibles pour la catÃ©gorie courante (ou toutes
   * catÃ©gories si `selectedCategorieId` est null).
   */
  readonly loadArticles = this.effect<number | null>((categorieId$) =>
    categorieId$.pipe(
      tap(() => {
        this.setArticlesLoading(true);
        this.setArticles([]);
      }),
      switchMap((categorieId) =>
        this.articlesService.findDisponibles(categorieId ?? undefined).pipe(
          tap((articles) => {
            this.setArticles(articles);
            this.setArticlesLoading(false);
          }),
          catchError(() => {
            this.setArticles([]);
            this.setArticlesLoading(false);
            this.setError('restaurant.pos.errors.loadArticles');
            return EMPTY;
          }),
        ),
      ),
    ),
  );

  /**
   * Charge les services hÃ´teliers actifs de l'hÃ´tel courant (Tour 55).
   * AppelÃ© au premier basculement vers le mode `SERVICES` (lazy load).
   */
  readonly loadServices = this.effect<void>((trigger$) =>
    trigger$.pipe(
      tap(() => {
        this.setServicesLoading(true);
      }),
      switchMap(() =>
        this.servicesHoteliersService.findActifs().pipe(
          tap((services) => {
            this.setServices(services);
            this.setServicesLoading(false);
          }),
          catchError(() => {
            this.setServices([]);
            this.setServicesLoading(false);
            this.setError('restaurant.pos.errors.loadServices');
            return EMPTY;
          }),
        ),
      ),
    ),
  );

  /**
   * Bridge — pousse les lignes SERVICE du panier sur la facture de la
   * rÃ©servation sÃ©lectionnÃ©e (Tour 55). Pour chaque ligne SERVICE, appelle
   * l'endpoint `POST /api/finance/factures` avec `LigneFactureCreateDto`
   * typÃ©e SERVICE (cf. `LigneServiceService.addLigneService`).
   *
   * Idempotent cÃ´tÃ© backend : si la rÃ©servation a dÃ©jÃ  une facture brouillon,
   * la ligne est ajoutÃ©e ; sinon la facture est crÃ©Ã©e et la ligne posÃ©e dessus.
   *
   * Une fois toutes les lignes SERVICE poussÃ©es, on les retire du panier
   * (les ARTICLE restent pour Ãªtre encaissÃ©es / reportÃ©es sÃ©parÃ©ment).
   */
  readonly pushServicesToReservation = this.effect<void>((trigger$) =>
    trigger$.pipe(
      withLatestFrom(this.state$),
      tap(() => {
        this.setSubmitting(true);
        this.setError(null);
      }),
      switchMap(([, state]) => {
        const reservationId = state.selectedReservation?.reservationId;
        if (reservationId == null) {
          this.setSubmitting(false);
          this.setError('restaurant.pos.errors.servicesRequireReservation');
          return of(null);
        }
        const serviceLines = state.cart.filter((l) => l.type === 'SERVICE');
        if (serviceLines.length === 0) {
          this.setSubmitting(false);
          return of(null);
        }
        const requests = serviceLines
          .filter((l) => l.serviceId != null)
          .map((l) => {
            const req: AjouterLigneServiceRequest = {
              reservationId,
              serviceId: l.serviceId as number,
              quantite: l.quantite,
              prixUnitaire: l.prixUnitaire,
              libelle: l.libelle,
            };
            return this.ligneServiceService.addLigneService(req);
          });
        return forkJoin(requests).pipe(
          tap(() => {
            // Retire toutes les lignes SERVICE du panier.
            this.patchState((s) => ({
              cart: s.cart.filter((l) => l.type !== 'SERVICE'),
            }));
            this.setSuccess('restaurant.pos.messages.servicesPushed');
            this.setSubmitting(false);
          }),
          catchError(() => {
            this.setSubmitting(false);
            this.setError('restaurant.pos.errors.servicesPushFailed');
            return EMPTY;
          }),
        );
      }),
    ),
  );

  /**
   * Charge les rÃ©servations actives du client sÃ©lectionnÃ©. AppelÃ© aprÃ¨s
   * `selectClient`.
   */
  readonly loadActiveReservationsForClient = this.effect<number>((clientId$) =>
    clientId$.pipe(
      tap(() => {
        this.setReservationsLoading(true);
        this.setActiveReservations([]);
      }),
      switchMap((clientId) =>
        // Endpoint dÃ©diÃ© `/by-client/{clientId}` â€” le filtre `clientId` n'est
        // PAS supportÃ© par l'endpoint paginÃ© gÃ©nÃ©rique (cf. ReservationController
        // backend, qui ignore ce query param et retourne toutes les rÃ©sas).
        this.reservationsService.findByClient(clientId, 0, 50).pipe(
          map((page) => page.content.filter(isActiveForPos)),
          // Charge le dÃ©tail (chambres + clients) de chaque rÃ©servation active
          // pour pouvoir afficher numÃ©ro/type de chambre dans le POS.
          // Reproduit la logique de l'ancien `point-de-vente` qui interrogeait
          // `getInstanceReservationsForClient` avec les jointures.
          switchMap((reservations: Reservation[]) => {
            if (reservations.length === 0) {
              return of<Reservation[]>([]);
            }
            const details$ = reservations.map((r) =>
              r.reservationId != null
                ? this.reservationsService
                    .findById(r.reservationId)
                    .pipe(catchError(() => of(r)))
                : of(r),
            );
            return forkJoin(details$);
          }),
          tap((reservations: Reservation[]) => {
            this.setActiveReservations(reservations);
            this.setReservationsLoading(false);
          }),
          catchError(() => {
            this.setActiveReservations([]);
            this.setReservationsLoading(false);
            this.setError('restaurant.pos.errors.loadReservations');
            return EMPTY;
          }),
        ),
      ),
    ),
  );

  /**
   * Soumet la commande comptant : crÃ©e la commande puis l'encaisse en un
   * second appel (le backend orchestre la facture + paiement).
   */
  readonly submitOrderComptant = this.effect<EncaissementCommandeRequest>(
    (encaissement$) =>
      encaissement$.pipe(
        withLatestFrom(this.state$),
        tap(() => {
          this.setSubmitting(true);
          this.setError(null);
        }),
        switchMap(([encaissement, state]) => {
          const createPayload = this.toCreateRequest(state, ModeReglement.COMPTANT);
          if (!createPayload) {
            this.setSubmitting(false);
            this.setError('restaurant.pos.errors.invalidPayload');
            return of(null);
          }
          return this.commandesService.create(createPayload).pipe(
            switchMap((commande) => {
              if (commande.commandeId == null) {
                return of<Commande | null>(null);
              }
              return this.commandesService
                .encaisserComptant(commande.commandeId, encaissement)
                .pipe(catchError(() => of<Commande | null>(null)));
            }),
            tap((commande: Commande | null) => {
              if (!commande) {
                this.setSubmitting(false);
                this.setError('restaurant.pos.errors.encaissement');
                return;
              }
              this.setLastCommande(commande);
              this.setSuccess('restaurant.pos.messages.encaissementSuccess');
              this.setSubmitting(false);
              this.resetAfterCheckout();
            }),
            catchError(() => {
              this.setSubmitting(false);
              this.setError('restaurant.pos.errors.encaissement');
              return EMPTY;
            }),
          );
        }),
      ),
  );

  /**
   * Soumet la commande pour report sur chambre.
   *
   * <p><b>Doctrine Tour 50</b> : le report sur chambre est entiÃ¨rement
   * encodÃ© dans le {@code create()} de la commande via
   * {@code modeReglement = REPORTE_CHAMBRE} + {@code reservationId}. Pas
   * d'endpoint backend sÃ©parÃ© : la commande est automatiquement picorÃ©e
   * par {@code FactureServiceImpl.fromReservation} au check-out de la rÃ©sa
   * (Tour 25 â€“ "rÃ©cupere AUSSI les commandes REPORTE_CHAMBRE non facturÃ©es").</p>
   */
  readonly submitOrderReportChambre = this.effect<void>((trigger$) =>
    trigger$.pipe(
      withLatestFrom(this.state$),
      tap(() => {
        this.setSubmitting(true);
        this.setError(null);
      }),
      switchMap(([, state]) => {
        const reservationId = state.selectedReservation?.reservationId;
        if (reservationId == null) {
          this.setSubmitting(false);
          this.setError('restaurant.pos.errors.reservationRequired');
          return of(null);
        }
        const createPayload = this.toCreateRequest(
          state,
          ModeReglement.REPORTE_CHAMBRE,
        );
        if (!createPayload) {
          this.setSubmitting(false);
          this.setError('restaurant.pos.errors.invalidPayload');
          return of(null);
        }
        return this.commandesService.create(createPayload).pipe(
          tap((commande: Commande) => {
            this.setLastCommande(commande);
            this.setSuccess('restaurant.pos.messages.reportSuccess');
            this.setSubmitting(false);
            this.resetAfterCheckout();
          }),
          catchError(() => {
            this.setSubmitting(false);
            this.setError('restaurant.pos.errors.reportChambre');
            return EMPTY;
          }),
        );
      }),
    ),
  );

  /**
   * Imprime un ticket caisse (PDF base64 retournÃ© par le backend) pour une
   * commande donnÃ©e. Ouvre une fenÃªtre d'impression. Aucun changement d'Ã©tat
   * panier â€” c'est une action transversale sur `lastCommande`.
   */
  readonly imprimerTicketCaisse = this.effect<number>((commandeId$) =>
    commandeId$.pipe(
      tap(() => {
        this.setPrintingTicket(true);
        this.setError(null);
      }),
      switchMap((commandeId) =>
        this.ticketsService.imprimerCaisse(commandeId).pipe(
          tap((ticket) => {
            this.setLastTicket(ticket);
            this.setPrintingTicket(false);
            this.openTicketPdf(ticket);
          }),
          catchError(() => {
            this.setPrintingTicket(false);
            this.setError('restaurant.pos.errors.ticketCaisse');
            return EMPTY;
          }),
        ),
      ),
    ),
  );

  /**
   * Imprime un ticket cuisine (bon de fabrication) pour une commande.
   */
  readonly imprimerTicketCuisine = this.effect<number>((commandeId$) =>
    commandeId$.pipe(
      tap(() => {
        this.setPrintingTicket(true);
        this.setError(null);
      }),
      switchMap((commandeId) =>
        this.ticketsService.imprimerCuisine(commandeId).pipe(
          tap((ticket) => {
            this.setLastTicket(ticket);
            this.setPrintingTicket(false);
            this.openTicketPdf(ticket);
          }),
          catchError(() => {
            this.setPrintingTicket(false);
            this.setError('restaurant.pos.errors.ticketCuisine');
            return EMPTY;
          }),
        ),
      ),
    ),
  );

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Helpers privÃ©s
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  /**
   * Ouvre le PDF base64 du ticket dans une nouvelle fenÃªtre et dÃ©clenche
   * l'impression. Reproduit le pattern de l'ancienne implÃ©mentation
   * (`printInvoice()` jsPDF) mais en s'appuyant sur le PDF gÃ©nÃ©rÃ© cÃ´tÃ©
   * serveur, pour ne pas dupliquer la mise en forme entÃªte / pied de page.
   */
  private openTicketPdf(ticket: TicketDto): void {
    if (!ticket.pdfBase64) {
      return;
    }
    try {
      const byteCharacters = atob(ticket.pdfBase64);
      const byteNumbers = new Array(byteCharacters.length);
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i);
      }
      const blob = new Blob([new Uint8Array(byteNumbers)], {
        type: 'application/pdf',
      });
      const blobUrl = URL.createObjectURL(blob);
      const w = window.open(blobUrl, '_blank');
      if (w) {
        // Laisse le navigateur charger le PDF puis dÃ©clenche l'impression.
        w.addEventListener('load', () => {
          try {
            w.print();
          } catch {
            /* l'utilisateur peut imprimer manuellement depuis la fenÃªtre */
          }
        });
      }
      // LibÃ¨re l'URL aprÃ¨s 60 s â€” laisse le temps au rendu et Ã  l'impression.
      setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000);
    } catch {
      this.setError('restaurant.pos.errors.ticketDecode');
    }
  }

  /**
   * Construit le payload `CreerCommandeRequest` Ã  partir de l'Ã©tat panier
   * courant. Retourne `null` si l'Ã©tat n'est pas valide pour soumission.
   */
  private toCreateRequest(
    state: PosState,
    modeReglement: ModeReglement,
  ): CreerCommandeRequest | null {
    if (state.selectedClient == null) {
      return null;
    }
    // (Tour 55) Seules les lignes ARTICLE entrent dans la commande POS â€”
    // les lignes SERVICE sont facturÃ©es Ã  part via `pushServicesToReservation`.
    const articleLines = state.cart.filter(
      (l) => (l.type ?? 'ARTICLE') === 'ARTICLE' && l.articleId != null,
    );
    if (articleLines.length === 0) {
      return null;
    }
    const lignes: CreerLigneCommandeRequest[] = articleLines.map((l) => ({
      articleId: l.articleId as number,
      quantite: l.quantite,
      prixUnitaire: l.prixUnitaire,
      notes: l.notes,
    }));
    const payload: CreerCommandeRequest = {
      clientId: state.selectedClient.clientId,
      modeReglement,
      lignes,
    };
    // Backend (CommandeServiceImpl.java:151) rejette REPORTE_CHAMBRE sans
    // reservationId (error.commande.reservation.required) ET inversement
    // rejette COMPTANT avec reservationId (error.commande.reservation.interditComptant).
    if (modeReglement === ModeReglement.REPORTE_CHAMBRE) {
      const reservationId = state.selectedReservation?.reservationId;
      if (reservationId == null) {
        return null;
      }
      payload.reservationId = reservationId;
    }
    return payload;
  }

  /**
   * Permet aux composants enfants (ex. `client-search`) de dÃ©clencher la
   * recherche client en injectant le service `ClientsService` cÃ´tÃ© composant
   * (le store n'en a pas besoin). Voir `PosComponent.search()`.
   *
   * ExposÃ© en tant que mÃ©thode pour dÃ©couplage.
   */
  applyClientResults(results: Observable<Client[]>): void {
    this.setClientResultsLoading(true);
    results
      .pipe(
        tap((clients) => {
          this.setClientResults(clients);
          this.setClientResultsLoading(false);
        }),
        catchError(() => {
          this.setClientResults([]);
          this.setClientResultsLoading(false);
          this.setError('restaurant.pos.errors.searchClients');
          return EMPTY;
        }),
      )
      .subscribe();
  }
}
