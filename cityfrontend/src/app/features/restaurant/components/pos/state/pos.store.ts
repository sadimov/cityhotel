import { Injectable } from '@angular/core';
import { ComponentStore } from '@ngrx/component-store';
import { EMPTY, Observable, of } from 'rxjs';
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
import { ArticleMenu } from '../../../models/article-menu.model';
import { CategorieMenu } from '../../../models/categorie-menu.model';
import {
  Commande,
  CreerCommandeRequest,
  CreerLigneCommandeRequest,
  EncaissementCommandeRequest,
  LigneCommande,
  ModeReglement,
  ReportChambreRequest,
} from '../../../models/commande.model';
import { ArticlesMenusService } from '../../../services/articles-menus.service';
import { CategoriesMenusService } from '../../../services/categories-menus.service';
import { CommandesService } from '../../../services/commandes.service';

/**
 * Étapes du workflow POS — pilote l'affichage de la zone active.
 *
 *  - `SELECT_CLIENT` : recherche/sélection client + (optionnel) réservation.
 *  - `SELECT_ARTICLES` : grille articles + édition panier.
 *  - `PAYMENT` : modal de paiement ouvert.
 */
export enum PosStep {
  SELECT_CLIENT = 'SELECT_CLIENT',
  SELECT_ARTICLES = 'SELECT_ARTICLES',
  PAYMENT = 'PAYMENT',
}

/**
 * État du POS — un seul `ComponentStore` local au composant `PosComponent`,
 * détruit avec lui (pas de pollution du store global NgRx).
 *
 * Conventions :
 *  - immutabilité stricte (toujours retourner un nouvel objet dans les
 *    updaters — runtimeChecks NgRx hérités du store global le vérifient
 *    en dev).
 *  - `error` / `lastSuccessMessage` sont des **clés i18n** ; le composant
 *    les passe à `TranslateService.instant()` pour le rendu visuel.
 */
export interface PosState {
  step: PosStep;

  // Catalogue
  categories: CategorieMenu[];
  selectedCategorieId: number | null;
  articles: ArticleMenu[];
  articlesLoading: boolean;
  articleSearch: string;

  // Client + réservation
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

  // États globaux
  submitting: boolean;
  /** Clé i18n d'erreur courante (ex. `restaurant.pos.errors.cartEmpty`). */
  error: string | null;
  /** Clé i18n de succès dernière action. */
  lastSuccessMessage: string | null;
  /** Dernière commande créée (utilisée pour navigation post-paiement). */
  lastCommande: Commande | null;
}

const INITIAL_STATE: PosState = {
  step: PosStep.SELECT_CLIENT,
  categories: [],
  selectedCategorieId: null,
  articles: [],
  articlesLoading: false,
  articleSearch: '',
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
};

/**
 * Génère un identifiant local pour une ligne panier. Pas besoin de
 * cryptographie — un compteur monotone suffit (le store est local et
 * détruit avec le composant).
 */
let cartLineCounter = 0;
function nextCartLineId(): string {
  cartLineCounter += 1;
  return `cl_${cartLineCounter}`;
}

/**
 * Détermine si une réservation est "active" pour le POS (le client peut
 * imputer la commande dessus). Critères :
 *  - statut ARRIVEE (client séjourne actuellement)
 *  - OU statut CONFIRMEE et la date courante est dans la plage du séjour.
 */
function isActiveForPos(reservation: Reservation): boolean {
  if (reservation.statut === StatutReservation.ARRIVEE) {
    return true;
  }
  if (reservation.statut !== StatutReservation.CONFIRMEE) {
    return false;
  }
  if (!reservation.dateArrivee || !reservation.dateDepart) {
    return false;
  }
  const now = new Date();
  const start = new Date(reservation.dateArrivee);
  const end = new Date(reservation.dateDepart);
  return start <= now && now <= end;
}

/**
 * NgRx Component Store local du POS Restaurant.
 *
 * Provided au niveau du composant `PosComponent` (cf. `providers: [PosStore]`)
 * pour que son cycle de vie soit lié à celui du composant.
 */
@Injectable()
export class PosStore extends ComponentStore<PosState> {
  constructor(
    private readonly categoriesService: CategoriesMenusService,
    private readonly articlesService: ArticlesMenusService,
    private readonly reservationsService: ReservationsService,
    private readonly commandesService: CommandesService,
  ) {
    super(INITIAL_STATE);
  }

  // ──────────────────────────────────────────────────────────────────────
  // Selectors
  // ──────────────────────────────────────────────────────────────────────

  readonly step$ = this.select((s) => s.step);
  readonly categories$ = this.select((s) => s.categories);
  readonly selectedCategorieId$ = this.select((s) => s.selectedCategorieId);
  readonly articles$ = this.select((s) => s.articles);
  readonly articlesLoading$ = this.select((s) => s.articlesLoading);
  readonly articleSearch$ = this.select((s) => s.articleSearch);

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

  /** Total panier en MRU (somme des sous-totaux). */
  readonly total$ = this.select(this.cart$, (cart) =>
    cart.reduce((sum, line) => sum + line.sousTotal, 0),
  );

  /** Nombre total d'articles (somme des quantités). */
  readonly itemsCount$ = this.select(this.cart$, (cart) =>
    cart.reduce((sum, line) => sum + line.quantite, 0),
  );

