-- ============================================================================
-- SEED EXTENDED Part 03 — INVENTORY
--   * bons_commande + lignes_bons_commande (5 par hotel = 10)
--   * bons_sortie + lignes_bons_sortie (5 par hotel = 10)
--   * mouvements_stock (ENTREE depuis BC LIVRE + SORTIE depuis BS LIVRE + AJUSTEMENT)
--   * types_services_hoteliers (Tour 51) — 4 par hotel
--   * services_hoteliers (Tour 51) — 8 par hotel
-- ============================================================================

\echo '=== Part 03: INVENTORY (BC, BS, mouvements, services hoteliers) ==='

-- ----------------------------------------------------------------------------
-- 3.1 BONS_COMMANDE — 5 par hotel
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     f AS (SELECT fournisseur_id, hotel_id, nom_fournisseur FROM inventory.fournisseurs),
     u AS (SELECT user_id, hotel_id, username FROM core.dbusers)
INSERT INTO inventory.bons_commande
  (hotel_id, numero_bc, fournisseur_id, statut, date_commande, date_livraison_prevue, date_livraison_reelle,
   montant_total, montant_tva, commentaires, user_id, created_at, updated_at, created_by)
SELECT h.hotel_id, b.num, f.fournisseur_id, b.statut, b.dt_cmd::date, b.dt_prev::date, b.dt_reel::date,
       b.mt, 0, b.com, u.user_id, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','BC-2026-MR-000001','Boissons Distrib SA',    'RECU_COMPLET','2026-04-15','2026-04-20','2026-04-20', 32500, 'Reappro boissons avril'),
  ('NKC','BC-2026-MR-000002','Marche Sebkha',          'RECU_COMPLET','2026-04-22','2026-04-25','2026-04-25', 18750, 'Legumes semaine'),
  ('NKC','BC-2026-MR-000003','Boucherie Centrale',     'RECU_COMPLET','2026-05-02','2026-05-04','2026-05-04', 24300, 'Viandes semaine 18'),
  ('NKC','BC-2026-MR-000004','Hygiene Pro NKC',        'ENVOYE',      '2026-05-08','2026-05-12',NULL,        45200, 'Reappro hygiene mensuel'),
  ('NKC','BC-2026-MR-000005','Marche Aux Poissons',    'BROUILLON',   '2026-05-10', NULL,        NULL,         9500, 'A confirmer chef'),
  ('DKR','BC-2026-MR-000001','Pecheries Dakhla',       'RECU_COMPLET','2026-04-18','2026-04-22','2026-04-22', 41500, 'Poissons fraichs avril'),
  ('DKR','BC-2026-MR-000002','Marche Central DKR',     'RECU_COMPLET','2026-04-25','2026-04-28','2026-04-28', 14200, 'Epicerie semaine'),
  ('DKR','BC-2026-MR-000003','Maraicher Bio Dakhla',   'RECU_COMPLET','2026-05-01','2026-05-03','2026-05-03', 11800, 'Legumes bio semaine 18'),
  ('DKR','BC-2026-MR-000004','Hygiene Sahel',          'ENVOYE',      '2026-05-07','2026-05-11',NULL,        52000, 'Reappro hygiene mensuel'),
  ('DKR','BC-2026-MR-000005','Boissons Atlantique',    'BROUILLON',   '2026-05-09', NULL,        NULL,        21500, 'Confirmer apres inventaire')
) AS b(hcode, num, fnom, statut, dt_cmd, dt_prev, dt_reel, mt, com)
JOIN h ON h.hotel_code = b.hcode
JOIN f ON f.nom_fournisseur = b.fnom AND f.hotel_id = h.hotel_id
JOIN u ON u.username = 'magasin.' || LOWER(h.hotel_code) AND u.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, numero_bc) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3.2 LIGNES_BONS_COMMANDE — 3 a 5 lignes par BC
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     bc AS (SELECT bon_commande_id, hotel_id, numero_bc FROM inventory.bons_commande),
     p  AS (SELECT produit_id, hotel_id, code_produit FROM inventory.produits)
