-- ============================================================================
-- SEED EXTENDED Part 06 — MENAGE
--   * planning  : creneaux de travail (7 jours x 3 personnel par hotel)
--   * historique: audit log des taches existantes (creation + transitions)
-- ============================================================================

\echo '=== Part 06: MENAGE (planning + historique) ==='

-- ----------------------------------------------------------------------------
-- 6.1 PLANNING — 7 jours du 2026-05-09 au 2026-05-15, creneau 08:00-16:00, pour 3 personnels actifs par hotel
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     pers AS (
       SELECT personnel_id, hotel_id, numero_employe,
              ROW_NUMBER() OVER (PARTITION BY hotel_id ORDER BY personnel_id) AS rn
       FROM menage.personnel
       WHERE actif = true
     ),
     days AS (SELECT generate_series('2026-05-09'::date, '2026-05-15'::date, '1 day'::interval)::date AS d)
INSERT INTO menage.planning
  (hotel_id, personnel_id, date_travail, heure_debut, heure_fin, disponible, commentaires,
   created_at, updated_at, created_by)
SELECT
  pers.hotel_id,
  pers.personnel_id,
  days.d,
  -- alternance shift matin/apres-midi pour les 3 premiers personnels
  CASE pers.rn WHEN 1 THEN '08:00'::time WHEN 2 THEN '08:00'::time ELSE '13:00'::time END,
  CASE pers.rn WHEN 1 THEN '16:00'::time WHEN 2 THEN '12:00'::time ELSE '20:00'::time END,
  -- Jour 7 (dimanche) : personnel 3 indisponible (repos hebdo)
  CASE WHEN EXTRACT(DOW FROM days.d) = 0 AND pers.rn = 3 THEN false ELSE true END,
  CASE WHEN EXTRACT(DOW FROM days.d) = 0 AND pers.rn = 3 THEN 'Repos hebdomadaire' ELSE NULL END,
  NOW(), NOW(), 'system'
FROM pers
CROSS JOIN days
WHERE pers.rn <= 3
  AND NOT EXISTS (
    SELECT 1 FROM menage.planning pl
    WHERE pl.hotel_id = pers.hotel_id
      AND pl.personnel_id = pers.personnel_id
      AND pl.date_travail = days.d
  );

-- ----------------------------------------------------------------------------
-- 6.2 HISTORIQUE — pour les taches existantes : action CREATION (PLANIFIEE) + quelques transitions simulees
-- ----------------------------------------------------------------------------
-- 6.2.a Entree historique CREATION pour chaque tache
WITH t AS (
  SELECT t.tache_id, t.hotel_id, t.chambre_id, t.personnel_id, t.statut, t.created_at
  FROM menage.taches t
),
u_h AS (
  SELECT hotel_id, MIN(user_id) AS user_id
  FROM core.dbusers
  WHERE username IN ('menage1.nkc','menage1.dkr','gerant.nkc','gerant.dkr')
  GROUP BY hotel_id
)
INSERT INTO menage.historique
  (hotel_id, tache_id, chambre_id, personnel_id, action, ancien_statut, nouveau_statut,
   commentaire, user_id, timestamp_action, created_at, updated_at, created_by)
SELECT t.hotel_id, t.tache_id, t.chambre_id, t.personnel_id, 'CREATION', NULL, t.statut,
       'Creation tache (seed)', u_h.user_id, t.created_at, NOW(), NOW(), 'system'
FROM t
JOIN u_h ON u_h.hotel_id = t.hotel_id
WHERE NOT EXISTS (
  SELECT 1 FROM menage.historique hx
  WHERE hx.tache_id = t.tache_id AND hx.action = 'CREATION'
);

-- 6.2.b Quelques transitions PLANIFIEE -> EN_COURS pour les premieres taches (2 par hotel)
WITH t AS (
  SELECT t.tache_id, t.hotel_id, t.chambre_id, t.personnel_id, t.statut,
         ROW_NUMBER() OVER (PARTITION BY t.hotel_id ORDER BY t.tache_id) AS rn
  FROM menage.taches t
  WHERE t.statut = 'PLANIFIEE'
),
u_h AS (
  SELECT hotel_id, MIN(user_id) AS user_id
  FROM core.dbusers
  WHERE username IN ('menage1.nkc','menage1.dkr')
  GROUP BY hotel_id
)
INSERT INTO menage.historique
  (hotel_id, tache_id, chambre_id, personnel_id, action, ancien_statut, nouveau_statut,
   commentaire, user_id, timestamp_action, created_at, updated_at, created_by)
