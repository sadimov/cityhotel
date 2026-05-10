-- ============================================================================
-- SEED Part 4 — INVENTORY + RESTAURANT + MENAGE + FINANCE base
-- ============================================================================

-- ====================================================
-- 13. INVENTORY.CATEGORIES_PRODUITS (5 par hôtel = 10)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO inventory.categories_produits (hotel_id, code_categorie, nom_categorie, description, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, c.code, c.nom, c.descr, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','BOISSON','Boissons','Eau, sodas, jus, alcools'),
  ('NKC','EPICERIE','Epicerie','Epices, conserves, sucre, sel'),
  ('NKC','VIANDE','Viandes et poissons','Boeuf, mouton, poulet, poisson'),
  ('NKC','LEGUMES','Legumes et fruits','Frais quotidien'),
  ('NKC','HYGIENE','Hygiene chambre','Savons, serviettes, papier toilette'),
  ('DKR','BOISSON','Boissons','Eau, sodas, jus, alcools'),
  ('DKR','EPICERIE','Epicerie','Epices, conserves, sucre, sel'),
  ('DKR','VIANDE','Viandes et poissons','Specialite poisson Dakhla'),
  ('DKR','LEGUMES','Legumes et fruits','Frais quotidien'),
  ('DKR','HYGIENE','Hygiene chambre','Savons, serviettes, papier toilette')
) AS c(hcode, code, nom, descr)
JOIN h ON h.hotel_code = c.hcode;

-- ====================================================
-- 14. INVENTORY.FOURNISSEURS (5 par hôtel = 10)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO inventory.fournisseurs (hotel_id, nom_fournisseur, contact_principal, telephone, email, adresse, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, f.nom, f.contact, f.tel, f.email, f.adr, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','Marche Sebkha','Mohamed Diop','+22247001001','marche.sebkha@gmail.com','Marche Sebkha NKC'),
  ('NKC','Boissons Distrib SA','Sidi Moktar','+22247001002','contact@boissonsdistrib.mr','Zone industrielle NKC'),
  ('NKC','Marche Aux Poissons','Brahim Ba','+22247001003','poissons.nkc@gmail.com','Port de peche NKC'),
  ('NKC','Hygiene Pro NKC','Aicha Ahmed','+22247001004','contact@hygienepro.mr','Tevragh Zeina NKC'),
  ('NKC','Boucherie Centrale','Yacoub Sy','+22247001005','boucherie.cnt@gmail.com','Centre ville NKC'),
  ('DKR','Pecheries Dakhla','Cheikh Wade','+22247002001','pecheries@dakhla.mr','Port Dakhla'),
  ('DKR','Marche Central DKR','Halima Sidi','+22247002002','marche.dkr@gmail.com','Centre ville Dakhla'),
  ('DKR','Boissons Atlantique','Mamadou Diallo','+22247002003','contact@boissonsatl.mr','Zone industrielle DKR'),
  ('DKR','Hygiene Sahel','Khadija Ba','+22247002004','contact@hygienesahel.mr','Centre Dakhla'),
  ('DKR','Maraicher Bio Dakhla','Aboubacar Ahmed','+22247002005','bio.dkr@gmail.com','Peripherie Dakhla')
) AS f(hcode, nom, contact, tel, email, adr)
JOIN h ON h.hotel_code = f.hcode;

-- ====================================================
-- 15. INVENTORY.PRODUITS (15 par hôtel = 30, mix catégories)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     cat AS (SELECT categorie_id, hotel_id, code_categorie FROM inventory.categories_produits),
     fr AS (SELECT fournisseur_id, hotel_id, nom_fournisseur FROM inventory.fournisseurs)
