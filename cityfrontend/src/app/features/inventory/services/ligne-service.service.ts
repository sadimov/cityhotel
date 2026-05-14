import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';

/**
 * Service HTTP — Bridge ServiceHotelier ↔ LigneFacture (Tour 51bis).
 *
 * Permet d'ajouter une ligne de facture de type SERVICE à une facture
 * existante (ou de créer la facture brouillon si elle n'existe pas encore)
 * pour une réservation. Backend cible :
 *   - POST /api/finance/factures (FactureCreateDto avec lignes[serviceId,typeLigne=SERVICE])
 *     → côté Tour 51bis on s'appuie sur l'endpoint déjà câblé qui accepte
 *       un `serviceId` dans `LigneFactureCreateDto`.
 *
 * ⚠️ `hotelId` jamais transmis (JWT côté serveur).
 */
export interface AjouterLigneServiceRequest {
  reservationId: number;
  serviceId: number;
  quantite: number;
  /** Override prix unitaire optionnel — sinon prix du service utilisé. */
  prixUnitaire?: number;
  libelle?: string;
  datePrestation?: string;
}

export interface AjouterLigneServiceResponse {
  factureId: number;
  ligneFactureId?: number;
  numeroFacture?: string;
  montantTtc?: number;
}

@Injectable({ providedIn: 'root' })
export class LigneServiceService {
  /**
   * Endpoint cible Tour 51bis : on passe par la route `from-reservation`
   * qui crée/réutilise la facture brouillon, puis on POST sur la facture
   * pour ajouter la ligne service. Pour rester compatible avec l'API
   * actuelle (qui ne possède pas d'endpoint "POST ligne-service" dédié),
   * on utilise `POST /api/finance/factures` avec FactureCreateDto contenant
   * une seule ligne typeLigne=SERVICE — le service backend `creerLigne`
   * accepte déjà `serviceId` (cf. FactureServiceImpl#creerLigne L569).
   */
  private readonly factureBase = `${environment.apiUrl}/api/finance/factures`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Ajoute une ligne service à la facture associée à une réservation.
   *
   * Stratégie :
   *  1. Tente de récupérer / créer la facture brouillon via
   *     POST `/factures/from-reservation/{reservationId}` (idempotent côté back).
   *  2. Construit le payload `FactureCreateDto` minimal — ou idéalement
   *     ajoute la ligne via un endpoint dédié si exposé ultérieurement.
   *
   * Tour 51bis : la version actuelle utilise `POST /factures` avec
   * `reservationId` + `lignes[serviceId,typeLigne=SERVICE]`. Le backend
   * crée la facture si elle n'existe pas et la lie à la réservation.
   */
  addLigneService(req: AjouterLigneServiceRequest): Observable<AjouterLigneServiceResponse> {
    const payload = {
      typeFacture: 'FACTURE',
      reservationId: req.reservationId,
      lignes: [
        {
          typeLigne: 'SERVICE',
          serviceId: req.serviceId,
          libelle: req.libelle ?? '',
          quantite: req.quantite,
          prixUnitaire: req.prixUnitaire ?? 0,
          datePrestation: req.datePrestation,
        },
      ],
    };
    return this.http.post<AjouterLigneServiceResponse>(this.factureBase, payload);
  }
}
