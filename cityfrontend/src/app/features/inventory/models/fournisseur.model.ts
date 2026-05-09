/**
 * Fournisseur — entité du module inventory.
 *
 * Note multi-tenant : `hotelId` n'est jamais envoyé par le client lors d'un
 * POST/PUT (le backend le lit du JWT). Il est cependant exposé en lecture
 * dans le DTO de retour. Cf. CLAUDE.md racine §6.1.
 */
export interface Fournisseur {
  fournisseurId?: number;
  hotelId?: number;
  nomFournisseur: string;
  contactPrincipal?: string;
  telephone?: string;
  email?: string;
  adresse?: string;
  ville?: string;
  pays?: string;
  conditionsPaiement?: string;
  actif?: boolean;
  dateCreation?: string;
  nombreBonsCommande?: number;
  montantTotalCommandes?: number;
}
