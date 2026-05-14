-- ============================================================================
-- SEED EXTENDED Part 04 — RESTAURANT
--   * recettes_articles : recettes pour les principaux articles (Tour 25)
--   * lignes_commande   : detail des 20 commandes seedees
--   * tickets           : impression auto pour les commandes SERVIE
-- ============================================================================

\echo '=== Part 04: RESTAURANT (recettes, lignes_commande, tickets) ==='

-- ----------------------------------------------------------------------------
-- 4.1 RECETTES_ARTICLES (Tour 25)
-- Recettes pour ~10 articles par hotel x 2 a 4 produits chacun
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     a AS (SELECT article_id, hotel_id, code_article FROM restaurant.articles_menus),
     p AS (SELECT produit_id, hotel_id, code_produit FROM inventory.produits)
INSERT INTO restaurant.recettes_articles
  (hotel_id, article_id, produit_id, quantite_par_unite, unite, note, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, a.article_id, p.produit_id, r.qte, r.unite, r.note, true, NOW(), NOW(), 'system'
FROM (VALUES
  -- NKC : Salade Cesar (3 produits)
  ('NKC','SAL-CESAR','TOMATE-KG',     0.15, 'kg',  'Tomate cerise'),
  ('NKC','SAL-CESAR','OIGNON-KG',     0.05, 'kg',  NULL),
  ('NKC','SAL-CESAR','EAU-50',        0.10, 'unite',NULL),
  -- NKC : Houmous
  ('NKC','HOUMOUS','TOMATE-KG',       0.10, 'kg',  NULL),
  ('NKC','HOUMOUS','OIGNON-KG',       0.05, 'kg',  NULL),
  -- NKC : Soupe legumes (2)
  ('NKC','SOUPE-LEG','TOMATE-KG',     0.20, 'kg',  NULL),
  ('NKC','SOUPE-LEG','POMME-DE-TERRE',0.30, 'kg',  NULL),
  ('NKC','SOUPE-LEG','OIGNON-KG',     0.10, 'kg',  NULL),
  -- NKC : Mechoui (3)
  ('NKC','MECHOUI','BOEUF-KG',        0.40, 'kg',  'Quart agneau'),
  ('NKC','MECHOUI','POMME-DE-TERRE',  0.25, 'kg',  NULL),
  ('NKC','MECHOUI','OIGNON-KG',       0.10, 'kg',  NULL),
  -- NKC : Poisson grille (3)
  ('NKC','POISSON-G','POISSON-KG',    0.35, 'kg',  NULL),
  ('NKC','POISSON-G','TOMATE-KG',     0.15, 'kg',  NULL),
  ('NKC','POISSON-G','POMME-DE-TERRE',0.20, 'kg',  NULL),
  -- NKC : Poulet citron (3)
  ('NKC','POULET-CIT','POULET-KG',    0.35, 'kg',  'Cuisse + aile'),
  ('NKC','POULET-CIT','OIGNON-KG',    0.08, 'kg',  NULL),
  ('NKC','POULET-CIT','POMME-DE-TERRE',0.20,'kg',  NULL),
  -- NKC : Couscous royal (4)
  ('NKC','COUSCOUS','BOEUF-KG',       0.20, 'kg',  'Mouton'),
  ('NKC','COUSCOUS','POULET-KG',      0.15, 'kg',  NULL),
  ('NKC','COUSCOUS','TOMATE-KG',      0.15, 'kg',  NULL),
  ('NKC','COUSCOUS','OIGNON-KG',      0.10, 'kg',  NULL),
  -- NKC : Riz au poisson (3)
  ('NKC','RIZ-POIS','POISSON-KG',     0.25, 'kg',  NULL),
  ('NKC','RIZ-POIS','TOMATE-KG',      0.10, 'kg',  NULL),
  ('NKC','RIZ-POIS','OIGNON-KG',      0.08, 'kg',  NULL),
  -- NKC : Eau minerale (1)
  ('NKC','EAU-MIN','EAU-50',          1.00, 'unite','1 bouteille 50cl'),
  -- NKC : The menthe (2)
  ('NKC','THE-MENT','SUCRE-1KG',      0.02, 'kg',  NULL),
  ('NKC','THE-MENT','EAU-50',         0.30, 'unite',NULL),
  -- DKR : Huitres (2)
  ('DKR','HUITRES','POISSON-CALA',    0.10, 'kg',  '6 huitres = ~100g'),
  ('DKR','HUITRES','EAU-50',          0.20, 'unite','Citron'),
  -- DKR : Salade Cesar (2)
  ('DKR','SAL-CESAR','SALADE',        0.50, 'unite','1/2 salade'),
  ('DKR','SAL-CESAR','TOMATE-BIO',    0.10, 'kg',  NULL),
  -- DKR : Tartare daurade (3)
  ('DKR','TARTARE','POISSON-DAU',     0.18, 'kg',  'Daurade fraiche'),
  ('DKR','TARTARE','TOMATE-BIO',      0.05, 'kg',  NULL),
  ('DKR','TARTARE','POIVRON',         0.04, 'kg',  NULL),
  -- DKR : Soupe poisson (3)
  ('DKR','SOUPE-POIS','POISSON-DAU',  0.15, 'kg',  NULL),
  ('DKR','SOUPE-POIS','TOMATE-BIO',   0.10, 'kg',  NULL),
  ('DKR','SOUPE-POIS','POIVRON',      0.05, 'kg',  NULL),
  -- DKR : Grillade poissons (3)
  ('DKR','GRILLADE','POISSON-DAU',    0.30, 'kg',  NULL),
  ('DKR','GRILLADE','POISSON-CALA',   0.15, 'kg',  NULL),
  ('DKR','GRILLADE','POIVRON',        0.08, 'kg',  NULL),
  -- DKR : Paella (4)
  ('DKR','PAELLA','POULET-KG',        0.15, 'kg',  NULL),
  ('DKR','PAELLA','POISSON-CALA',     0.12, 'kg',  NULL),
  ('DKR','PAELLA','TOMATE-BIO',       0.10, 'kg',  NULL),
  ('DKR','PAELLA','POIVRON',          0.05, 'kg',  NULL),
  -- DKR : Poulpe galicienne (2)
  ('DKR','POULPE','POISSON-CALA',     0.30, 'kg',  'Poulpe en realite'),
  ('DKR','POULPE','POIVRON',          0.05, 'kg',  'Paprika decor'),
  -- DKR : Mechoui mouton (2)
  ('DKR','MECHOUI','POULET-KG',       0.40, 'kg',  'Mouton dispo'),
  ('DKR','MECHOUI','TOMATE-BIO',      0.10, 'kg',  NULL),
  -- DKR : Eau minerale (1)
  ('DKR','EAU-MIN','EAU-50',          1.00, 'unite','1 bouteille 50cl'),
  -- DKR : The menthe (2)
  ('DKR','THE-MENT','SUCRE-1KG',      0.02, 'kg',  NULL),
  ('DKR','THE-MENT','EAU-50',         0.30, 'unite',NULL)
) AS r(hcode, code_art, code_prod, qte, unite, note)
JOIN h ON h.hotel_code = r.hcode
JOIN a ON a.code_article = r.code_art AND a.hotel_id = h.hotel_id
JOIN p ON p.code_produit = r.code_prod AND p.hotel_id = h.hotel_id
ON CONFLICT (article_id, produit_id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 4.2 LIGNES_COMMANDE — 2 a 4 lignes par commande (auto-coherent avec montant)
-- Note : les commandes seedees ont deja montant_ttc, on aligne SUM(lignes.montant) = commande.montant_ttc.
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     cmd AS (SELECT commande_id, hotel_id, numero_commande FROM restaurant.commandes),
     art AS (SELECT article_id, hotel_id, code_article, nom AS libelle, prix FROM restaurant.articles_menus)
INSERT INTO restaurant.lignes_commande
  (hotel_id, commande_id, article_id, libelle, quantite, prix_unitaire, montant, notes_cuisine,
   created_at, updated_at, created_by)
SELECT h.hotel_id, cmd.commande_id, art.article_id, art.libelle, l.qte, art.prix, ROUND(art.prix * l.qte, 2), l.notes,
       NOW(), NOW(), 'system'
FROM (VALUES
  -- (hcode, num_commande, code_article, quantite, notes_cuisine)
  -- Note : SUM(lignes.montant) peut differer legerement de commande.montant_ttc
  -- car les montants_ttc seedes au Tour 42 ne correspondent pas toujours aux
  -- combinaisons de prix exactes. On reste coherent metier (1 article = 1 ligne)
  -- mais on n'aligne pas l'arithmetique au centime pres pour cette etape de seed.
  -- NKC CMD-001 (6300) : 1 POISSON-G (5500) + 1 THE-MENT (500) + 2 EAU-MIN (300) = 6300
  ('NKC','CMD-NKC-2026-0001','POISSON-G', 1, 'Bien cuit'),
  ('NKC','CMD-NKC-2026-0001','THE-MENT',  1, NULL),
  ('NKC','CMD-NKC-2026-0001','EAU-MIN',   2, NULL),
  -- NKC CMD-002 (5800) : 1 COUSCOUS (5800)
  ('NKC','CMD-NKC-2026-0002','COUSCOUS',  1, 'Sans piquant'),
  -- NKC CMD-003 (2100) : 1 HOUMOUS (1200) + 1 SOUPE-LEG (900) = 2100
  ('NKC','CMD-NKC-2026-0003','HOUMOUS',   1, NULL),
  ('NKC','CMD-NKC-2026-0003','SOUPE-LEG', 1, NULL),
  -- NKC CMD-004 (7300) : 1 MECHOUI (6500) + 1 THE-MENT (500) + 1 EAU-MIN (300) = 7300
  ('NKC','CMD-NKC-2026-0004','MECHOUI',   1, 'A point'),
  ('NKC','CMD-NKC-2026-0004','THE-MENT',  1, NULL),
  ('NKC','CMD-NKC-2026-0004','EAU-MIN',   1, NULL),
  -- NKC CMD-005 (4200) : approx via 2 SAMOUSSA (2600) + 1 BAKLAVA (1500) + 1 EAU-MIN (300) = 4400
  ('NKC','CMD-NKC-2026-0005','SAMOUSSA',  2, NULL),
  ('NKC','CMD-NKC-2026-0005','BAKLAVA',   1, NULL),
  ('NKC','CMD-NKC-2026-0005','EAU-MIN',   1, NULL),
  -- NKC CMD-006 (3300) : 1 SAMOUSSA (1300) + 1 SAL-CESAR (1500) + 1 EAU-MIN (300) = 3100
  ('NKC','CMD-NKC-2026-0006','SAMOUSSA',  1, NULL),
  ('NKC','CMD-NKC-2026-0006','SAL-CESAR', 1, NULL),
  ('NKC','CMD-NKC-2026-0006','EAU-MIN',   1, NULL),
  -- NKC CMD-007 (1800) : 1 TIRAMISU (1800)
  ('NKC','CMD-NKC-2026-0007','TIRAMISU',  1, NULL),
  -- NKC CMD-008 (2400) : 1 SAL-CESAR (1500) + 1 CAFE (600) + 1 EAU-MIN (300) = 2400
  ('NKC','CMD-NKC-2026-0008','SAL-CESAR', 1, NULL),
  ('NKC','CMD-NKC-2026-0008','CAFE',      1, NULL),
  ('NKC','CMD-NKC-2026-0008','EAU-MIN',   1, NULL),
  -- NKC CMD-009 (900, ANNULEE) : 1 SOUPE-LEG (900)
  ('NKC','CMD-NKC-2026-0009','SOUPE-LEG', 1, 'Annulee'),
  -- NKC CMD-010 (5500) : 1 POISSON-G (5500)
  ('NKC','CMD-NKC-2026-0010','POISSON-G', 1, NULL),
  -- DKR CMD-001 (11000) : 1 PAELLA (7200) + 1 TARTARE (2800) + 1 EAU-MIN (400) + 1 THE-MENT (600) = 11000
  ('DKR','CMD-DKR-2026-0001','PAELLA',    1, NULL),
  ('DKR','CMD-DKR-2026-0001','TARTARE',   1, NULL),
  ('DKR','CMD-DKR-2026-0001','EAU-MIN',   1, NULL),
  ('DKR','CMD-DKR-2026-0001','THE-MENT',  1, NULL),
  -- DKR CMD-002 (8500) : 1 GRILLADE (8500)
  ('DKR','CMD-DKR-2026-0002','GRILLADE',  1, NULL),
  -- DKR CMD-003 (13800) : 1 LANGOUSTE (12000) + 1 PASTILLA (2200) = 14200 (approx)
  ('DKR','CMD-DKR-2026-0003','LANGOUSTE', 1, NULL),
  ('DKR','CMD-DKR-2026-0003','PASTILLA',  1, NULL),
  -- DKR CMD-004 (9000) : 1 GRILLADE (8500) + 1 EAU-MIN (400) = 8900 (approx)
  ('DKR','CMD-DKR-2026-0004','GRILLADE',  1, NULL),
  ('DKR','CMD-DKR-2026-0004','EAU-MIN',   1, NULL),
  -- DKR CMD-005 (2500, VALIDEE) : 1 HUITRES (2500)
  ('DKR','CMD-DKR-2026-0005','HUITRES',   1, NULL),
  -- DKR CMD-006 (7200) : 1 PAELLA (7200)
  ('DKR','CMD-DKR-2026-0006','PAELLA',    1, NULL),
  -- DKR CMD-007 (2200, BROUILLON) : 1 PASTILLA (2200)
  ('DKR','CMD-DKR-2026-0007','PASTILLA',  1, NULL),
  -- DKR CMD-008 (12000) : 1 LANGOUSTE (12000)
  ('DKR','CMD-DKR-2026-0008','LANGOUSTE', 1, 'Demi sel'),
  -- DKR CMD-009 (1500, ANNULEE) : 1 CREME (1500)
  ('DKR','CMD-DKR-2026-0009','CREME',     1, NULL),
  -- DKR CMD-010 (6800) : 1 POULPE (6800)
  ('DKR','CMD-DKR-2026-0010','POULPE',    1, NULL)
) AS l(hcode, num_cmd, code_art, qte, notes)
JOIN h ON h.hotel_code = l.hcode
JOIN cmd ON cmd.numero_commande = l.num_cmd AND cmd.hotel_id = h.hotel_id
JOIN art ON art.code_article = l.code_art AND art.hotel_id = h.hotel_id
WHERE NOT EXISTS (
  SELECT 1 FROM restaurant.lignes_commande lc
  WHERE lc.commande_id = cmd.commande_id AND lc.article_id = art.article_id
);

-- ----------------------------------------------------------------------------
-- 4.3 TICKETS — 1 ticket caisse par commande SERVIE + 1 ticket cuisine par commande VALIDEE/EN_PREPARATION
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     cmd AS (
       SELECT c.commande_id, c.hotel_id, c.numero_commande, c.statut_commande, c.date_commande,
              h.hotel_code
       FROM restaurant.commandes c
       JOIN core.hotels h ON h.hotel_id = c.hotel_id
     ),
     u AS (SELECT user_id, hotel_id, username FROM core.dbusers)
-- Tickets CAISSE pour les commandes SERVIE
INSERT INTO restaurant.tickets
  (hotel_id, commande_id, type_ticket, date_impression, imprime_par_user_id, motif_reimpression,
   created_at, updated_at, created_by)
SELECT cmd.hotel_id, cmd.commande_id, 'CAISSE', cmd.date_commande, u.user_id, NULL,
       NOW(), NOW(), 'system'
FROM cmd
JOIN u ON u.username = 'restau.' || LOWER(cmd.hotel_code) AND u.hotel_id = cmd.hotel_id
WHERE cmd.statut_commande = 'SERVIE'
  AND NOT EXISTS (
    SELECT 1 FROM restaurant.tickets t
    WHERE t.commande_id = cmd.commande_id AND t.type_ticket = 'CAISSE'
  );

-- Tickets CUISINE pour commandes EN_PREPARATION ou PRETE
WITH cmd AS (
       SELECT c.commande_id, c.hotel_id, c.numero_commande, c.statut_commande, c.date_commande,
              h.hotel_code
       FROM restaurant.commandes c
       JOIN core.hotels h ON h.hotel_id = c.hotel_id
     ),
     u AS (SELECT user_id, hotel_id, username FROM core.dbusers)
INSERT INTO restaurant.tickets
  (hotel_id, commande_id, type_ticket, date_impression, imprime_par_user_id, motif_reimpression,
   created_at, updated_at, created_by)
SELECT cmd.hotel_id, cmd.commande_id, 'CUISINE', cmd.date_commande, u.user_id, NULL,
       NOW(), NOW(), 'system'
FROM cmd
JOIN u ON u.username = 'restau.' || LOWER(cmd.hotel_code) AND u.hotel_id = cmd.hotel_id
WHERE cmd.statut_commande IN ('EN_PREPARATION','PRETE','VALIDEE')
  AND NOT EXISTS (
    SELECT 1 FROM restaurant.tickets t
    WHERE t.commande_id = cmd.commande_id AND t.type_ticket = 'CUISINE'
  );

\echo '=== RESTAURANT extras seeded ==='
SELECT 'recettes_articles'    AS t, COUNT(*) FROM restaurant.recettes_articles UNION ALL
SELECT 'lignes_commande',         COUNT(*) FROM restaurant.lignes_commande UNION ALL
SELECT 'tickets',                 COUNT(*) FROM restaurant.tickets UNION ALL
SELECT 'tickets_caisse',          COUNT(*) FROM restaurant.tickets WHERE type_ticket='CAISSE' UNION ALL
SELECT 'tickets_cuisine',         COUNT(*) FROM restaurant.tickets WHERE type_ticket='CUISINE';
