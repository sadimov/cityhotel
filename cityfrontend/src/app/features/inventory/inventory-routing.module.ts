import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { BonCommandeFormComponent } from './components/bon-commande-form/bon-commande-form.component';
import { BonsCommandeListComponent } from './components/bons-commande-list/bons-commande-list.component';
import { ProduitFormComponent } from './components/produit-form/produit-form.component';
import { ProduitsListComponent } from './components/produits-list/produits-list.component';
import { StocksListComponent } from './components/stocks-list/stocks-list.component';

/**
 * Routes du module `inventory`.
 *
 * `AuthGuard` est appliqué au niveau parent (`app-routing.module.ts` →
 * route `inventory`). `RoleGuard` filtre finement par action :
 *  - listes / lecture stocks : MAGASIN + GERANT + ADMIN + SUPERADMIN
 *  - écriture produits : ADMIN + GERANT + SUPERADMIN
 *  - bons de commande : ADMIN + GERANT + MAGASIN + SUPERADMIN
 *
 * Cf. `roles_utilisateurs.txt` racine + `Prompts_Backend_Frontend.txt` (sidebar).
 *
 * Note Tour 16 : on intègre uniquement le périmètre minimum exigé par le
 * brief (produits-list/form, bons-commande-list/form, stocks-list). Les
 * routes `categories`, `fournisseurs`, `bons-sortie` et `mouvements-stock`
 * sont différées (voir TODO du rapport de tour).
 */
const routes: Routes = [
  { path: '', redirectTo: 'produits', pathMatch: 'full' },

  // Produits
  {
    path: 'produits',
    component: ProduitsListComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'MAGASIN'] },
  },
  {
    path: 'produits/new',
    component: ProduitFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] },
  },
  {
    path: 'produits/:id',
    component: ProduitFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] },
  },

  // Bons de commande
  {
    path: 'bons-commande',
    component: BonsCommandeListComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'MAGASIN'] },
  },
  {
    path: 'bons-commande/new',
    component: BonCommandeFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'MAGASIN'] },
  },
  {
    path: 'bons-commande/:id',
    component: BonCommandeFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'MAGASIN'] },
  },

  // Stocks (lecture seule)
  {
    path: 'stocks',
    component: StocksListComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'MAGASIN'] },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class InventoryRoutingModule {}
