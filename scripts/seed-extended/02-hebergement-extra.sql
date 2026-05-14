-- ============================================================================
-- SEED EXTENDED Part 02 — HEBERGEMENT
--   * Types SALLE (Tour 49 categorie=SALLE)
--   * Chambres-salles physiques
--   * Tarifs saisonniers (priorite Tour 44)
-- ============================================================================

\echo '=== Part 02: HEBERGEMENT (salles + tarifs saisonniers) ==='

-- ----------------------------------------------------------------------------
-- 2.1 Types SALLE (2 par hotel)
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO hebergement.types_chambres
  (hotel_id, type_code, type_nom, description, nb_personnes_max, nb_lits_max, superficie, prix_base, categorie, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, t.code, t.nom, t.descr, t.npers, t.nlits, t.superf, t.prix, 'SALLE', true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','SALLE-CONF','Salle Conference',  'Salle de conference 30 places, video-projecteur, WiFi',         30, 0, 80,  35000),
  ('NKC','SALLE-REC', 'Salle Reception',  'Salle de reception 80 places, mariages, seminaires',           80, 0, 200, 75000),
  ('DKR','SALLE-CONF','Salle Conference',  'Salle de conference 25 places vue ocean',                       25, 0, 70,  40000),
  ('DKR','SALLE-REC', 'Salle Reception',  'Salle de reception 100 places terrasse ocean',                 100, 0, 220, 80000)
) AS t(hcode, code, nom, descr, npers, nlits, superf, prix)
JOIN h ON h.hotel_code = t.hcode
ON CONFLICT (hotel_id, type_code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2.2 Chambres-salles physiques (2 par hotel)
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     tc AS (SELECT type_id, hotel_id, type_code FROM hebergement.types_chambres)
INSERT INTO hebergement.chambres
  (hotel_id, type_id, numero_chambre, etage, statut, nb_lits, nb_personnes_max, equipements, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, tc.type_id, c.num, c.etage, c.statut, 0, c.npers, c.equip, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','SALLE-CONF', 'SC-01', 0, 'DISPONIBLE', 30, 'Video-projecteur, WiFi, Climatisation, Tableau blanc'),
  ('NKC','SALLE-REC','SR-01',0, 'DISPONIBLE', 80, 'Scene, Sono, Eclairage, Climatisation, Bar'),
  ('DKR','SALLE-CONF', 'SC-01', 0, 'DISPONIBLE', 25, 'Video-projecteur, WiFi, Vue ocean'),
  ('DKR','SALLE-REC','SR-01',0, 'DISPONIBLE',100, 'Scene, Sono, Terrasse ocean, Bar, Cuisine equipee')
) AS c(hcode, tcode, num, etage, statut, npers, equip)
JOIN h ON h.hotel_code = c.hcode
JOIN tc ON tc.type_code = c.tcode AND tc.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, numero_chambre) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2.3 Tarifs saisonniers — 3 saisons par type (basse, haute, weekend), priorite Tour 44
-- Tarifs ajoutes en complement de ceux deja seedes (1/type, prio=0).
-- Basse saison    : jan-mars, octobre-decembre, priorite 5  (-15%)
-- Haute saison    : avril-septembre,             priorite 10 (+25%)
-- Promo evenement : 1-15 dec,                    priorite 100 (-30%)
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     tc AS (SELECT type_id, hotel_id, type_code, prix_base FROM hebergement.types_chambres)
INSERT INTO hebergement.tarifs_chambres
  (hotel_id, type_id, nom_tarif, date_debut, date_fin, prix_nuit, prix_weekend, priorite, actif, created_at, updated_at, created_by)
SELECT
  tc.hotel_id, tc.type_id, s.nom_tarif, s.dt_debut::date, s.dt_fin::date,
  ROUND(tc.prix_base * s.coef, 2),
  ROUND(tc.prix_base * s.coef * 1.2, 2),
  s.prio, true, NOW(), NOW(), 'system'
FROM tc
CROSS JOIN (VALUES
  ('Basse saison Q1 2026',       '2026-01-01', '2026-03-31', 0.85, 5),
  ('Haute saison ete 2026',      '2026-04-01', '2026-09-30', 1.25, 10),
  ('Promo decembre 2026',        '2026-12-01', '2026-12-15', 0.70, 100)
) AS s(nom_tarif, dt_debut, dt_fin, coef, prio)
-- evite doublon si rejoue
WHERE NOT EXISTS (
  SELECT 1 FROM hebergement.tarifs_chambres tcx
  WHERE tcx.hotel_id = tc.hotel_id AND tcx.type_id = tc.type_id AND tcx.nom_tarif = s.nom_tarif
);

\echo '=== HEBERGEMENT extras seeded ==='
SELECT 'types_chambres'             AS t, COUNT(*) FROM hebergement.types_chambres UNION ALL
SELECT 'types_chambres_SALLE',         COUNT(*) FROM hebergement.types_chambres WHERE categorie = 'SALLE' UNION ALL
SELECT 'chambres',                     COUNT(*) FROM hebergement.chambres UNION ALL
SELECT 'tarifs_chambres',              COUNT(*) FROM hebergement.tarifs_chambres;