INSERT INTO inventory.lignes_bons_commande
  (bon_commande_id, produit_id, quantite_commandee, quantite_recue, prix_unitaire, date_reception)
SELECT bc.bon_commande_id, p.produit_id, l.qcmd, l.qrec, l.prix, l.dt_recep::date
FROM (VALUES
  -- BC-001 NKC : Boissons (3 produits)
  ('NKC','BC-2026-MR-000001','EAU-50',     100,100, 50, '2026-04-20'),
  ('NKC','BC-2026-MR-000001','EAU-150',    50, 50, 120,'2026-04-20'),
  ('NKC','BC-2026-MR-000001','COCA-33',    80, 80, 150,'2026-04-20'),
  -- BC-002 NKC : Legumes (3 produits)
  ('NKC','BC-2026-MR-000002','TOMATE-KG',  30, 30, 180,'2026-04-25'),
  ('NKC','BC-2026-MR-000002','OIGNON-KG',  40, 40, 120,'2026-04-25'),
  ('NKC','BC-2026-MR-000002','POMME-DE-TERRE',50,50,150,'2026-04-25'),
  -- BC-003 NKC : Viandes (3 produits)
  ('NKC','BC-2026-MR-000003','BOEUF-KG',   20, 20, 850,'2026-05-04'),
  ('NKC','BC-2026-MR-000003','POULET-KG',  15, 15, 650,'2026-05-04'),
  ('NKC','BC-2026-MR-000003','POISSON-KG', 10, 10, 750,'2026-05-04'),
  -- BC-004 NKC : Hygiene (4 produits, statut ENVOYE -> quantite_recue 0)
  ('NKC','BC-2026-MR-000004','SAVON-50',   200, 0, 45, NULL),
  ('NKC','BC-2026-MR-000004','PAPIER-TOIL',150, 0, 55, NULL),
  ('NKC','BC-2026-MR-000004','SHAMPOOING-30',100,0, 75, NULL),
  ('NKC','BC-2026-MR-000004','SERVIETTE',  40,  0,680, NULL),
  -- BC-005 NKC : Poisson (1 produit, BROUILLON)
  ('NKC','BC-2026-MR-000005','POISSON-KG', 12,  0, 750, NULL),
  -- BC-001 DKR : Poissons (3 produits)
  ('DKR','BC-2026-MR-000001','POISSON-DAU',25, 25, 900,'2026-04-22'),
  ('DKR','BC-2026-MR-000001','POISSON-CALA',10,10,1100,'2026-04-22'),
  ('DKR','BC-2026-MR-000001','POULET-KG',  15, 15, 700,'2026-04-22'),
  -- BC-002 DKR : Epicerie (3 produits)
  ('DKR','BC-2026-MR-000002','SUCRE-1KG',   30, 30, 200,'2026-04-28'),
  ('DKR','BC-2026-MR-000002','SEL-1KG',     20, 20,  90,'2026-04-28'),
  ('DKR','BC-2026-MR-000002','EAU-50',      80, 80,  60,'2026-04-28'),
  -- BC-003 DKR : Legumes bio (3 produits)
  ('DKR','BC-2026-MR-000003','TOMATE-BIO',  25, 25, 220,'2026-05-03'),
  ('DKR','BC-2026-MR-000003','SALADE',      35, 35,  95,'2026-05-03'),
  ('DKR','BC-2026-MR-000003','POIVRON',     20, 20, 180,'2026-05-03'),
  -- BC-004 DKR : Hygiene (4 produits, ENVOYE)
  ('DKR','BC-2026-MR-000004','SAVON-50',    200, 0, 55, NULL),
  ('DKR','BC-2026-MR-000004','PAPIER-TOIL', 150, 0, 65, NULL),
  ('DKR','BC-2026-MR-000004','SHAMPOOING-30',100,0, 85, NULL),
  ('DKR','BC-2026-MR-000004','SERVIETTE',   40,  0,780, NULL),
  -- BC-005 DKR : Boissons (3 produits, BROUILLON)
  ('DKR','BC-2026-MR-000005','EAU-50',     70,  0, 60, NULL),
  ('DKR','BC-2026-MR-000005','EAU-150',    40,  0,140, NULL),
  ('DKR','BC-2026-MR-000005','COCA-33',    50,  0,180, NULL)
) AS l(hcode, num_bc, code_prod, qcmd, qrec, prix, dt_recep)
JOIN h ON h.hotel_code = l.hcode
JOIN bc ON bc.numero_bc = l.num_bc AND bc.hotel_id = h.hotel_id
JOIN p ON p.code_produit = l.code_prod AND p.hotel_id = h.hotel_id
WHERE NOT EXISTS (
  SELECT 1 FROM inventory.lignes_bons_commande lbc
  WHERE lbc.bon_commande_id = bc.bon_commande_id AND lbc.produit_id = p.produit_id
);

