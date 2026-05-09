/**
 * Modèle TypeChambre — feature `hebergement`.
 *
 * Source : `HEBERGEMENT/COMPONENT_CALENDAR/models_services_hebergement_frontend.ts`
 * (Tour 11 — diff trois-voies : les 4 instances `models_services_hebergement_frontend.ts`
 * dans /HEBERGEMENT/COMPONENT_CALENDAR, /MENAGE/files_front, /RESTAURANT et
 * /RESTAURANT/point-vente sont byte-for-byte identiques (md5 commun
 * `2ad7cd3314ab4f81b133df6a6ec0065f`). Choix par défaut COMPONENT_CALENDAR.
 *
 * ⚠️ `hotelId` reste exposé en lecture pour rétro-compatibilité avec les payloads
 * serveur, mais le **front ne doit jamais l'envoyer** lors d'un create/update —
 * le backend l'extrait du JWT (CLAUDE.md racine §6.1).
 */
export interface TypeChambre {
  typeId?: number;
  /** ⚠️ Reçu en lecture uniquement — ne jamais l'envoyer côté front. */
  hotelId?: number;
  typeCode: string;
  typeNom: string;
  description?: string;
  superficie?: number;
  nbLitsMax: number;
  nbPersonnesMax: number;
  prixBase: number;
  actif?: boolean;
  dateCreation?: string;
  dateModification?: string;

  // Champs calculés serveur (lecture seule)
  nombreChambres?: number;
  nombreChambresActives?: number;
  nombreChambresDisponibles?: number;
  hotelNom?: string;
}
