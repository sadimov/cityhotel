import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { TranslateModule } from '@ngx-translate/core';

import { BalanceViewerComponent } from './components/balance/balance-viewer/balance-viewer.component';
import { BilanViewerComponent } from './components/bilan/bilan-viewer/bilan-viewer.component';
import { CompteMappingListComponent } from './components/compte-mapping/compte-mapping-list/compte-mapping-list.component';
import { CompteResultatViewerComponent } from './components/compte-resultat/compte-resultat-viewer/compte-resultat-viewer.component';
import { DeclarationTvaFormComponent } from './components/declarations-tva/declaration-tva-form/declaration-tva-form.component';
import { DeclarationsTvaListComponent } from './components/declarations-tva/declarations-tva-list/declarations-tva-list.component';
import { EcritureDetailComponent } from './components/ecritures/ecriture-detail/ecriture-detail.component';
import { EcritureFormComponent } from './components/ecritures/ecriture-form/ecriture-form.component';
import { EcrituresListComponent } from './components/ecritures/ecritures-list/ecritures-list.component';
import { ExercicesListComponent } from './components/exercices/exercices-list/exercices-list.component';
import { GrandLivreViewerComponent } from './components/grand-livre/grand-livre-viewer/grand-livre-viewer.component';
import { JournalFormComponent } from './components/journaux/journal-form/journal-form.component';
import { JournauxListComponent } from './components/journaux/journaux-list/journaux-list.component';
import { JournalViewerComponent } from './components/journal/journal-viewer/journal-viewer.component';
import { PlanComptableListComponent } from './components/plan-comptable/plan-comptable-list/plan-comptable-list.component';
import { TvaConfigListComponent } from './components/tva-config/tva-config-list/tva-config-list.component';
import { ComptabiliteRoutingModule } from './comptabilite-routing.module';

/**
 * Module feature `comptabilite` (B7, 2026-05-08).
 *
 * Lazy-loaded depuis `app-routing.module.ts` sur le chemin `/comptabilite`.
 * Architecture NgModule (cf. cityfrontend/CLAUDE.md §1) : `standalone: false`
 * sur les composants déclarés.
 *
 * Convention back : les services consomment des DTOs directs (pas
 * d'enveloppe ApiResponse), Page<T> Spring Data standard pour les listes.
 * Le `hotelId` n'est JAMAIS envoyé par le client (JWT côté serveur).
 *
 * Périmètre B7 :
 *  - exercices (liste + clôturer)
 *  - plan-comptable (consultation seule)
 *  - compte-mapping (édition inline par typeEvenement)
 *  - journaux (liste + form create/edit + activer/désactiver)
 *  - ecritures (liste + form create + detail + contre-passer)
 *  - tva config (édition inline) + déclarations TVA (liste + form + valider)
 *  - états de synthèse : balance, grand-livre, journal, bilan,
 *    compte-resultat — avec exports XLSX et PDF (download Blob).
 *
 * Pas de feature store NgRx — états locaux composant (BehaviorSubject /
 * variables) + services HTTP suffisent. Cohérent avec convention
 * `cityfrontend/CLAUDE.md §7 root-only`.
 */
@NgModule({
  declarations: [
    ExercicesListComponent,
    PlanComptableListComponent,
    CompteMappingListComponent,
    JournauxListComponent,
    JournalFormComponent,
    EcrituresListComponent,
    EcritureDetailComponent,
    EcritureFormComponent,
    TvaConfigListComponent,
    DeclarationsTvaListComponent,
    DeclarationTvaFormComponent,
    BalanceViewerComponent,
    GrandLivreViewerComponent,
    JournalViewerComponent,
    BilanViewerComponent,
    CompteResultatViewerComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    ComptabiliteRoutingModule,
    TranslateModule.forChild(),
  ],
})
export class ComptabiliteModule {}