-- ----------------------------------------------------------------------------
-- 3.3 BONS_SORTIE — 5 par hotel
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     u AS (SELECT user_id, hotel_id, username FROM core.dbusers)
INSERT INTO inventory.bons_sortie
  (hotel_id, numero_bs, destination, statut, date_sortie, commentaires, user_id, created_at, updated_at, created_by)
SELECT h.hotel_id, b.num, b.dest, b.statut, b.dt::date, b.com, u.user_id, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','BS-2026-MR-000001','RESTAURANT', 'LIVRE',    '2026-05-05','Sortie restaurant matin'),
  ('NKC','BS-2026-MR-000002','RESTAURANT', 'LIVRE',    '2026-05-06','Sortie restaurant midi'),
  ('NKC','BS-2026-MR-000003','MENAGE',     'LIVRE',    '2026-05-07','Hygiene chambres etage 2'),
  ('NKC','BS-2026-MR-000004','RESTAURANT', 'VALIDE',   '2026-05-09','En attente livraison'),
  ('NKC','BS-2026-MR-000005','BAR',        'BROUILLON','2026-05-10','A valider chef caisse'),
  ('DKR','BS-2026-MR-000001','RESTAURANT', 'LIVRE',    '2026-05-04','Sortie cuisine quotidienne'),
  ('DKR','BS-2026-MR-000002','RESTAURANT', 'LIVRE',    '2026-05-05','Sortie soir poissons'),
  ('DKR','BS-2026-MR-000003','MENAGE',     'LIVRE',    '2026-05-06','Hygiene bungalows'),
  ('DKR','BS-2026-MR-000004','RESTAURANT', 'VALIDE',   '2026-05-09','En cours'),
  ('DKR','BS-2026-MR-000005','BAR',        'BROUILLON','2026-05-10','A valider')
) AS b(hcode, num, dest, statut, dt, com)
JOIN h ON h.hotel_code = b.hcode
JOIN u ON u.username = 'magasin.' || LOWER(h.hotel_code) AND u.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, numero_bs) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3.4 LIGNES_BONS_SORTIE — 3 par BS
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     bs AS (SELECT bon_sortie_id, hotel_id, numero_bs FROM inventory.bons_sortie),
     p  AS (SELECT produit_id, hotel_id, code_produit FROM inventory.produits)
INSERT INTO inventory.lignes_bons_sortie
  (bon_sortie_id, produit_id, quantite_demandee, quantite_servie, commentaires)