  /**
   * Vrai si le panier est vide.
   */
  readonly isCartEmpty$ = this.select(this.cart$, (cart) => cart.length === 0);

  /**
   * Vrai si la commande peut être encaissée comptant. Conditions :
   *  - panier non vide ;
   *  - client sélectionné (un POS sans client est interdit en V1).
   */
  readonly canCheckoutComptant$ = this.select(
    this.cart$,
    this.selectedClient$,
    this.submitting$,
    (cart, client, submitting) =>
      cart.length > 0 && client != null && !submitting,
  );

  /**
   * Vrai si la commande peut être reportée sur chambre. Conditions :
   *  - panier non vide ;
   *  - client sélectionné ;
   *  - réservation active sélectionnée.
   */
  readonly canReportChambre$ = this.select(
    this.cart$,
    this.selectedClient$,
    this.selectedReservation$,
    this.submitting$,
    (cart, client, reservation, submitting) =>
      cart.length > 0 &&
      client != null &&
      reservation != null &&
      reservation.reservationId != null &&
      !submitting,
  );

  // ──────────────────────────────────────────────────────────────────────
  // Updaters (synchrones)
  // ──────────────────────────────────────────────────────────────────────

  readonly setStep = this.updater<PosStep>((state, step) => ({
    ...state,
    step,
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
   * Ajoute un article au panier. Si une ligne existe déjà pour cet article
   * sans note de cuisine (cas standard), on incrémente sa quantité.
   * Sinon on crée une nouvelle ligne.
   */
  readonly addArticle = this.updater<ArticleMenu>((state, article) => {
    if (article.articleId == null || article.prix == null || article.prix < 0) {
      return { ...state, error: 'restaurant.pos.errors.invalidArticle' };
    }
    const existingIndex = state.cart.findIndex(
      (l) => l.articleId === article.articleId && !l.notes,
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
      articleId: article.articleId,
      libelle: article.nomArticle,
      quantite: 1,
      prixUnitaire: article.prix,
      sousTotal: article.prix,
    };
    return { ...state, cart: [...state.cart, newLine], error: null };
  });

  /**
   * Retire la ligne identifiée par son `cartLineId` local.
   */
  readonly removeLine = this.updater<string>((state, cartLineId) => ({
    ...state,
    cart: state.cart.filter((l) => l.cartLineId !== cartLineId),
  }));

  /**
   * Met à jour la quantité d'une ligne. Quantité ≤ 0 retire la ligne.
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
   * Met à jour la note cuisine d'une ligne.
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

  /**
   * Réinitialise totalement l'état après une commande clôturée — prêt pour
   * la prochaine vente. Conserve les catégories chargées.
   */
  readonly resetAfterCheckout = this.updater((state) => ({
    ...INITIAL_STATE,
    categories: state.categories,
  }));

  // ──────────────────────────────────────────────────────────────────────
  // Effects (asynchrones)
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Charge les catégories actives. Appelé au démarrage du POS.
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
   * Charge les articles disponibles pour la catégorie courante (ou toutes
   * catégories si `selectedCategorieId` est null).
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
   * Charge les réservations actives du client sélectionné. Appelé après
   * `selectClient`.
   */
  readonly loadActiveReservationsForClient = this.effect<number>((clientId$) =>
    clientId$.pipe(
      tap(() => {
        this.setReservationsLoading(true);
        this.setActiveReservations([]);
      }),
      switchMap((clientId) =>
        this.reservationsService.page({ clientId }, 0, 50).pipe(
          map((page) => page.content.filter(isActiveForPos)),
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
   * Soumet la commande comptant : crée la commande puis l'encaisse en un
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
   * Soumet la commande pour report sur chambre. Crée la commande puis
   * appelle `/reporter-chambre` avec la réservation sélectionnée.
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
        const reportPayload: ReportChambreRequest = { reservationId };
        return this.commandesService.create(createPayload).pipe(
          switchMap((commande) => {
            if (commande.commandeId == null) {
              return of<Commande | null>(null);
            }
            return this.commandesService
              .reporterSurChambre(commande.commandeId, reportPayload)
              .pipe(catchError(() => of<Commande | null>(null)));
          }),
          tap((commande: Commande | null) => {
            if (!commande) {
              this.setSubmitting(false);
              this.setError('restaurant.pos.errors.reportChambre');
              return;
            }
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

  // ──────────────────────────────────────────────────────────────────────
  // Helpers privés
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Construit le payload `CreerCommandeRequest` à partir de l'état panier
   * courant. Retourne `null` si l'état n'est pas valide pour soumission.
   */
  private toCreateRequest(
    state: PosState,
    modeReglement: ModeReglement,
  ): CreerCommandeRequest | null {
    if (state.cart.length === 0 || state.selectedClient == null) {
      return null;
    }
    const lignes: CreerLigneCommandeRequest[] = state.cart.map((l) => ({
      articleId: l.articleId,
      quantite: l.quantite,
      prixUnitaire: l.prixUnitaire,
      notes: l.notes,
    }));
    return {
      clientId: state.selectedClient.clientId,
      modeReglement,
      lignes,
    };
  }

  /**
   * Permet aux composants enfants (ex. `client-search`) de déclencher la
   * recherche client en injectant le service `ClientsService` côté composant
   * (le store n'en a pas besoin). Voir `PosComponent.search()`.
   *
   * Exposé en tant que méthode pour découplage.
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
