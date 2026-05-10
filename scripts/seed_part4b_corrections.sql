-- ============================================================================
-- SEED Part 4b — Corrections noms tables/colonnes
-- ============================================================================

-- ====================================================
-- 16. RESTAURANT.CATEGORIES_MENUS (avec S final, 4 par hôtel = 8)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO restaurant.categories_menus (hotel_id, code_categorie, nom_categorie, description, ordre_affichage, actif, created_at, updated_at, created_by)
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
-- 17. RESTAURANT.ARTICLES_MENUS (15 par hôtel = 30)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     cat AS (SELECT categorie_id, hotel_id, code_categorie FROM restaurant.categories_menus)
INSERT INTO restaurant.articles_menus (hotel_id, categorie_id, code_article, nom_article, description, prix_vente, statut, ordre_affichage, created_at, updated_at, created_by)
SELECT h.hotel_id, cat.categorie_id, a.code, a.nom, a.descr, a.prix, 'DISPONIBLE', a.ordre, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','ENTREE','SAL-CESAR','Salade Cesar','Laitue, croutons, parmesan',1500,1),
  ('NKC','ENTREE','HOUMOUS','Houmous traditionnel','Pois chiches, tahini, citron',1200,2),
  ('NKC','ENTREE','SOUPE-LEG','Soupe de legumes','Legumes de saison',900,3),
  ('NKC','ENTREE','SAMOUSSA','Samoussa au boeuf','3 pieces',1300,4),
  ('NKC','PLAT','MECHOUI','Mechoui d agneau','Quart agneau roti, riz',6500,1),
  ('NKC','PLAT','POISSON-GRILLE','Poisson grille','Poisson, riz, legumes',5500,2),
  ('NKC','PLAT','POULET-CITRON','Poulet citron confit','Poulet, citron, olives',4800,3),
  ('NKC','PLAT','COUSCOUS-ROYAL','Couscous royal','Mouton, poulet, merguez',5800,4),
  ('NKC','PLAT','RIZ-POISSON','Riz au poisson','Specialite locale',4500,5),
  ('NKC','DESSERT','TIRAMISU','Tiramisu maison','Cafe, mascarpone',1800,1),
  ('NKC','DESSERT','SALADE-FRUITS','Salade de fruits','Fruits saison',1200,2),
  ('NKC','DESSERT','BAKLAVA','Baklava au miel','Patisserie traditionnelle',1500,3),
  ('NKC','BOISSON','EAU-MIN','Eau minerale 50cl','',300,1),
  ('NKC','BOISSON','THE-MENTHE','The a la menthe','',500,2),
  ('NKC','BOISSON','CAFE-EXP','Cafe espresso','',600,3),
  ('DKR','ENTREE','HUITRES','Huitres de Dakhla','6 pieces',2500,1),
  ('DKR','ENTREE','SAL-CESAR','Salade Cesar','Laitue, croutons',1700,2),
  ('DKR','ENTREE','TARTARE-DAU','Tartare daurade','Daurade, citron',2800,3),
  ('DKR','ENTREE','SOUPE-POISSON','Soupe de poisson','Aux poissons locaux',1900,4),
  ('DKR','PLAT','GRILLADE-POISSON','Grillade poissons','Daurade, calamar, crevettes',8500,1),
  ('DKR','PLAT','PAELLA','Paella royale','Riz, fruits de mer, poulet',7200,2),
  ('DKR','PLAT','POULPE','Poulpe a la galicienne','Poulpe grille',6800,3),
  ('DKR','PLAT','LANGOUSTE','Langouste grillee','A partir 500g',12000,4),
  ('DKR','PLAT','MECHOUI','Mechoui de mouton','1/4 mouton, riz',7500,5),
  ('DKR','DESSERT','PASTILLA','Pastilla aux amandes','Patisserie marocaine',2200,1),
  ('DKR','DESSERT','SALADE-FRUITS','Salade fruits exotiques','Mangue, ananas',1800,2),
  ('DKR','DESSERT','CREME-CARAMEL','Creme caramel maison','',1500,3),
  ('DKR','BOISSON','EAU-MIN','Eau minerale 50cl','',400,1),
  ('DKR','BOISSON','THE-MENTHE','The a la menthe','',600,2),
  ('DKR','BOISSON','JUS-MANGUE','Jus de mangue frais','',900,3)
) AS a(hcode, ccode, code, nom, descr, prix, ordre)
JOIN h ON h.hotel_code = a.hcode
JOIN cat ON cat.code_categorie = a.ccode AND cat.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, code_article) DO NOTHING;