SELECT bs.bon_sortie_id, p.produit_id, l.qd, l.qs, l.com
FROM (VALUES
  -- NKC BS-001 restaurant
  ('NKC','BS-2026-MR-000001','BOEUF-KG',    5, 5, 'Plat du jour'),
  ('NKC','BS-2026-MR-000001','TOMATE-KG',   8, 8, 'Salades'),
  ('NKC','BS-2026-MR-000001','OIGNON-KG',   5, 5, NULL),
  -- NKC BS-002 restaurant midi
  ('NKC','BS-2026-MR-000002','POULET-KG',   3, 3, 'Poulet citron'),
  ('NKC','BS-2026-MR-000002','POMME-DE-TERRE',6,6,'Accompagnement'),
  ('NKC','BS-2026-MR-000002','EAU-50',     30, 30, 'Service'),
  -- NKC BS-003 menage
  ('NKC','BS-2026-MR-000003','SAVON-50',   50, 50, 'Etage 2'),
  ('NKC','BS-2026-MR-000003','SHAMPOOING-30',30,30,'Etage 2'),
  ('NKC','BS-2026-MR-000003','PAPIER-TOIL',20, 20, NULL),
  -- NKC BS-004 restaurant (VALIDE, quantite_servie=0)
  ('NKC','BS-2026-MR-000004','POISSON-KG',  4, 0, 'Diner'),
  ('NKC','BS-2026-MR-000004','TOMATE-KG',   3, 0, NULL),
  -- NKC BS-005 bar (BROUILLON)
  ('NKC','BS-2026-MR-000005','COCA-33',    24, 0, 'Reappro bar'),
  -- DKR BS-001 cuisine
  ('DKR','BS-2026-MR-000001','POISSON-DAU', 5, 5, 'Grillade midi'),
  ('DKR','BS-2026-MR-000001','SALADE',     10,10, NULL),
  ('DKR','BS-2026-MR-000001','POIVRON',     4, 4, NULL),
  -- DKR BS-002 soir poissons
  ('DKR','BS-2026-MR-000002','POISSON-CALA',3, 3, 'Calamars du soir'),
  ('DKR','BS-2026-MR-000002','TOMATE-BIO',  5, 5, NULL),
  ('DKR','BS-2026-MR-000002','EAU-50',     25, 25, NULL),
  -- DKR BS-003 menage
  ('DKR','BS-2026-MR-000003','SAVON-50',   60, 60, 'Bungalows'),
  ('DKR','BS-2026-MR-000003','SHAMPOOING-30',40,40,'Bungalows'),
  ('DKR','BS-2026-MR-000003','SERVIETTE',  10, 10, 'Remplacement'),
  -- DKR BS-004 restaurant (VALIDE)
  ('DKR','BS-2026-MR-000004','POULET-KG',   4, 0, NULL),
  ('DKR','BS-2026-MR-000004','SUCRE-1KG',   2, 0, 'Desserts'),
  -- DKR BS-005 bar (BROUILLON)
  ('DKR','BS-2026-MR-000005','EAU-150',    20, 0, 'Reappro bar')
) AS l(hcode, num_bs, code_prod, qd, qs, com)
JOIN h ON h.hotel_code = l.hcode
JOIN bs ON bs.numero_bs = l.num_bs AND bs.hotel_id = h.hotel_id
JOIN p ON p.code_produit = l.code_prod AND p.hotel_id = h.hotel_id
WHERE NOT EXISTS (
  SELECT 1 FROM inventory.lignes_bons_sortie lbs
  WHERE lbs.bon_sortie_id = bs.bon_sortie_id AND lbs.produit_id = p.produit_id
);

-- ----------------------------------------------------------------------------
-- 3.5 MOUVEMENTS_STOCK — ENTREE (BC RECU), SORTIE (BS LIVRE), AJUSTEMENT
-- Audit trail seul (le seed initial a deja initialise stock_actuel des produits).
-- Idempotent : verifie absence via reference_document + type + produit_id.
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     u AS (SELECT user_id, hotel_id, username FROM core.dbusers),
     p AS (SELECT produit_id, hotel_id, code_produit, stock_actuel FROM inventory.produits)
INSERT INTO inventory.mouvements_stock
  (hotel_id, produit_id, type_mouvement, quantite, prix_unitaire,
   stock_avant, stock_apres, reference_document, commentaire, user_id,
   created_at, updated_at, created_by)
SELECT
  h.hotel_id, p.produit_id, m.type_mvt, m.qte, m.prix,
  m.stock_av, m.stock_ap, m.ref, m.com, u.user_id,
  NOW(), NOW(), 'system'