INSERT INTO inventory.produits (hotel_id, categorie_id, fournisseur_principal_id, code_produit, nom_produit, unite_mesure, prix_unitaire, stock_actuel, seuil_alerte, seuil_critique, est_facturable, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, cat.categorie_id, fr.fournisseur_id, p.code, p.nom, p.unite, p.prix, p.stock, p.alerte, p.crit, true, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','BOISSON','Boissons Distrib SA','EAU-50','Eau minerale 50cl','bouteille',50,200,50,20),
  ('NKC','BOISSON','Boissons Distrib SA','EAU-150','Eau minerale 1.5L','bouteille',120,80,30,10),
  ('NKC','BOISSON','Boissons Distrib SA','COCA-33','Coca-Cola 33cl','canette',150,150,40,15),
  ('NKC','EPICERIE','Marche Sebkha','SUCRE-1KG','Sucre cristal 1kg','sac',180,40,15,5),
  ('NKC','EPICERIE','Marche Sebkha','SEL-1KG','Sel fin 1kg','sac',80,30,10,5),
  ('NKC','VIANDE','Boucherie Centrale','BOEUF-KG','Boeuf 1kg','kg',850,25,10,3),
  ('NKC','VIANDE','Boucherie Centrale','POULET-KG','Poulet 1kg','kg',650,20,8,2),
  ('NKC','VIANDE','Marche Aux Poissons','POISSON-KG','Poisson frais 1kg','kg',750,15,6,2),
  ('NKC','LEGUMES','Marche Sebkha','TOMATE-KG','Tomate 1kg','kg',180,35,10,3),
  ('NKC','LEGUMES','Marche Sebkha','OIGNON-KG','Oignon 1kg','kg',120,40,12,4),
  ('NKC','LEGUMES','Marche Sebkha','POMME-DE-TERRE','Pomme de terre 1kg','kg',150,50,15,5),
  ('NKC','HYGIENE','Hygiene Pro NKC','SAVON-50','Savon hotel 50g','unite',45,300,80,30),
  ('NKC','HYGIENE','Hygiene Pro NKC','PAPIER-TOIL','Papier toilette double','rouleau',55,250,60,20),
  ('NKC','HYGIENE','Hygiene Pro NKC','SHAMPOOING-30','Shampooing hotel 30ml','unite',75,180,50,15),
  ('NKC','HYGIENE','Hygiene Pro NKC','SERVIETTE','Serviette eponge','unite',680,60,15,5),
  ('DKR','BOISSON','Boissons Atlantique','EAU-50','Eau minerale 50cl','bouteille',60,180,50,20),
  ('DKR','BOISSON','Boissons Atlantique','EAU-150','Eau minerale 1.5L','bouteille',140,70,30,10),
  ('DKR','BOISSON','Boissons Atlantique','COCA-33','Coca-Cola 33cl','canette',180,140,40,15),
  ('DKR','EPICERIE','Marche Central DKR','SUCRE-1KG','Sucre cristal 1kg','sac',200,35,15,5),
  ('DKR','EPICERIE','Marche Central DKR','SEL-1KG','Sel fin 1kg','sac',90,25,10,5),
  ('DKR','VIANDE','Pecheries Dakhla','POISSON-DAU','Daurade fraiche 1kg','kg',900,30,10,3),
  ('DKR','VIANDE','Pecheries Dakhla','POISSON-CALA','Calamars 1kg','kg',1100,12,5,2),
  ('DKR','VIANDE','Marche Central DKR','POULET-KG','Poulet 1kg','kg',700,18,8,2),
  ('DKR','LEGUMES','Maraicher Bio Dakhla','TOMATE-BIO','Tomate bio 1kg','kg',220,28,10,3),
  ('DKR','LEGUMES','Maraicher Bio Dakhla','SALADE','Salade verte','unite',95,40,12,4),
  ('DKR','LEGUMES','Maraicher Bio Dakhla','POIVRON','Poivrons 1kg','kg',180,22,8,3),
  ('DKR','HYGIENE','Hygiene Sahel','SAVON-50','Savon hotel 50g','unite',55,280,80,30),
  ('DKR','HYGIENE','Hygiene Sahel','PAPIER-TOIL','Papier toilette double','rouleau',65,220,60,20),
  ('DKR','HYGIENE','Hygiene Sahel','SHAMPOOING-30','Shampooing hotel 30ml','unite',85,160,50,15),
  ('DKR','HYGIENE','Hygiene Sahel','SERVIETTE','Serviette eponge','unite',780,55,15,5)
) AS p(hcode, ccode, fnom, code, nom, unite, prix, stock, alerte, crit)
JOIN h ON h.hotel_code = p.hcode
JOIN cat ON cat.code_categorie = p.ccode AND cat.hotel_id = h.hotel_id
JOIN fr ON fr.nom_fournisseur = p.fnom AND fr.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, code_produit) DO NOTHING;

\echo '=== INVENTORY seeded ==='
SELECT 'categories_produits' AS t, COUNT(*) FROM inventory.categories_produits UNION ALL
SELECT 'fournisseurs',             COUNT(*) FROM inventory.fournisseurs UNION ALL
SELECT 'produits',                 COUNT(*) FROM inventory.produits;