SELECT t.hotel_id, t.tache_id, t.chambre_id, t.personnel_id, 'TRANSITION_STATUT', 'PLANIFIEE', 'EN_COURS',
       'Debut intervention (simule)', u_h.user_id,
       NOW() - INTERVAL '1 hour', NOW(), NOW(), 'system'
FROM t
JOIN u_h ON u_h.hotel_id = t.hotel_id
WHERE t.rn <= 2
  AND NOT EXISTS (
    SELECT 1 FROM menage.historique hx
    WHERE hx.tache_id = t.tache_id AND hx.action = 'TRANSITION_STATUT' AND hx.nouveau_statut = 'EN_COURS'
  );

-- 6.2.c Transition EN_COURS -> TERMINEE pour la 1ere tache de chaque hotel (terminee)
WITH t AS (
  SELECT t.tache_id, t.hotel_id, t.chambre_id, t.personnel_id,
         ROW_NUMBER() OVER (PARTITION BY t.hotel_id ORDER BY t.tache_id) AS rn
  FROM menage.taches t
),
u_h AS (
  SELECT hotel_id, MIN(user_id) AS user_id
  FROM core.dbusers
  WHERE username IN ('menage1.nkc','menage1.dkr')
  GROUP BY hotel_id
)
INSERT INTO menage.historique
  (hotel_id, tache_id, chambre_id, personnel_id, action, ancien_statut, nouveau_statut,
   commentaire, user_id, timestamp_action, created_at, updated_at, created_by)
SELECT t.hotel_id, t.tache_id, t.chambre_id, t.personnel_id, 'TRANSITION_STATUT', 'EN_COURS', 'TERMINEE',
       'Fin intervention (simule)', u_h.user_id,
       NOW(), NOW(), NOW(), 'system'
FROM t
JOIN u_h ON u_h.hotel_id = t.hotel_id
WHERE t.rn = 1
  AND NOT EXISTS (
    SELECT 1 FROM menage.historique hx
    WHERE hx.tache_id = t.tache_id AND hx.action = 'TRANSITION_STATUT' AND hx.nouveau_statut = 'TERMINEE'
  );

-- 6.2.d Audit ASSIGNATION sur 3 taches par hotel (changement personnel)
WITH t AS (
  SELECT t.tache_id, t.hotel_id, t.chambre_id, t.personnel_id,
         ROW_NUMBER() OVER (PARTITION BY t.hotel_id ORDER BY t.tache_id DESC) AS rn
  FROM menage.taches t
),
u_h AS (
  SELECT hotel_id, MIN(user_id) AS user_id
  FROM core.dbusers
  WHERE username IN ('gerant.nkc','gerant.dkr')
  GROUP BY hotel_id
)
INSERT INTO menage.historique
  (hotel_id, tache_id, chambre_id, personnel_id, action, ancien_statut, nouveau_statut,
   commentaire, user_id, timestamp_action, created_at, updated_at, created_by)
SELECT t.hotel_id, t.tache_id, t.chambre_id, t.personnel_id, 'ASSIGNATION', NULL, NULL,
       'Reassignation personnel (audit)', u_h.user_id,
       NOW() - INTERVAL '2 hour', NOW(), NOW(), 'system'
FROM t
JOIN u_h ON u_h.hotel_id = t.hotel_id
WHERE t.rn <= 3
  AND NOT EXISTS (
    SELECT 1 FROM menage.historique hx
    WHERE hx.tache_id = t.tache_id AND hx.action = 'ASSIGNATION'
  );

\echo '=== MENAGE extras seeded ==='
SELECT 'personnel'             AS t, COUNT(*) FROM menage.personnel UNION ALL
SELECT 'taches',                    COUNT(*) FROM menage.taches UNION ALL
SELECT 'planning',                  COUNT(*) FROM menage.planning UNION ALL
SELECT 'historique',                COUNT(*) FROM menage.historique;
