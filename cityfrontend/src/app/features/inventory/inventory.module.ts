import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { TranslateModule } from '@ngx-translate/core';

import { BonCommandeFormComponent } from './components/bon-commande-form/bon-commande-form.component';
import { BonsCommandeListComponent } from './components/bons-commande-list/bons-commande-list.component';
import { ProduitFormComponent } from './components/produit-form/produit-form.component';
import { ProduitsListComponent } from './components/produits-list/produits-list.component';
import { StocksListComponent } from './components/stocks-list/stocks-list.component';
import { InventoryRoutingModule } from './inventory-routing.module';

/**
 * Module feature `inventory` — chargé en lazy depuis `app-routing.module.ts`.
 *
 * Convention NgModule (cf. cityfrontend/CLAUDE.md §1) : `standalone: false`
 * sur les composants déclarés.
 *
 * `TranslateModule.forChild()` rejoint la chaîne de loaders ngx-translate
 * mise en place dans `CoreModule`. Pas besoin de réinjecter le `TranslateLoader`.
 *
 * Périmètre Tour 16 — minimum exigé par le brief :
 *  - produits-list + produit-form (CRUD produits)
 *  - bons-commande-list + bon-commande-form (FormArray lignes)
 *  - stocks-list (lecture seule)
 *
 * Composants différés (TODO tour suivant) : categories-list/form,
 * fournisseurs-list/form, bons-sortie-list/form, mouvements-stock,
 * dashboard, alertes-stock, ajustement-stock, reception-marchandise.
 */
@NgModule({
  declarations: [
    ProduitsListComponent,
    ProduitFormComponent,
    BonsCommandeListComponent,
    BonCommandeFormComponent,
    StocksListComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    InventoryRoutingModule,
    TranslateModule.forChild(),
  ],
})
export class InventoryModule {}