-- ====================================================
-- 16. RESTAURANT.CATEGORIES_MENU (4 par hôtel = 8)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO restaurant.categories_menu (hotel_id, code_categorie, nom_categorie, description, ordre_affichage, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, c.code, c.nom, c.descr, c.ordre, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','ENTREE','Entrees','Salades, soupes, mezze',1),
  ('NKC','PLAT','Plats principaux','Viandes, poissons, accompagnements',2),
  ('NKC','DESSERT','Desserts','Patisseries, fruits, glaces',3),
  ('NKC','BOISSON','Boissons','Eaux, sodas, jus, the, cafe',4),
  ('DKR','ENTREE','Entrees','Salades fraicheur, fruits de mer',1),
  ('DKR','PLAT','Plats principaux','Specialites poissons et viandes',2),
  ('DKR','DESSERT','Desserts','Patisseries marocaines',3),
  ('DKR','BOISSON','Boissons','Eaux, sodas, jus, the, cafe',4)
) AS c(hcode, code, nom, descr, ordre)
JOIN h ON h.hotel_code = c.hcode;

-- ====================================================
-- 17. RESTAURANT.ARTICLES_MENU (15 par hôtel = 30)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     cat AS (SELECT categorie_id, hotel_id, code_categorie FROM restaurant.categories_menu)
INSERT INTO restaurant.articles_menu (hotel_id, categorie_id, code_article, nom_article, description, prix_vente, statut, ordre_affichage, created_at, updated_at, created_by)
SELECT h.hotel_id, cat.categorie_id, a.code, a.nom, a.descr, a.prix, 'DISPONIBLE', a.ordre, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','ENTREE','SAL-CESAR','Salade Cesar','Laitue, croutons, parmesan, sauce Cesar',1500,1),
  ('NKC','ENTREE','HOUMOUS','Houmous traditionnel','Pois chiches, tahini, citron, huile olive',1200,2),
  ('NKC','ENTREE','SOUPE-LEG','Soupe de legumes','Legumes de saison',900,3),
  ('NKC','ENTREE','SAMOUSSA','Samoussa au boeuf','3 pieces, sauce piquante',1300,4),
  ('NKC','PLAT','MECHOUI','Mechoui d''agneau','1/4 d''agneau roti aux herbes, riz',6500,1),
  ('NKC','PLAT','POISSON-GRILLE','Poisson grille','Poisson local, riz, legumes',5500,2),
  ('NKC','PLAT','POULET-CITRON','Poulet au citron confit','Poulet, citron, olives, riz',4800,3),
  ('NKC','PLAT','COUSCOUS-ROYAL','Couscous royal','Mouton, poulet, merguez, legumes',5800,4),
  ('NKC','PLAT','RIZ-POISSON','Riz au poisson','Specialite locale',4500,5),
  ('NKC','DESSERT','TIRAMISU','Tiramisu maison','Cafe, mascarpone, biscuits',1800,1),
  ('NKC','DESSERT','SALADE-FRUITS','Salade de fruits','Fruits frais de saison',1200,2),
  ('NKC','DESSERT','BAKLAVA','Baklava au miel','Patisserie traditionnelle',1500,3),
  ('NKC','BOISSON','EAU-MIN','Eau minerale 50cl','',300,1),
  ('NKC','BOISSON','THE-MENTHE','The a la menthe','',500,2),
  ('NKC','BOISSON','CAFE-EXP','Cafe espresso','',600,3),
  ('DKR','ENTREE','HUITRES','Huitres de Dakhla','6 pieces fraiches',2500,1),
  ('DKR','ENTREE','SAL-CESAR','Salade Cesar','Laitue, croutons, parmesan',1700,2),
  ('DKR','ENTREE','TARTARE-DAU','Tartare de daurade','Daurade fraiche, citron',2800,3),
  ('DKR','ENTREE','SOUPE-POISSON','Soupe de poisson','Soupe maison aux poissons locaux',1900,4),
  ('DKR','PLAT','GRILLADE-POISSON','Grillade poissons assortis','Daurade, calamar, crevettes',8500,1),
  ('DKR','PLAT','PAELLA','Paella royale','Riz, fruits de mer, poulet',7200,2),
  ('DKR','PLAT','POULPE','Poulpe a la galicienne','Poulpe grille, paprika, huile',6800,3),
  ('DKR','PLAT','LANGOUSTE','Langouste grillee','Langouste a partir de 500g',12000,4),
  ('DKR','PLAT','MECHOUI','Mechoui de mouton','1/4 mouton, riz',7500,5),
  ('DKR','DESSERT','PASTILLA','Pastilla aux amandes','Patisserie marocaine',2200,1),
  ('DKR','DESSERT','SALADE-FRUITS','Salade de fruits exotiques','Mangue, ananas, papaye',1800,2),
  ('DKR','DESSERT','CREME-CARAMEL','Creme caramel maison','',1500,3),
  ('DKR','BOISSON','EAU-MIN','Eau minerale 50cl','',400,1),
  ('DKR','BOISSON','THE-MENTHE','The a la menthe','',600,2),
  ('DKR','BOISSON','JUS-MANGUE','Jus de mangue frais','',900,3)
) AS a(hcode, ccode, code, nom, descr, prix, ordre)
JOIN h ON h.hotel_code = a.hcode
JOIN cat ON cat.code_categorie = a.ccode AND cat.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, code_article) DO NOTHING;

