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
import { ClientSearchComponent } from './components/pos/client-search/client-search.component';
import { PaymentModalComponent } from './components/pos/payment-modal/payment-modal.component';
import { PosComponent } from './components/pos/pos.component';
import { RestaurantRoutingModule } from './restaurant-routing.module';

/**
 * Module feature `restaurant` — chargé en lazy depuis `app-routing.module.ts`.
 *
 * Convention NgModule (cf. cityfrontend/CLAUDE.md §1) : `standalone: false`
 * sur les composants déclarés.
 *
 * `TranslateModule.forChild()` rejoint la chaîne de loaders ngx-translate
 * mise en place dans `CoreModule`. Pas besoin de réinjecter le `TranslateLoader`.
 *
 * Périmètre Tour 23 — CATALOGUE :
 *  - articles-list + article-form (CRUD articles du menu)
 *  - categories-list + category-form (CRUD catégories)
 *
 * Périmètre Tour 24 — POS avancé (NgRx Component Store local) :
 *  - pos (point d'entrée, 3 zones)
 *  - pos/client-search (recherche client + réservations actives)
 *  - pos/article-grid (grille tactile)
 *  - pos/cart (panier + actions)
 *  - pos/payment-modal (encaissement comptant)
 *  - PosStore est `provided` au niveau du `PosComponent`, pas globalement.
 *
 * Périmètre différé (Tour 25+) :
 *  - commandes-list + commande-form (suivi cuisine, réimpressions)
 */
@NgModule({
  declarations: [
    CategoriesListComponent,
    CategoryFormComponent,
    ArticlesListComponent,
    ArticleFormComponent,
    PosComponent,
    ClientSearchComponent,
    ArticleGridComponent,
    CartComponent,
    PaymentModalComponent,
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
