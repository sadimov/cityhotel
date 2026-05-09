import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { ArticleFormComponent } from './components/article-form/article-form.component';
import { ArticlesListComponent } from './components/articles-list/articles-list.component';
import { CategoriesListComponent } from './components/categories-list/categories-list.component';
import { CategoryFormComponent } from './components/category-form/category-form.component';
import { PosComponent } from './components/pos/pos.component';

/**
 * Routes du module `restaurant`.
 *
 * `AuthGuard` est appliqué au niveau parent (`app-routing.module.ts`).
 * `RoleGuard` filtre finement par action :
 *  - listes / lecture : SUPERADMIN + ADMIN + GERANT + RECEPTION + RESREC + RESTAURANT
 *  - écriture : SUPERADMIN + ADMIN + GERANT + RESTAURANT
 *  - POS : SUPERADMIN + ADMIN + GERANT + RESTAURANT + RESREC
 *    (RESREC = réception/restaurant, peut encaisser au comptoir)
 *
 * Les rôles RECEPTION / RESREC ont la lecture du catalogue car ils consultent
 * le menu pour les commandes en chambre (cf. `roles_utilisateurs.txt`).
 *
 * Périmètre :
 *  - Tour 23 : catalogue (articles, categories)
 *  - Tour 24 : POS avancé (`/restaurant/pos`)
 *  - Tour 25+ : `/restaurant/commandes` (suivi cuisine — différé)
 */
const routes: Routes = [
  { path: '', redirectTo: 'articles', pathMatch: 'full' },

  // POS Restaurant (Tour 24)
  {
    path: 'pos',
    component: PosComponent,
    canActivate: [RoleGuard],
    data: {
      roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RESTAURANT', 'RESREC'],
    },
  },

  // Articles du menu
  {
    path: 'articles',
    component: ArticlesListComponent,
    canActivate: [RoleGuard],
    data: {
      roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'RESTAURANT'],
    },
  },
  {
    path: 'articles/new',
    component: ArticleFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RESTAURANT'] },
  },
  {
    path: 'articles/:id',
    component: ArticleFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RESTAURANT'] },
  },

  // Catégories de menu
  {
    path: 'categories',
    component: CategoriesListComponent,
    canActivate: [RoleGuard],
    data: {
      roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'RESTAURANT'],
    },
  },
  {
    path: 'categories/new',
    component: CategoryFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RESTAURANT'] },
  },
  {
    path: 'categories/:id',
    component: CategoryFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RESTAURANT'] },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class RestaurantRoutingModule {}