FROM (VALUES
  -- NKC : ENTREE depuis BC-001 livre
  ('NKC','EAU-50',    'ENTREE', 100, 50,  200, 300, 'BC-2026-MR-000001','Reception BC reappro boissons','magasin.nkc'),
  ('NKC','EAU-150',   'ENTREE', 50,  120,  80, 130, 'BC-2026-MR-000001','Reception BC reappro boissons','magasin.nkc'),
  ('NKC','COCA-33',   'ENTREE', 80,  150, 150, 230, 'BC-2026-MR-000001','Reception BC reappro boissons','magasin.nkc'),
  -- NKC : ENTREE depuis BC-002 legumes
  ('NKC','TOMATE-KG', 'ENTREE', 30,  180,  35,  65, 'BC-2026-MR-000002','Reception legumes','magasin.nkc'),
  ('NKC','OIGNON-KG', 'ENTREE', 40,  120,  40,  80, 'BC-2026-MR-000002','Reception legumes','magasin.nkc'),
  -- NKC : ENTREE depuis BC-003 viandes
  ('NKC','BOEUF-KG',  'ENTREE', 20,  850,  25,  45, 'BC-2026-MR-000003','Reception viandes','magasin.nkc'),
  ('NKC','POULET-KG', 'ENTREE', 15,  650,  20,  35, 'BC-2026-MR-000003','Reception viandes','magasin.nkc'),
  -- NKC : SORTIE depuis BS-001 restaurant
  ('NKC','BOEUF-KG',  'SORTIE', 5,   850,  45,  40, 'BS-2026-MR-000001','Sortie restaurant','magasin.nkc'),
  ('NKC','TOMATE-KG', 'SORTIE', 8,   180,  65,  57, 'BS-2026-MR-000001','Sortie restaurant','magasin.nkc'),
  ('NKC','OIGNON-KG', 'SORTIE', 5,   120,  80,  75, 'BS-2026-MR-000001','Sortie restaurant','magasin.nkc'),
  -- NKC : SORTIE depuis BS-002 restaurant midi
  ('NKC','POULET-KG', 'SORTIE', 3,   650,  35,  32, 'BS-2026-MR-000002','Sortie restaurant midi','magasin.nkc'),
  ('NKC','EAU-50',    'SORTIE', 30,   50, 300, 270, 'BS-2026-MR-000002','Sortie restaurant midi','magasin.nkc'),
  -- NKC : SORTIE depuis BS-003 menage hygiene
  ('NKC','SAVON-50',     'SORTIE', 50,  45, 300, 250, 'BS-2026-MR-000003','Sortie menage etage 2','magasin.nkc'),
  ('NKC','SHAMPOOING-30','SORTIE', 30,  75, 180, 150, 'BS-2026-MR-000003','Sortie menage etage 2','magasin.nkc'),
  -- NKC : AJUSTEMENT inventaire physique
  ('NKC','PAPIER-TOIL', 'AJUSTEMENT', 5, 55, 250, 255, 'INV-2026-04','Inventaire physique mensuel','magasin.nkc'),
  -- NKC : PERTE
  ('NKC','POISSON-KG',  'PERTE',      2, 750,  15,  13, 'PERTE-2026-05-04','Perte fraicheur','magasin.nkc'),
  -- DKR : ENTREE depuis BC-001
  ('DKR','POISSON-DAU', 'ENTREE', 25, 900,  30,  55, 'BC-2026-MR-000001','Reception poissons','magasin.dkr'),
  ('DKR','POISSON-CALA','ENTREE', 10,1100,  12,  22, 'BC-2026-MR-000001','Reception poissons','magasin.dkr'),
  ('DKR','POULET-KG',   'ENTREE', 15, 700,  18,  33, 'BC-2026-MR-000001','Reception','magasin.dkr'),
  -- DKR : ENTREE BC-002 epicerie
  ('DKR','SUCRE-1KG',   'ENTREE', 30, 200,  35,  65, 'BC-2026-MR-000002','Reception epicerie','magasin.dkr'),
  ('DKR','SEL-1KG',     'ENTREE', 20,  90,  25,  45, 'BC-2026-MR-000002','Reception epicerie','magasin.dkr'),
  -- DKR : ENTREE BC-003 legumes bio
  ('DKR','TOMATE-BIO',  'ENTREE', 25, 220,  28,  53, 'BC-2026-MR-000003','Reception legumes bio','magasin.dkr'),
  ('DKR','SALADE',      'ENTREE', 35,  95,  40,  75, 'BC-2026-MR-000003','Reception legumes bio','magasin.dkr'),
  ('DKR','POIVRON',     'ENTREE', 20, 180,  22,  42, 'BC-2026-MR-000003','Reception legumes bio','magasin.dkr'),
  -- DKR : SORTIE BS-001 cuisine
  ('DKR','POISSON-DAU', 'SORTIE',  5, 900,  55,  50, 'BS-2026-MR-000001','Sortie cuisine','magasin.dkr'),
  ('DKR','SALADE',      'SORTIE', 10,  95,  75,  65, 'BS-2026-MR-000001','Sortie cuisine','magasin.dkr'),
  ('DKR','POIVRON',     'SORTIE',  4, 180,  42,  38, 'BS-2026-MR-000001','Sortie cuisine','magasin.dkr'),
  -- DKR : SORTIE BS-002 soir poissons
  ('DKR','POISSON-CALA','SORTIE',  3,1100,  22,  19, 'BS-2026-MR-000002','Sortie soir','magasin.dkr'),
  ('DKR','TOMATE-BIO',  'SORTIE',  5, 220,  53,  48, 'BS-2026-MR-000002','Sortie soir','magasin.dkr'),
  ('DKR','EAU-50',      'SORTIE', 25,  60, 180, 155, 'BS-2026-MR-000002','Sortie soir','magasin.dkr'),
  -- DKR : SORTIE BS-003 menage bungalows
  ('DKR','SAVON-50',    'SORTIE', 60,  55, 280, 220, 'BS-2026-MR-000003','Sortie bungalows','magasin.dkr'),
  ('DKR','SHAMPOOING-30','SORTIE',40,  85, 160, 120, 'BS-2026-MR-000003','Sortie bungalows','magasin.dkr'),
  ('DKR','SERVIETTE',   'SORTIE', 10, 780,  55,  45, 'BS-2026-MR-000003','Remplacement','magasin.dkr'),
  -- DKR : AJUSTEMENT
  ('DKR','PAPIER-TOIL', 'AJUSTEMENT', -3, 65, 220, 217, 'INV-2026-04','Ecart inventaire','magasin.dkr'),
  -- DKR : PERTE
  ('DKR','TOMATE-BIO',  'PERTE',       2,220,  48,  46, 'PERTE-2026-05-04','Perte fraicheur','magasin.dkr')
) AS m(hcode, code_prod, type_mvt, qte, prix, stock_av, stock_ap, ref, com, uname)
JOIN h ON h.hotel_code = m.hcode
JOIN p ON p.code_produit = m.code_prod AND p.hotel_id = h.hotel_id
JOIN u ON u.username = m.uname AND u.hotel_id = h.hotel_id
WHERE NOT EXISTS (
  SELECT 1 FROM inventory.mouvements_stock ms
  WHERE ms.hotel_id = h.hotel_id
    AND ms.produit_id = p.produit_id
    AND ms.reference_document = m.ref
    AND ms.type_mouvement = m.type_mvt
);

