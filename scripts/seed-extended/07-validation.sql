-- ============================================================================
-- SEED EXTENDED Part 07 — VALIDATION
--
-- Verifications de coherence post-seed :
--   * Tous les CHECK(hotel_id > 0) respectes (rien a verifier ici, fait par DB)
--   * Facture.montant_ttc = SUM(LigneFacture.montant_ttc) approximatif
--   * Facture.montant_paye = SUM(AffectationPaiement.montant_affecte)
--   * Nuitee.reservation_id toujours valide
--   * MouvementStock.produit_id toujours valide
--   * Counts par table touchee
-- ============================================================================

\echo '################################################################'
\echo '#                    VALIDATION POST-SEED                       #'
\echo '################################################################'

\echo ''
\echo '--- Volumetrie globale par schema/table ---'

SELECT 'core.hotels'                  AS table_name, COUNT(*) AS n FROM core.hotels UNION ALL
SELECT 'core.roles',                                COUNT(*) FROM core.roles UNION ALL
SELECT 'core.dbusers',                              COUNT(*) FROM core.dbusers UNION ALL
SELECT 'core.parametres',                           COUNT(*) FROM core.parametres UNION ALL
SELECT 'client.societes',                           COUNT(*) FROM client.societes UNION ALL
SELECT 'client.clients',                            COUNT(*) FROM client.clients UNION ALL
SELECT 'hebergement.types_chambres',                COUNT(*) FROM hebergement.types_chambres UNION ALL
SELECT 'hebergement.types_chambres (SALLE)',        COUNT(*) FROM hebergement.types_chambres WHERE categorie='SALLE' UNION ALL
SELECT 'hebergement.chambres',                      COUNT(*) FROM hebergement.chambres UNION ALL
SELECT 'hebergement.tarifs_chambres',               COUNT(*) FROM hebergement.tarifs_chambres UNION ALL
SELECT 'hebergement.reservations',                  COUNT(*) FROM hebergement.reservations UNION ALL
SELECT 'hebergement.reservations_chambres',         COUNT(*) FROM hebergement.reservations_chambres UNION ALL
SELECT 'hebergement.nuitees',                       COUNT(*) FROM hebergement.nuitees UNION ALL
SELECT 'inventory.categories_produits',             COUNT(*) FROM inventory.categories_produits UNION ALL
SELECT 'inventory.fournisseurs',                    COUNT(*) FROM inventory.fournisseurs UNION ALL
SELECT 'inventory.produits',                        COUNT(*) FROM inventory.produits UNION ALL
SELECT 'inventory.bons_commande',                   COUNT(*) FROM inventory.bons_commande UNION ALL
SELECT 'inventory.lignes_bons_commande',            COUNT(*) FROM inventory.lignes_bons_commande UNION ALL
SELECT 'inventory.bons_sortie',                     COUNT(*) FROM inventory.bons_sortie UNION ALL
SELECT 'inventory.lignes_bons_sortie',              COUNT(*) FROM inventory.lignes_bons_sortie UNION ALL
SELECT 'inventory.mouvements_stock',                COUNT(*) FROM inventory.mouvements_stock UNION ALL
SELECT 'inventory.types_services_hoteliers',        COUNT(*) FROM inventory.types_services_hoteliers UNION ALL
SELECT 'inventory.services_hoteliers',              COUNT(*) FROM inventory.services_hoteliers UNION ALL
SELECT 'restaurant.categories_menus',               COUNT(*) FROM restaurant.categories_menus UNION ALL
SELECT 'restaurant.articles_menus',                 COUNT(*) FROM restaurant.articles_menus UNION ALL
SELECT 'restaurant.recettes_articles',              COUNT(*) FROM restaurant.recettes_articles UNION ALL
SELECT 'restaurant.commandes',                      COUNT(*) FROM restaurant.commandes UNION ALL
SELECT 'restaurant.lignes_commande',                COUNT(*) FROM restaurant.lignes_commande UNION ALL
SELECT 'restaurant.tickets',                        COUNT(*) FROM restaurant.tickets UNION ALL
SELECT 'finance.numerotation_sequence',             COUNT(*) FROM finance.numerotation_sequence UNION ALL
SELECT 'finance.comptes',                           COUNT(*) FROM finance.comptes UNION ALL
SELECT 'finance.factures',                          COUNT(*) FROM finance.factures UNION ALL
SELECT 'finance.lignes_factures',                   COUNT(*) FROM finance.lignes_factures UNION ALL
SELECT 'finance.paiements',                         COUNT(*) FROM finance.paiements UNION ALL
SELECT 'finance.affectations_paiements',            COUNT(*) FROM finance.affectations_paiements UNION ALL
SELECT 'finance.operations_comptes',                COUNT(*) FROM finance.operations_comptes UNION ALL
SELECT 'menage.personnel',                          COUNT(*) FROM menage.personnel UNION ALL
SELECT 'menage.taches',                             COUNT(*) FROM menage.taches UNION ALL
SELECT 'menage.planning',                           COUNT(*) FROM menage.planning UNION ALL
SELECT 'menage.historique',                         COUNT(*) FROM menage.historique
ORDER BY 1;

