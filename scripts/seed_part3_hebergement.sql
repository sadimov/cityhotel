-- ============================================================================
-- SEED Part 3 — HEBERGEMENT (types + chambres + tarifs + réservations + nuitées)
-- ============================================================================

-- 7. TYPES_CHAMBRES (4 par hôtel = 8)
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO hebergement.types_chambres (hotel_id, type_code, type_nom, description, nb_personnes_max, nb_lits_max, superficie, prix_base, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, t.code, t.nom, t.descr, t.npers, t.nlits, t.superf, t.prix, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','STD','Standard','Chambre standard 1 lit double',2,1,18,3500),
  ('NKC','SUP','Superieure','Chambre superieure 1 lit double + balcon',2,1,22,4500),
  ('NKC','SUITE','Suite Junior','Suite avec salon separe',3,2,35,7500),
  ('NKC','FAM','Familial','Chambre familiale 4 personnes',4,3,40,8500),
  ('DKR','STD','Standard','Chambre standard vue jardin',2,1,20,4000),
  ('DKR','SUP','Superieure','Chambre superieure vue mer',2,1,25,5500),
  ('DKR','SUITE','Suite Ocean','Suite vue ocean panoramique',3,2,40,9500),
  ('DKR','FAM','Familial','Bungalow familial 4 personnes',4,3,50,11000)
) AS t(hcode, code, nom, descr, npers, nlits, superf, prix)
JOIN h ON h.hotel_code = t.hcode
ON CONFLICT (hotel_id, type_code) DO NOTHING;

-- 8. CHAMBRES (15 par hôtel = 30, étages 1-3, statuts variés)
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     tc AS (SELECT type_id, hotel_id, type_code FROM hebergement.types_chambres)
INSERT INTO hebergement.chambres (hotel_id, type_id, numero_chambre, etage, statut, nb_lits, nb_personnes_max, equipements, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, tc.type_id, c.num, c.etage, c.statut, c.nlits, c.npers, c.equip, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','STD','101',1,'DISPONIBLE',1,2,'WiFi, TV, Climatisation'),
  ('NKC','STD','102',1,'DISPONIBLE',1,2,'WiFi, TV, Climatisation'),
  ('NKC','STD','103',1,'OCCUPEE',1,2,'WiFi, TV, Climatisation'),
  ('NKC','STD','104',1,'NETTOYAGE',1,2,'WiFi, TV, Climatisation'),
  ('NKC','STD','105',1,'DISPONIBLE',1,2,'WiFi, TV, Climatisation'),
  ('NKC','SUP','201',2,'DISPONIBLE',1,2,'WiFi, TV, Climatisation, Mini-bar, Balcon'),
  ('NKC','SUP','202',2,'OCCUPEE',1,2,'WiFi, TV, Climatisation, Mini-bar, Balcon'),
  ('NKC','SUP','203',2,'DISPONIBLE',1,2,'WiFi, TV, Climatisation, Mini-bar, Balcon'),
  ('NKC','SUP','204',2,'MAINTENANCE',1,2,'WiFi, TV, Climatisation, Mini-bar, Balcon'),
  ('NKC','SUP','205',2,'DISPONIBLE',1,2,'WiFi, TV, Climatisation, Mini-bar, Balcon'),
  ('NKC','SUITE','301',3,'DISPONIBLE',2,3,'WiFi, TV, Climatisation, Mini-bar, Salon, Jacuzzi'),
  ('NKC','SUITE','302',3,'OCCUPEE',2,3,'WiFi, TV, Climatisation, Mini-bar, Salon, Jacuzzi'),
  ('NKC','FAM','401',4,'DISPONIBLE',3,4,'WiFi, TV, Climatisation, Coin enfants'),
  ('NKC','FAM','402',4,'NETTOYAGE',3,4,'WiFi, TV, Climatisation, Coin enfants'),
  ('NKC','FAM','403',4,'DISPONIBLE',3,4,'WiFi, TV, Climatisation, Coin enfants'),
  ('DKR','STD','101',1,'DISPONIBLE',1,2,'WiFi, TV, Vue jardin'),
  ('DKR','STD','102',1,'OCCUPEE',1,2,'WiFi, TV, Vue jardin'),
  ('DKR','STD','103',1,'DISPONIBLE',1,2,'WiFi, TV, Vue jardin'),
  ('DKR','STD','104',1,'DISPONIBLE',1,2,'WiFi, TV, Vue jardin'),
  ('DKR','STD','105',1,'NETTOYAGE',1,2,'WiFi, TV, Vue jardin'),
  ('DKR','SUP','201',2,'DISPONIBLE',1,2,'WiFi, TV, Mini-bar, Vue mer'),
  ('DKR','SUP','202',2,'OCCUPEE',1,2,'WiFi, TV, Mini-bar, Vue mer'),
  ('DKR','SUP','203',2,'DISPONIBLE',1,2,'WiFi, TV, Mini-bar, Vue mer'),
  ('DKR','SUP','204',2,'DISPONIBLE',1,2,'WiFi, TV, Mini-bar, Vue mer'),
  ('DKR','SUP','205',2,'MAINTENANCE',1,2,'WiFi, TV, Mini-bar, Vue mer'),
  ('DKR','SUITE','301',3,'DISPONIBLE',2,3,'WiFi, TV, Mini-bar, Terrasse ocean'),
  ('DKR','SUITE','302',3,'OCCUPEE',2,3,'WiFi, TV, Mini-bar, Terrasse ocean'),
  ('DKR','FAM','BG01',1,'DISPONIBLE',3,4,'WiFi, TV, Cuisine, Terrasse'),
  ('DKR','FAM','BG02',1,'OCCUPEE',3,4,'WiFi, TV, Cuisine, Terrasse'),
  ('DKR','FAM','BG03',1,'DISPONIBLE',3,4,'WiFi, TV, Cuisine, Terrasse')
) AS c(hcode, tcode, num, etage, statut, nlits, npers, equip)
JOIN h ON h.hotel_code = c.hcode
JOIN tc ON tc.type_code = c.tcode AND tc.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, numero_chambre) DO NOTHING;