-- ====================================================
-- 19. MENAGE.PERSONNEL (specialites au lieu de fonction)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO menage.personnel (hotel_id, numero_employe, prenom, nom, email, telephone, specialites, date_embauche, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, p.num, p.prenom, p.nom, p.email, p.tel, p.spec, p.date::date, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','MEN-NKC-001','Mariam','Mint Cheikh','m.cheikh.menage@cityhotel.mr','+22248001001','Femme de chambre senior, formation receptionniste','2023-01-15'),
  ('NKC','MEN-NKC-002','Aicha','Mint Brahim','a.brahim.menage@cityhotel.mr','+22248001002','Femme de chambre','2023-06-20'),
  ('NKC','MEN-NKC-003','Hawa','Diallo','h.diallo.menage@cityhotel.mr','+22248001003','Femme de chambre','2024-02-10'),
  ('NKC','MEN-NKC-004','Boubacar','Sow','b.sow.menage@cityhotel.mr','+22248001004','Agent maintenance, plomberie, electricite','2022-11-01'),
  ('NKC','MEN-NKC-005','Mamadou','Diop','m.diop.menage@cityhotel.mr','+22248001005','Agent menage commun, espaces publics','2024-09-15'),
  ('DKR','MEN-DKR-001','Aminata','Tall','a.tall.menage@cityhotel.mr','+22248002001','Femme de chambre senior, supervision','2023-03-22'),
  ('DKR','MEN-DKR-002','Khadija','Mint Sidi','k.sidi.menage@cityhotel.mr','+22248002002','Femme de chambre','2023-08-15'),
  ('DKR','MEN-DKR-003','Mariama','Diop','m.diop.dkr@cityhotel.mr','+22248002003','Femme de chambre','2024-04-20'),
  ('DKR','MEN-DKR-004','Cheikh','Ahmed','c.ahmed.menage@cityhotel.mr','+22248002004','Agent maintenance, climatisation','2022-12-05'),
  ('DKR','MEN-DKR-005','Yacine','Wade','y.wade.menage@cityhotel.mr','+22248002005','Agent menage commun, jardinage','2024-08-01')
) AS p(hcode, num, prenom, nom, email, tel, spec, date)
JOIN h ON h.hotel_code = p.hcode
ON CONFLICT (hotel_id, numero_employe) DO NOTHING;

-- ====================================================
-- 20. FINANCE.NUMEROTATION_SEQUENCE (type + last_value)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO finance.numerotation_sequence (hotel_id, type, exercice, last_value, created_at, updated_at, created_by)
SELECT h.hotel_id, t.typ, 2026, t.val, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','FACTURE',0),
  ('NKC','PAIEMENT',0),
  ('NKC','BC',0),
  ('NKC','BS',0),
  ('DKR','FACTURE',0),
  ('DKR','PAIEMENT',0),
  ('DKR','BC',0),
  ('DKR','BS',0)
) AS t(hcode, typ, val)
JOIN h ON h.hotel_code = t.hcode
ON CONFLICT (hotel_id, type, exercice) DO NOTHING;

\echo '=== Corrections appliquees ==='
SELECT 'categories_menus' AS t, COUNT(*) FROM restaurant.categories_menus UNION ALL
SELECT 'articles_menus',         COUNT(*) FROM restaurant.articles_menus UNION ALL
SELECT 'personnel',              COUNT(*) FROM menage.personnel UNION ALL
SELECT 'numerotation_sequence',  COUNT(*) FROM finance.numerotation_sequence;