\echo ''
\echo '--- Verification coherence facture vs lignes (montant_ttc agregeable) ---'
SELECT f.numero_facture,
       f.montant_ttc                                            AS facture_ttc,
       COALESCE(SUM(lf.montant_ttc), 0)                         AS somme_lignes_ttc,
       f.montant_ttc - COALESCE(SUM(lf.montant_ttc), 0)         AS ecart
FROM finance.factures f
LEFT JOIN finance.lignes_factures lf ON lf.facture_id = f.facture_id
WHERE f.statut <> 'BROUILLON'
GROUP BY f.facture_id, f.numero_facture, f.montant_ttc
ORDER BY f.numero_facture;

\echo ''
\echo '--- Verification coherence facture.montant_paye vs SUM(affectations) ---'
SELECT f.numero_facture,
       f.statut,
       f.montant_paye                                                 AS facture_montant_paye,
       COALESCE(SUM(ap.montant_affecte), 0)                           AS somme_affectations,
       f.montant_paye - COALESCE(SUM(ap.montant_affecte), 0)          AS ecart
FROM finance.factures f
LEFT JOIN finance.affectations_paiements ap ON ap.facture_id = f.facture_id
GROUP BY f.facture_id, f.numero_facture, f.statut, f.montant_paye
ORDER BY f.numero_facture;

\echo ''
\echo '--- Verification soldes comptes (DEBIT - CREDIT) ---'
SELECT c.numero_compte,
       c.type_compte,
       c.solde_actuel,
       COALESCE(SUM(CASE WHEN oc.type_operation = 'DEBIT'  THEN oc.montant ELSE 0 END), 0) AS total_debit,
       COALESCE(SUM(CASE WHEN oc.type_operation = 'CREDIT' THEN oc.montant ELSE 0 END), 0) AS total_credit
FROM finance.comptes c
LEFT JOIN finance.operations_comptes oc ON oc.compte_id = c.compte_id
GROUP BY c.compte_id, c.numero_compte, c.type_compte, c.solde_actuel
HAVING SUM(CASE WHEN oc.type_operation IS NOT NULL THEN 1 ELSE 0 END) > 0
ORDER BY c.numero_compte;

\echo ''
\echo '--- Verification FK : nuitees -> reservations valides ---'
SELECT COUNT(*) AS nuitees_orphelines
FROM hebergement.nuitees n
LEFT JOIN hebergement.reservations r ON r.reservation_id = n.reservation_id
WHERE r.reservation_id IS NULL;

\echo ''
\echo '--- Verification FK : mouvements_stock -> produit valide ---'
SELECT COUNT(*) AS mouvements_orphelins
FROM inventory.mouvements_stock ms
LEFT JOIN inventory.produits p ON p.produit_id = ms.produit_id
WHERE p.produit_id IS NULL;

\echo ''
\echo '--- Verification FK : lignes_factures -> facture valide ---'
SELECT COUNT(*) AS lignes_orphelines
FROM finance.lignes_factures lf
LEFT JOIN finance.factures f ON f.facture_id = lf.facture_id
WHERE f.facture_id IS NULL;

\echo ''
\echo '--- Verification isolation tenant : aucune ligne hotel_id=0 (sentinel ROOT) ---'
SELECT 'finance.factures'         AS t, COUNT(*) AS n FROM finance.factures WHERE hotel_id = 0 UNION ALL
SELECT 'finance.paiements',           COUNT(*)        FROM finance.paiements WHERE hotel_id = 0 UNION ALL
SELECT 'inventory.bons_commande',     COUNT(*)        FROM inventory.bons_commande WHERE hotel_id = 0 UNION ALL
SELECT 'inventory.bons_sortie',       COUNT(*)        FROM inventory.bons_sortie WHERE hotel_id = 0 UNION ALL
SELECT 'inventory.mouvements_stock',  COUNT(*)        FROM inventory.mouvements_stock WHERE hotel_id = 0 UNION ALL
SELECT 'inventory.services_hoteliers',COUNT(*)        FROM inventory.services_hoteliers WHERE hotel_id = 0 UNION ALL
SELECT 'restaurant.recettes_articles',COUNT(*)        FROM restaurant.recettes_articles WHERE hotel_id = 0 UNION ALL
SELECT 'menage.planning',             COUNT(*)        FROM menage.planning WHERE hotel_id = 0 UNION ALL
SELECT 'menage.historique',           COUNT(*)        FROM menage.historique WHERE hotel_id = 0;

\echo ''
\echo '--- Repartition tenant (hotel_id NKC + DKR) ---'
SELECT
  (SELECT hotel_code FROM core.hotels WHERE hotel_id = f.hotel_id) AS hotel,
  COUNT(*) AS factures
FROM finance.factures f
GROUP BY f.hotel_id
ORDER BY hotel;

\echo ''
\echo '################################################################'
\echo '#                    VALIDATION TERMINEE                        #'
\echo '################################################################'