-- 9. TARIFS_CHAMBRES (1 par type/hôtel = 8)
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     tc AS (SELECT type_id, hotel_id, type_code FROM hebergement.types_chambres)
INSERT INTO hebergement.tarifs_chambres (hotel_id, type_id, nom_tarif, prix_nuit, prix_weekend, date_debut, date_fin, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, tc.type_id, t.nom, t.prix, t.prix*1.2, '2026-01-01'::date, '2026-12-31'::date, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','STD','Tarif standard 2026',3500),
  ('NKC','SUP','Tarif superieur 2026',4500),
  ('NKC','SUITE','Tarif suite 2026',7500),
  ('NKC','FAM','Tarif familial 2026',8500),
  ('DKR','STD','Tarif standard 2026',4000),
  ('DKR','SUP','Tarif superieur vue mer 2026',5500),
  ('DKR','SUITE','Tarif suite ocean 2026',9500),
  ('DKR','FAM','Tarif bungalow 2026',11000)
) AS t(hcode, tcode, nom, prix)
JOIN h ON h.hotel_code = t.hcode
JOIN tc ON tc.type_code = t.tcode AND tc.hotel_id = h.hotel_id;

-- 10. RESERVATIONS (10 par hôtel = 20)
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     cl AS (SELECT client_id, hotel_id, numero_client FROM client.clients),
     u AS (SELECT user_id, hotel_id, username FROM core.dbusers)
