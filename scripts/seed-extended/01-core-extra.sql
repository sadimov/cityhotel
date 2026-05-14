-- ============================================================================
-- SEED EXTENDED Part 01 — CORE (parametres globaux additionnels)
-- Tour 52 — completement du seed initial (Tour 42)
--
-- Note : table core.parametres NON tenant-scoped, pas de hotel_id.
-- ============================================================================

\echo '=== Part 01: CORE.PARAMETRES (additionnels) ==='

-- Ajout de 6 nouveaux parametres globaux (les 5 de Tour 42 restent).
INSERT INTO core.parametres (cle, valeur, description, modifiable, categorie, created_at, updated_at, created_by, updated_by) VALUES
('app.version',                       '1.0.0',                'Version applicative courante',                         false, 'app',          NOW(), NOW(), 'system', 'system'),
('app.locale.default',                'fr',                   'Langue par defaut (fr/ar/en)',                         true,  'app',          NOW(), NOW(), 'system', 'system'),
('finance.exercice.courant',          '2026',                 'Exercice comptable en cours',                          true,  'finance',      NOW(), NOW(), 'system', 'system'),
('hebergement.checkin.heure.standard','14:00',                'Heure check-in standard',                              true,  'hebergement',  NOW(), NOW(), 'system', 'system'),
('hebergement.checkout.heure.standard','12:00',               'Heure check-out standard',                             true,  'hebergement',  NOW(), NOW(), 'system', 'system'),
('inventory.alerte.email.enabled',    'true',                 'Alertes par email sur seuil de stock',                 true,  'inventory',    NOW(), NOW(), 'system', 'system')
ON CONFLICT (cle) DO NOTHING;

\echo 'core.parametres total =>'
SELECT COUNT(*) FROM core.parametres;
