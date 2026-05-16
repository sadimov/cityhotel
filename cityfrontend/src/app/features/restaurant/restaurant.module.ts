import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { TranslateModule } from '@ngx-translate/core';

import { ArticleFormComponent } from './components/article-form/article-form.component';
import { ArticlesListComponent } from './components/articles-list/articles-list.component';
import { CategoriesListComponent } from './components/categories-list/categories-list.component';
import { CategoryFormComponent } from './components/category-form/category-form.component';
import { ArticleGridComponent } from './components/pos/article-grid/article-grid.component';
import { CartComponent } from './components/pos/cart/cart.component';
import { ClientHeaderComponent } from './components/pos/client-header/client-header.component';
import { ClientSearchComponent } from './components/pos/client-search/client-search.component';
import { PosComponent } from './components/pos/pos.component';
import { RestaurantRoutingModule } from './restaurant-routing.module';

/**
 * Module feature `restaurant` — chargé en lazy depuis `app-routing.module.ts`.
 *
 * Convention NgModule (cf. cityfrontend/CLAUDE.md §1) : `standalone: false`
 * sur les composants déclarés.
 *
 * `TranslateModule.forChild()` rejoint la chaîne de loaders ngx-translate
 * mise en place dans `CoreModule`.
 *
 * Périmètre Tour 23 — CATALOGUE :
 *  - articles-list + article-form (CRUD articles du menu)
 *  - categories-list + category-form (CRUD catégories)
 *
 * Périmètre Tour 24 — POS avancé (NgRx Component Store local) :
 *  - pos (point d'entrée, layout 2 zones plein-écran)
 *  - pos/client-search (MODALE sélection client + réservation, 2 onglets)
 *  - pos/article-grid (grille tactile + chips catégorie)
 *  - pos/cart (panier + paiement inline + actions)
 *  - PosStore est `provided` au niveau du `PosComponent`, pas globalement.
 *
 * Note Tour 54 : `PaymentModalComponent` historique retiré au profit du
 * panneau paiement inline intégré dans `CartComponent` (cohérence POS
 * moderne — un seul écran encaissement, pas de modale flottante).
 */
@NgModule({
  declarations: [
    CategoriesListComponent,
    CategoryFormComponent,
    ArticlesListComponent,
    ArticleFormComponent,
    PosComponent,
    ClientSearchComponent,
    ClientHeaderComponent,
    ArticleGridComponent,
    CartComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RestaurantRoutingModule,
    TranslateModule.forChild(),
  ],
})
export class RestaurantModule {}