INSERT INTO hebergement.reservations (hotel_id, numero_reservation, client_principal_id, user_id, date_arrivee, date_depart, nb_nuits, nb_adultes, nb_enfants, montant_total, statut, motif_sejour, created_at, updated_at, created_by)
SELECT h.hotel_id, r.num, cl.client_id, u.user_id, r.arr::date, r.dep::date, (r.dep::date - r.arr::date)::int, r.nadults, r.nenf, r.montant, r.statut, r.motif, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','RES-NKC-2026-0001','CLI-NKC-2026-0001','reception1.nkc','2026-05-01','2026-05-04',1,0,10500,'PARTIE','TOURISME'),
  ('NKC','RES-NKC-2026-0002','CLI-NKC-2026-0003','reception1.nkc','2026-05-08','2026-05-11',1,0,13500,'PARTIE','AFFAIRES'),
  ('NKC','RES-NKC-2026-0003','CLI-NKC-2026-0005','reception1.nkc','2026-05-10','2026-05-13',2,1,22500,'ARRIVEE','TOURISME'),
  ('NKC','RES-NKC-2026-0004','CLI-NKC-2026-0007','reception1.nkc','2026-05-09','2026-05-12',1,0,13500,'ARRIVEE','AFFAIRES'),
  ('NKC','RES-NKC-2026-0005','CLI-NKC-2026-0009','reception1.nkc','2026-05-12','2026-05-15',2,0,22500,'CONFIRMEE','AFFAIRES'),
  ('NKC','RES-NKC-2026-0006','CLI-NKC-2026-0011','reception1.nkc','2026-05-15','2026-05-18',1,0,13500,'CONFIRMEE','TOURISME'),
  ('NKC','RES-NKC-2026-0007','CLI-NKC-2026-0013','reception1.nkc','2026-05-20','2026-05-22',2,0,15000,'CONFIRMEE','AFFAIRES'),
  ('NKC','RES-NKC-2026-0008','CLI-NKC-2026-0002','reception1.nkc','2026-04-25','2026-04-27',1,0,7000,'PARTIE','TOURISME'),
  ('NKC','RES-NKC-2026-0009','CLI-NKC-2026-0006','reception1.nkc','2026-05-25','2026-05-28',2,0,22500,'CONFIRMEE','TOURISME'),
  ('NKC','RES-NKC-2026-0010','CLI-NKC-2026-0014','reception1.nkc','2026-04-20','2026-04-22',1,0,9000,'ANNULEE','TOURISME'),
  ('DKR','RES-DKR-2026-0001','CLI-DKR-2026-0001','reception1.dkr','2026-05-02','2026-05-06',2,0,22000,'PARTIE','TOURISME'),
  ('DKR','RES-DKR-2026-0002','CLI-DKR-2026-0003','reception1.dkr','2026-05-09','2026-05-12',1,0,16500,'ARRIVEE','AFFAIRES'),
  ('DKR','RES-DKR-2026-0003','CLI-DKR-2026-0005','reception1.dkr','2026-05-10','2026-05-13',2,1,28500,'ARRIVEE','TOURISME'),
  ('DKR','RES-DKR-2026-0004','CLI-DKR-2026-0007','reception1.dkr','2026-05-12','2026-05-15',2,0,28500,'CONFIRMEE','AFFAIRES'),
  ('DKR','RES-DKR-2026-0005','CLI-DKR-2026-0009','reception1.dkr','2026-05-14','2026-05-17',1,0,16500,'CONFIRMEE','AFFAIRES'),
  ('DKR','RES-DKR-2026-0006','CLI-DKR-2026-0011','reception1.dkr','2026-05-18','2026-05-22',3,2,44000,'CONFIRMEE','TOURISME'),
  ('DKR','RES-DKR-2026-0007','CLI-DKR-2026-0013','reception1.dkr','2026-05-22','2026-05-25',2,0,28500,'CONFIRMEE','AFFAIRES'),
  ('DKR','RES-DKR-2026-0008','CLI-DKR-2026-0002','reception1.dkr','2026-04-28','2026-04-30',1,0,11000,'PARTIE','TOURISME'),
  ('DKR','RES-DKR-2026-0009','CLI-DKR-2026-0006','reception1.dkr','2026-05-26','2026-05-30',2,0,38000,'CONFIRMEE','TOURISME'),
  ('DKR','RES-DKR-2026-0010','CLI-DKR-2026-0014','reception1.dkr','2026-04-22','2026-04-24',1,0,8000,'NO_SHOW','TOURISME')
) AS r(hcode, num, ncli, uname, arr, dep, nadults, nenf, montant, statut, motif)
JOIN h ON h.hotel_code = r.hcode
JOIN cl ON cl.numero_client = r.ncli AND cl.hotel_id = h.hotel_id
JOIN u ON u.username = r.uname AND u.hotel_id = h.hotel_id;