-- ----------------------------------------------------------------------------
-- 3.6 TYPES_SERVICES_HOTELIERS (Tour 51) — 4 par hotel
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO inventory.types_services_hoteliers
  (hotel_id, code, nom, description, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, t.code, t.nom, t.descr, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','BIEN-ETRE',     'Bien-etre',     'Spa, massage, sauna, soins du corps'),
  ('NKC','TRANSPORT',     'Transport',     'Taxi, transfert aeroport, location vehicule'),
  ('NKC','BLANCHISSERIE', 'Blanchisserie', 'Lavage, repassage, pressing'),
  ('NKC','AUTRE',         'Autre',         'Services divers (room service, baby-sitting...)'),
  ('DKR','BIEN-ETRE',     'Bien-etre',     'Spa, massage, hammam, soins'),
  ('DKR','TRANSPORT',     'Transport',     'Taxi, transfert, excursion'),
  ('DKR','BLANCHISSERIE', 'Blanchisserie', 'Lavage, repassage'),
  ('DKR','AUTRE',         'Autre',         'Services divers')
) AS t(hcode, code, nom, descr)
JOIN h ON h.hotel_code = t.hcode
ON CONFLICT (hotel_id, code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3.7 SERVICES_HOTELIERS (Tour 51) — 8 par hotel
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     tsh AS (SELECT type_service_id, hotel_id, code FROM inventory.types_services_hoteliers)
INSERT INTO inventory.services_hoteliers
  (hotel_id, type_service_id, code, nom, description, prix_unitaire, unite, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, tsh.type_service_id, s.code, s.nom, s.descr, s.prix, s.unite, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','BIEN-ETRE',     'SPA-MASSAGE-1H',   'Massage spa 1h',           'Massage relaxant 1 heure',           8000,  'prestation'),
  ('NKC','BIEN-ETRE',     'SPA-SAUNA',        'Acces sauna',              'Acces sauna 1 heure',                3500,  'heure'),
  ('NKC','TRANSPORT',     'TRANSF-AEROPORT',  'Transfert aeroport',       'Transfert hotel-aeroport (4 pax)',   5000,  'prestation'),
  ('NKC','TRANSPORT',     'TAXI-VILLE',       'Taxi en ville',            'Course taxi en ville',               1500,  'prestation'),
  ('NKC','BLANCHISSERIE', 'BLANCH-KG',        'Blanchisserie au kg',      'Lavage repassage par kg',             400,  'kg'),
  ('NKC','BLANCHISSERIE', 'PRESSING-COMPLET', 'Pressing costume',         'Pressing complet costume 2 pieces',  1200,  'prestation'),
  ('NKC','AUTRE',         'ROOM-SERVICE',     'Service en chambre',       'Supplement service en chambre',       500,  'prestation'),
  ('NKC','AUTRE',         'BABY-SITTING',     'Baby-sitting',             'Garde enfant 1h',                    2500,  'heure'),
  ('DKR','BIEN-ETRE',     'SPA-MASSAGE-1H',   'Massage spa 1h',           'Massage relaxant face ocean 1h',     9500,  'prestation'),
  ('DKR','BIEN-ETRE',     'SPA-HAMMAM',       'Hammam traditionnel',      'Acces hammam + savon noir',          4500,  'prestation'),
  ('DKR','TRANSPORT',     'TRANSF-AEROPORT',  'Transfert aeroport',       'Transfert hotel-aeroport Dakhla',    6500,  'prestation'),
  ('DKR','TRANSPORT',     'EXCURSION-DESERT', 'Excursion desert',         'Excursion desert 4 heures',         18000,  'prestation'),
  ('DKR','BLANCHISSERIE', 'BLANCH-KG',        'Blanchisserie au kg',      'Lavage repassage par kg',             500,  'kg'),
  ('DKR','BLANCHISSERIE', 'PRESSING-COMPLET', 'Pressing costume',         'Pressing costume 2 pieces',          1500,  'prestation'),
  ('DKR','AUTRE',         'ROOM-SERVICE',     'Service en chambre',       'Supplement service en chambre',       700,  'prestation'),
  ('DKR','AUTRE',         'LOCATION-VELO',    'Location velo',            'Location velo 1 journee',            2000,  'jour')
) AS s(hcode, tcode, code, nom, descr, prix, unite)
JOIN h ON h.hotel_code = s.hcode
JOIN tsh ON tsh.code = s.tcode AND tsh.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, code) DO NOTHING;

\echo '=== INVENTORY extras seeded ==='
SELECT 'bons_commande'             AS t, COUNT(*) FROM inventory.bons_commande UNION ALL
SELECT 'lignes_bons_commande',         COUNT(*) FROM inventory.lignes_bons_commande UNION ALL
SELECT 'bons_sortie',                  COUNT(*) FROM inventory.bons_sortie UNION ALL
SELECT 'lignes_bons_sortie',           COUNT(*) FROM inventory.lignes_bons_sortie UNION ALL
SELECT 'mouvements_stock',             COUNT(*) FROM inventory.mouvements_stock UNION ALL
SELECT 'types_services_hoteliers',     COUNT(*) FROM inventory.types_services_hoteliers UNION ALL
SELECT 'services_hoteliers',           COUNT(*) FROM inventory.services_hoteliers;
