import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { TranslateModule } from '@ngx-translate/core';

import { AjouterServiceFactureComponent } from './components/ajouter-service-facture/ajouter-service-facture.component';
import { BonCommandeDetailComponent } from './components/bon-commande-detail/bon-commande-detail.component';
import { BonCommandeFormComponent } from './components/bon-commande-form/bon-commande-form.component';
import { BonSortieDetailComponent } from './components/bon-sortie-detail/bon-sortie-detail.component';
import { BonSortieFormComponent } from './components/bon-sortie-form/bon-sortie-form.component';
import { BonsCommandeListComponent } from './components/bons-commande-list/bons-commande-list.component';
import { BonsSortieListComponent } from './components/bons-sortie-list/bons-sortie-list.component';
import { CategorieDetailComponent } from './components/categorie-detail/categorie-detail.component';
import { CategorieFormComponent } from './components/categorie-form/categorie-form.component';
import { CategoriesListComponent } from './components/categories-list/categories-list.component';
import { FournisseurDetailComponent } from './components/fournisseur-detail/fournisseur-detail.component';
import { FournisseurFormComponent } from './components/fournisseur-form/fournisseur-form.component';
import { FournisseursListComponent } from './components/fournisseurs-list/fournisseurs-list.component';
import { MouvementsStockListComponent } from './components/mouvements-stock-list/mouvements-stock-list.component';
import { ProduitDetailComponent } from './components/produit-detail/produit-detail.component';
import { ProduitFormComponent } from './components/produit-form/produit-form.component';
import { ProduitsListComponent } from './components/produits-list/produits-list.component';
import { ServiceHotelierDetailComponent } from './components/service-hotelier-detail/service-hotelier-detail.component';
import { ServiceHotelierFormComponent } from './components/service-hotelier-form/service-hotelier-form.component';
import { ServicesHoteliersListComponent } from './components/services-hoteliers-list/services-hoteliers-list.component';
import { StocksListComponent } from './components/stocks-list/stocks-list.component';
import { TypeServiceHotelierDetailComponent } from './components/type-service-hotelier-detail/type-service-hotelier-detail.component';
import { TypeServiceHotelierFormComponent } from './components/type-service-hotelier-form/type-service-hotelier-form.component';
import { TypesServicesHoteliersListComponent } from './components/types-services-hoteliers-list/types-services-hoteliers-list.component';
import { InventoryRoutingModule } from './inventory-routing.module';

/**
 * Module feature `inventory` — chargé en lazy depuis `app-routing.module.ts`.
 *
 * Convention NgModule (cf. cityfrontend/CLAUDE.md §1) : `standalone: false`
 * sur les composants déclarés.
 *
 * Tour 51 : ajout du CRUD complet (catégories, fournisseurs, bons de sortie,
 * mouvements de stock) + Phase A (services hôteliers et types).
 * Tour 51bis : ajout des vues détail read-only par entité + transitions
 * statut sur bons-sortie/commande + bridge ServiceHotelier↔LigneFacture.
 */
@NgModule({
  declarations: [
    ProduitsListComponent,
    ProduitFormComponent,
    ProduitDetailComponent,
    BonsCommandeListComponent,
    BonCommandeFormComponent,
    BonCommandeDetailComponent,
    StocksListComponent,
    CategoriesListComponent,
    CategorieFormComponent,
    CategorieDetailComponent,
    FournisseursListComponent,
    FournisseurFormComponent,
    FournisseurDetailComponent,
    BonsSortieListComponent,
    BonSortieFormComponent,
    BonSortieDetailComponent,
    MouvementsStockListComponent,
    ServicesHoteliersListComponent,
    ServiceHotelierFormComponent,
    ServiceHotelierDetailComponent,
    TypesServicesHoteliersListComponent,
    TypeServiceHotelierFormComponent,
    TypeServiceHotelierDetailComponent,
    AjouterServiceFactureComponent,
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