-- 11. RESERVATIONS_CHAMBRES (1 chambre par réservation, mapping séquentiel)
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     r AS (SELECT reservation_id, hotel_id, numero_reservation, date_arrivee, date_depart FROM hebergement.reservations),
     ch AS (SELECT chambre_id, hotel_id, numero_chambre FROM hebergement.chambres)
INSERT INTO hebergement.reservations_chambres (hotel_id, reservation_id, chambre_id, date_debut, date_fin, prix_nuit, created_at, updated_at, created_by)
SELECT h.hotel_id, r.reservation_id, ch.chambre_id, r.date_arrivee, r.date_depart, m.prix, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','RES-NKC-2026-0001','101',3500),
  ('NKC','RES-NKC-2026-0002','201',4500),
  ('NKC','RES-NKC-2026-0003','103',3500),
  ('NKC','RES-NKC-2026-0004','202',4500),
  ('NKC','RES-NKC-2026-0005','301',7500),
  ('NKC','RES-NKC-2026-0006','203',4500),
  ('NKC','RES-NKC-2026-0007','105',3500),
  ('NKC','RES-NKC-2026-0008','102',3500),
  ('NKC','RES-NKC-2026-0009','302',7500),
  ('NKC','RES-NKC-2026-0010','401',8500),
  ('DKR','RES-DKR-2026-0001','101',4000),
  ('DKR','RES-DKR-2026-0002','201',5500),
  ('DKR','RES-DKR-2026-0003','102',4000),
  ('DKR','RES-DKR-2026-0004','301',9500),
  ('DKR','RES-DKR-2026-0005','202',5500),
  ('DKR','RES-DKR-2026-0006','BG01',11000),
  ('DKR','RES-DKR-2026-0007','203',5500),
  ('DKR','RES-DKR-2026-0008','103',4000),
  ('DKR','RES-DKR-2026-0009','302',9500),
  ('DKR','RES-DKR-2026-0010','BG02',11000)
) AS m(hcode, num, ch_num, prix)
JOIN h ON h.hotel_code = m.hcode
JOIN r ON r.numero_reservation = m.num
JOIN ch ON ch.numero_chambre = m.ch_num AND ch.hotel_id = h.hotel_id
ON CONFLICT (reservation_id, chambre_id, date_debut) DO NOTHING;

-- 12. NUITEES (générées pour les réservations PARTIE = facturable, statut CONSOMMEE)
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     rc AS (
       SELECT rc.hotel_id, rc.reservation_id, rc.chambre_id, rc.date_debut, rc.date_fin, rc.prix_nuit, r.statut, r.numero_reservation
       FROM hebergement.reservations_chambres rc
       JOIN hebergement.reservations r ON r.reservation_id = rc.reservation_id
       WHERE r.statut IN ('PARTIE','ARRIVEE')
     )
INSERT INTO hebergement.nuitees (hotel_id, reservation_id, chambre_id, date_nuit, prix_nuit, taxe_sejour, statut, created_at, updated_at, created_by)
SELECT rc.hotel_id, rc.reservation_id, rc.chambre_id, gs::date, rc.prix_nuit, 50,
       CASE WHEN rc.statut = 'PARTIE' THEN 'CONSOMMEE' ELSE 'PREVUE' END,
       NOW(), NOW(), 'system'
FROM rc, generate_series(rc.date_debut, rc.date_fin - 1, '1 day'::interval) gs
ON CONFLICT (reservation_id, chambre_id, date_nuit) DO NOTHING;

\echo '=== HEBERGEMENT seeded ==='
SELECT 'types_chambres' AS t, COUNT(*) FROM hebergement.types_chambres UNION ALL
SELECT 'chambres',             COUNT(*) FROM hebergement.chambres UNION ALL
SELECT 'tarifs_chambres',      COUNT(*) FROM hebergement.tarifs_chambres UNION ALL
SELECT 'reservations',         COUNT(*) FROM hebergement.reservations UNION ALL
SELECT 'reservations_chambres',COUNT(*) FROM hebergement.reservations_chambres UNION ALL
SELECT 'nuitees',              COUNT(*) FROM hebergement.nuitees;
