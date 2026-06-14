/**
 * Modèles TS miroir des DTOs Java reporting restaurant.
 *   - JournalCaisseDto           (R-RES-001)
 *   - TopArticleDto              (R-RES-002)
 *   - TicketMarginDto            (R-RES-003)
 */

export interface ModePaiementLigneDto {
  modePaiement: string;
  nbPaiements: number;
  montantTotal: number;
}

export interface JournalCaisseDto {
  date: string;
  nbCommandes: number;
  totalRecettes: number;
  breakdownModes: ModePaiementLigneDto[];
}

export interface TopArticleLigneDto {
  rang: number;
  articleId: number;
  libelle: string;
  quantiteVendue: number;
  caTotal: number;
}

export interface TopArticleDto {
  from: string;
  to: string;
  limit: number;
  articles: TopArticleLigneDto[];
}

export interface ArticleMargeDto {
  articleId: number;
  libelle: string;
  prixVente: number;
  coutMatiere: number;
  margeUnitaire: number;
  margePourcentage: number;
}

export interface TicketMarginDto {
  from: string;
  to: string;
  nbCommandes: number;
  caTotal: number;
  ticketMoyen: number;
  marges: ArticleMargeDto[];
}
