/**
 * Modèles "Folio client" — feature `hebergement` (Tour 46).
 *
 * Réponse de `GET /api/finance/comptes/client/{clientId}/folio?dateDebut=&dateFin=`.
 *
 * Le folio liste les opérations DEBIT / CREDIT enregistrées sur le compte
 * auxiliaire client (cf. doctrine Tour 20 : City Hotel = comptabilité
 * auxiliaire client uniquement, comptabilité générale externalisée Dolibarr).
 *
 * Utilisé par la modale "Paiements" du calendrier — section "Liste Folio".
 */

/**
 * Type d'opération sur le compte client.
 *  - `DEBIT`  : montant dû (facturation, nuitée, produit, service)
 *  - `CREDIT` : montant reçu / encaissé (paiement, avoir)
 */
export type TypeOperationCompte = 'DEBIT' | 'CREDIT';

/**
 * Une opération unitaire (1 ligne du folio) — ordonnée chronologiquement.
 *
 * `soldeApres` reflète l'état du compte juste après l'opération.
 * Les champs `factureId` / `ligneFactureId` / `paiementId` sont optionnels et
 * exposés quand l'opération est rattachée à un objet métier (utile pour
 * navigation et explication).
 */
export interface OperationCompteFolioDto {
  operationId: number;
  /** ISO `yyyy-MM-dd` (ou ISO instant si serveur le sérialise complet). */
  dateOperation: string;
  type: TypeOperationCompte;
  /** Libellé court de l'opération. */
  motif: string;
  /** Détail libre (optionnel). */
  description?: string;
  /** Montant absolu (signe implicite via `type`). */
  montant: number;
  /** Solde du compte après cette opération. */
  soldeApres: number;
  /** Référence facture (si l'opération matérialise une facturation). */
  factureId?: number;
  factureNumero?: string;
  /** Référence ligne facture (granularité fine). */
  ligneFactureId?: number;
  ligneLibelle?: string;
  /** Référence paiement (si l'opération matérialise un encaissement). */
  paiementId?: number;
  paiementNumero?: string;
}

/**
 * Réponse complète du folio pour une période donnée.
 *
 * `soldeOuverture` = solde avant le premier mouvement de la période.
 * `soldeCloture`   = solde après le dernier mouvement de la période.
 * `totalDebits` / `totalCredits` = sommes brutes sur la période (sans signe).
 *
 * Convention : un solde **négatif** signifie que le client doit de l'argent
 * (cumul DEBITS > cumul CREDITS — facturations supérieures aux paiements).
 */
export interface FolioDto {
  compteId: number;
  clientId: number;
  clientNom: string;
  soldeOuverture: number;
  soldeCloture: number;
  totalDebits: number;
  totalCredits: number;
  operations: OperationCompteFolioDto[];
}
