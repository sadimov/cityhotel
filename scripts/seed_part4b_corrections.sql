-- ============================================================================
-- SEED Part 4b — Corrections noms tables/colonnes restaurant + menage + finance
-- (V2 — fix bug categories_menus colonnes : nom + ordre, pas code_categorie)
-- ============================================================================

-- ====================================================
-- restaurant.categories_menus (nom, ordre — pas code_categorie)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels WHERE hotel_code IN ('NKC','DKR'))
INSERT INTO restaurant.categories_menus (hotel_id, nom, description, ordre, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, c.nom, c.descr, c.ordre, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','Entrees','Salades, soupes, mezze',1),
  ('NKC','Plats principaux','Viandes, poissons',2),
  ('NKC','Desserts','Patisseries, fruits',3),
  ('NKC','Boissons','Eaux, sodas, jus',4),
  ('DKR','Entrees','Salades, fruits de mer',1),
  ('DKR','Plats principaux','Specialites poissons',2),
  ('DKR','Desserts','Patisseries marocaines',3),
  ('DKR','Boissons','Eaux, sodas, jus',4)
) AS c(hcode, nom, descr, ordre)
JOIN h ON h.hotel_code = c.hcode;

-- ====================================================
-- restaurant.articles_menus (nom + prix + disponible + statut ACTIF)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels WHERE hotel_code IN ('NKC','DKR')),
     cat AS (SELECT categorie_id, hotel_id, nom FROM restaurant.categories_menus)
INSERT INTO restaurant.articles_menus (hotel_id, categorie_id, code_article, nom, description, prix, statut, disponible, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, cat.categorie_id, a.code, a.nom, a.descr, a.prix, 'ACTIF', true, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','Entrees','SAL-CESAR','Salade Cesar','Laitue, croutons, parmesan',1500),
  ('NKC','Entrees','HOUMOUS','Houmous','Pois chiches, tahini',1200),
  ('NKC','Entrees','SOUPE-LEG','Soupe legumes','Legumes saison',900),
  ('NKC','Entrees','SAMOUSSA','Samoussa boeuf','3 pieces',1300),
  ('NKC','Plats principaux','MECHOUI','Mechoui agneau','Quart agneau, riz',6500),
  ('NKC','Plats principaux','POISSON-G','Poisson grille','Poisson, riz, legumes',5500),
  ('NKC','Plats principaux','POULET-CIT','Poulet citron','Poulet, citron, olives',4800),
  ('NKC','Plats principaux','COUSCOUS','Couscous royal','Mouton, poulet, merguez',5800),
  ('NKC','Plats principaux','RIZ-POIS','Riz au poisson','Specialite locale',4500),
  ('NKC','Desserts','TIRAMISU','Tiramisu','Cafe, mascarpone',1800),
  ('NKC','Desserts','SAL-FRUITS','Salade fruits','Saison',1200),
  ('NKC','Desserts','BAKLAVA','Baklava miel','Patisserie',1500),
  ('NKC','Boissons','EAU-MIN','Eau minerale 50cl','',300),
  ('NKC','Boissons','THE-MENT','The menthe','',500),
  ('NKC','Boissons','CAFE','Cafe espresso','',600),
  ('DKR','Entrees','HUITRES','Huitres Dakhla','6 pieces',2500),
  ('DKR','Entrees','SAL-CESAR','Salade Cesar','',1700),
  ('DKR','Entrees','TARTARE','Tartare daurade','Daurade, citron',2800),
  ('DKR','Entrees','SOUPE-POIS','Soupe poisson','Poissons locaux',1900),
  ('DKR','Plats principaux','GRILLADE','Grillade poissons','Daurade, calamar, crevettes',8500),
  ('DKR','Plats principaux','PAELLA','Paella royale','Riz, fruits mer',7200),
  ('DKR','Plats principaux','POULPE','Poulpe galicienne','Poulpe grille',6800),
  ('DKR','Plats principaux','LANGOUSTE','Langouste grillee','500g+',12000),
  ('DKR','Plats principaux','MECHOUI','Mechoui mouton','Quart mouton',7500),
  ('DKR','Desserts','PASTILLA','Pastilla amandes','Patisserie marocaine',2200),
  ('DKR','Desserts','SAL-FRUITS','Salade fruits exotiques','Mangue, ananas',1800),
  ('DKR','Desserts','CREME','Creme caramel','',1500),
  ('DKR','Boissons','EAU-MIN','Eau minerale 50cl','',400),
  ('DKR','Boissons','THE-MENT','The menthe','',600),
  ('DKR','Boissons','JUS-MAN','Jus mangue','',900)
) AS a(hcode, ccnom, code, nom, descr, prix)
JOIN h ON h.hotel_code = a.hcode
JOIN cat ON cat.nom = a.ccnom AND cat.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, code_article) DO NOTHING;

-- ====================================================
-- menage.personnel
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels WHERE hotel_code IN ('NKC','DKR'))
INSERT INTO menage.personnel (hotel_id, numero_employe, prenom, nom, email, telephone, specialites, date_embauche, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, p.num, p.prenom, p.nom, p.email, p.tel, p.spec, p.date::date, true, NOW(), NOW(), 'system'
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
) AS p(hcode, num, prenom, nom, email, tel, spec, date)
JOIN h ON h.hotel_code = p.hcode
ON CONFLICT (hotel_id, numero_employe) DO NOTHING;

-- ====================================================
-- finance.numerotation_sequence (type FACT/PAY/BC/BS/RES, last_value)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels WHERE hotel_code IN ('NKC','DKR'))
INSERT INTO finance.numerotation_sequence (hotel_id, type, exercice, last_value, created_at, updated_at, created_by)
SELECT h.hotel_id, t.typ, 2026, 0, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','FACT'),('NKC','PAY'),('NKC','BC'),('NKC','BS'),('NKC','RES'),
  ('DKR','FACT'),('DKR','PAY'),('DKR','BC'),('DKR','BS'),('DKR','RES')
) AS t(hcode, typ)
JOIN h ON h.hotel_code = t.hcode
ON CONFLICT (hotel_id, type, exercice) DO NOTHING;

\echo '=== Part 4b corrections OK ==='
SELECT 'categories_menus' AS t, COUNT(*) FROM restaurant.categories_menus UNION ALL
SELECT 'articles_menus',         COUNT(*) FROM restaurant.articles_menus UNION ALL
SELECT 'personnel',              COUNT(*) FROM menage.personnel UNION ALL
SELECT 'numerotation_sequence',  COUNT(*) FROM finance.numerotation_sequence;