-- ====================================================
-- 18. RESTAURANT.CAISSES (1 par hôtel = 2)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     u AS (SELECT user_id, hotel_id, username FROM core.dbusers WHERE username LIKE 'restau.%')
INSERT INTO restaurant.caisses (hotel_id, code_caisse, nom_caisse, statut, fond_caisse_initial, user_ouverture_id, date_ouverture, created_at, updated_at, created_by)
SELECT h.hotel_id, c.code, c.nom, 'OUVERTE', c.fond, u.user_id, NOW(), NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','CAISSE-PRINC','Caisse principale restaurant',5000),
  ('DKR','CAISSE-PRINC','Caisse principale restaurant',5000)
) AS c(hcode, code, nom, fond)
JOIN h ON h.hotel_code = c.hcode
JOIN u ON u.hotel_id = h.hotel_id;

\echo '=== RESTAURANT seeded ==='
SELECT 'categories_menu' AS t, COUNT(*) FROM restaurant.categories_menu UNION ALL
SELECT 'articles_menu',         COUNT(*) FROM restaurant.articles_menu UNION ALL
SELECT 'caisses',               COUNT(*) FROM restaurant.caisses;

-- ====================================================
-- 19. MENAGE.PERSONNEL (5 par hôtel = 10)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO menage.personnel (hotel_id, numero_employe, prenom, nom, email, telephone, fonction, date_embauche, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, p.num, p.prenom, p.nom, p.email, p.tel, p.fonction, p.date::date, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','MEN-NKC-001','Mariam','Mint Cheikh','m.cheikh.menage@cityhotel.mr','+22248001001','Femme de chambre senior','2023-01-15'),
  ('NKC','MEN-NKC-002','Aicha','Mint Brahim','a.brahim.menage@cityhotel.mr','+22248001002','Femme de chambre','2023-06-20'),
  ('NKC','MEN-NKC-003','Hawa','Diallo','h.diallo.menage@cityhotel.mr','+22248001003','Femme de chambre','2024-02-10'),
  ('NKC','MEN-NKC-004','Boubacar','Sow','b.sow.menage@cityhotel.mr','+22248001004','Agent maintenance','2022-11-01'),
  ('NKC','MEN-NKC-005','Mamadou','Diop','m.diop.menage@cityhotel.mr','+22248001005','Agent menage commun','2024-09-15'),
  ('DKR','MEN-DKR-001','Aminata','Tall','a.tall.menage@cityhotel.mr','+22248002001','Femme de chambre senior','2023-03-22'),
  ('DKR','MEN-DKR-002','Khadija','Mint Sidi','k.sidi.menage@cityhotel.mr','+22248002002','Femme de chambre','2023-08-15'),
  ('DKR','MEN-DKR-003','Mariama','Diop','m.diop.dkr@cityhotel.mr','+22248002003','Femme de chambre','2024-04-20'),
  ('DKR','MEN-DKR-004','Cheikh','Ahmed','c.ahmed.menage@cityhotel.mr','+22248002004','Agent maintenance','2022-12-05'),
  ('DKR','MEN-DKR-005','Yacine','Wade','y.wade.menage@cityhotel.mr','+22248002005','Agent menage commun','2024-08-01')
) AS p(hcode, num, prenom, nom, email, tel, fonction, date)
JOIN h ON h.hotel_code = p.hcode
ON CONFLICT (hotel_id, numero_employe) DO NOTHING;

\echo '=== MENAGE seeded ==='
SELECT 'personnel' AS t, COUNT(*) FROM menage.personnel;

-- ====================================================
-- 20. FINANCE.NUMEROTATION_SEQUENCE (4 types par hôtel = 8)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO finance.numerotation_sequence (hotel_id, type_sequence, exercice, derniere_valeur, created_at, updated_at, created_by)
SELECT h.hotel_id, t.type, 2026, t.val, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','FACTURE',0),
  ('NKC','PAIEMENT',0),
  ('NKC','BC',0),
  ('NKC','BS',0),
  ('DKR','FACTURE',0),
  ('DKR','PAIEMENT',0),
  ('DKR','BC',0),
  ('DKR','BS',0)
) AS t(hcode, type, val)
JOIN h ON h.hotel_code = t.hcode;

\echo '=== FINANCE base seeded ==='
SELECT 'numerotation_sequence' AS t, COUNT(*) FROM finance.numerotation_sequence;
