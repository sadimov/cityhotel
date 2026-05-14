import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
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

/**
 * Routes du module `inventory`.
 *
 * `AuthGuard` est appliqué au niveau parent (`app-routing.module.ts` →
 * route `inventory`). `RoleGuard` filtre finement par action :
 *  - listes / lecture stocks : MAGASIN + GERANT + ADMIN + SUPERADMIN
 *  - écriture produits / services / types : ADMIN + GERANT + SUPERADMIN
 *  - bons de commande / sortie : ADMIN + GERANT + MAGASIN + SUPERADMIN
 *
 * Tour 51 — extension du CRUD complet : catégories, fournisseurs,
 * bons de sortie, mouvements de stock, services hôteliers, types.
 * Tour 51bis — vues détail read-only par entité + bridge service-facture.
 */
const READ_ROLES = ['SUPERADMIN', 'ADMIN', 'GERANT', 'MAGASIN'];
const WRITE_ROLES = ['SUPERADMIN', 'ADMIN', 'GERANT'];
const STOCK_ROLES = ['SUPERADMIN', 'ADMIN', 'GERANT', 'MAGASIN'];
const BRIDGE_SERVICE_ROLES = ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION'];

const routes: Routes = [
  { path: '', redirectTo: 'produits', pathMatch: 'full' },

  // Produits
  { path: 'produits', component: ProduitsListComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'produits/new', component: ProduitFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },
  { path: 'produits/:id/view', component: ProduitDetailComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'produits/:id', component: ProduitFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },

  // Catégories
  { path: 'categories', component: CategoriesListComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'categories/new', component: CategorieFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },
  { path: 'categories/:id/view', component: CategorieDetailComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'categories/:id', component: CategorieFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },

  // Fournisseurs
  { path: 'fournisseurs', component: FournisseursListComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'fournisseurs/new', component: FournisseurFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },
  { path: 'fournisseurs/:id/view', component: FournisseurDetailComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'fournisseurs/:id', component: FournisseurFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },

  // Bons de commande
  { path: 'bons-commande', component: BonsCommandeListComponent, canActivate: [RoleGuard], data: { roles: STOCK_ROLES } },
  { path: 'bons-commande/new', component: BonCommandeFormComponent, canActivate: [RoleGuard], data: { roles: STOCK_ROLES } },
  { path: 'bons-commande/:id/view', component: BonCommandeDetailComponent, canActivate: [RoleGuard], data: { roles: STOCK_ROLES } },
  { path: 'bons-commande/:id', component: BonCommandeFormComponent, canActivate: [RoleGuard], data: { roles: STOCK_ROLES } },

  // Bons de sortie
  { path: 'bons-sortie', component: BonsSortieListComponent, canActivate: [RoleGuard], data: { roles: STOCK_ROLES } },
  { path: 'bons-sortie/new', component: BonSortieFormComponent, canActivate: [RoleGuard], data: { roles: STOCK_ROLES } },
  { path: 'bons-sortie/:id/view', component: BonSortieDetailComponent, canActivate: [RoleGuard], data: { roles: STOCK_ROLES } },
  { path: 'bons-sortie/:id', component: BonSortieFormComponent, canActivate: [RoleGuard], data: { roles: STOCK_ROLES } },

  // Stocks (lecture seule)
  { path: 'stocks', component: StocksListComponent, canActivate: [RoleGuard], data: { roles: STOCK_ROLES } },

  // Mouvements de stock (lecture seule)
  { path: 'mouvements-stock', component: MouvementsStockListComponent, canActivate: [RoleGuard], data: { roles: STOCK_ROLES } },

  // Services hôteliers (Tour 51 Phase A)
  // Bridge ServiceHotelier ↔ LigneFacture (Tour 51bis) — DOIT précéder /:id
  { path: 'services/ajouter-a-facture', component: AjouterServiceFactureComponent, canActivate: [RoleGuard], data: { roles: BRIDGE_SERVICE_ROLES } },
  { path: 'services-hoteliers', component: ServicesHoteliersListComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'services-hoteliers/new', component: ServiceHotelierFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },
  { path: 'services-hoteliers/:id/view', component: ServiceHotelierDetailComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'services-hoteliers/:id', component: ServiceHotelierFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },

  // Types de services hôteliers (Tour 51 Phase A)
  { path: 'types-services-hoteliers', component: TypesServicesHoteliersListComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'types-services-hoteliers/new', component: TypeServiceHotelierFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },
  { path: 'types-services-hoteliers/:id/view', component: TypeServiceHotelierDetailComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'types-services-hoteliers/:id', component: TypeServiceHotelierFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class InventoryRoutingModule {}
