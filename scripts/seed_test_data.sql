-- ============================================================================
-- SEED TEST DATA — city hotel v1.0.0
-- Inserts 10-30 lignes par table, respect ordre dépendances FK.
-- 2 hôtels : NKC (City Hotel Nouakchott) + DKR (Sahara Palace Dakhla)
-- Idempotent grâce à ON CONFLICT DO NOTHING (relançable).
-- ============================================================================

-- =====================================================
-- 1. CORE.ROLES (9 rôles système)
-- =====================================================
INSERT INTO core.roles (role_code, role_nom, description, permissions, actif) VALUES
('SUPERADMIN',  'Super Administrateur',     'Accès complet au système, multi-hôtels',           '{}'::json, true),
('ADMIN',       'Administrateur Hôtel',     'Administration complète d''un hôtel',               '{}'::json, true),
('GERANT',      'Gérant',                   'Gestion opérationnelle, validation factures',       '{}'::json, true),
('RECEPTION',   'Réceptionniste',           'Réservations, check-in, check-out, paiements',      '{}'::json, true),
('RESTAURANT',  'Personnel Restaurant',     'POS, commandes, encaissement restaurant',           '{}'::json, true),
('RESREC',      'Réception+Restaurant',     'Polyvalence réception et restaurant',               '{}'::json, true),
('MAGASIN',     'Magasinier',               'Gestion stocks, BC, BS, fournisseurs',              '{}'::json, true),
('MENAGE',      'Personnel Ménage',         'Tâches ménage, planning, état chambres',            '{}'::json, true),
('NIGHTAUDIT',  'Night Auditor',            'Audit de nuit, génération nuitées, no-show',        '{}'::json, true)
ON CONFLICT (role_code) DO NOTHING;

-- =====================================================
-- 2. CORE.HOTELS (2 hôtels)
-- =====================================================
INSERT INTO core.hotels (hotel_code, hotel_nom, hotel_adresse, ville, pays, code_pays, hotel_tel, email, devise, fuseau_horaire, actif, date_creation, date_modification) VALUES
('NKC', 'City Hotel Nouakchott',     'Avenue Gamal Abdel Nasser',    'Nouakchott', 'Mauritanie', 'MR', '+22245251010', 'contact@cityhotel-nkc.mr',     'MRU', 'Africa/Nouakchott', true, NOW(), NOW()),
('DKR', 'Sahara Palace Dakhla',      'Boulevard Wlad Hossein',       'Dakhla',     'Mauritanie', 'MR', '+22245290020', 'contact@saharapalace-dkr.mr',  'MRU', 'Africa/Nouakchott', true, NOW(), NOW())
ON CONFLICT (hotel_code) DO NOTHING;

-- =====================================================
-- 3. CORE.DBUSERS (15 utilisateurs répartis 2 hôtels)
-- Hash BCrypt = mot de passe "Test1234!" pour tous les comptes de test.
-- =====================================================
WITH hotels AS (SELECT hotel_id, hotel_code FROM core.hotels),
     roles  AS (SELECT role_id, role_code FROM core.roles)
INSERT INTO core.dbusers (username, email, password_hash, prenom, nom, telephone, poste, hotel_id, role_id, actif, compte_verrouille, mot_passe_temporaire, tentatives_connexion, date_creation, date_modification)
SELECT u.username, u.email, '$2a$10$lUrqzVCS5YpVvWpVoBoKDe.mYMUrEcS/Pv1sJ.NV8HiYSvEYhXsku',
       u.prenom, u.nom, u.tel, u.poste, h.hotel_id, r.role_id, true, false, false, 0, NOW(), NOW()
FROM (VALUES
  -- NKC team
  ('superadmin',     'superadmin@cityhotel.mr',     'Super',   'Admin',        '+22245251000', 'Direction',      'NKC', 'SUPERADMIN'),
  ('admin.nkc',      'admin.nkc@cityhotel.mr',      'Mohamed', 'Ould Ahmed',   '+22245251001', 'Directeur',      'NKC', 'ADMIN'),
  ('gerant.nkc',     'gerant.nkc@cityhotel.mr',     'Fatima',  'Mint Sidi',    '+22245251002', 'Gérante',        'NKC', 'GERANT'),
  ('reception1.nkc', 'reception1.nkc@cityhotel.mr', 'Aicha',   'Mint Brahim',  '+22245251003', 'Réception jour', 'NKC', 'RECEPTION'),
  ('reception2.nkc', 'reception2.nkc@cityhotel.mr', 'Salma',   'Mint Mokhtar', '+22245251004', 'Réception nuit', 'NKC', 'NIGHTAUDIT'),
  ('restau.nkc',     'restau.nkc@cityhotel.mr',     'Yacoub',  'Diallo',       '+22245251005', 'Chef caisse',    'NKC', 'RESTAURANT'),
  ('magasin.nkc',    'magasin.nkc@cityhotel.mr',    'Habib',   'Ba',           '+22245251006', 'Magasinier',     'NKC', 'MAGASIN'),
  ('menage1.nkc',    'menage1.nkc@cityhotel.mr',    'Mariem',  'Mint Cheikh',  '+22245251007', 'Femme de chambre','NKC', 'MENAGE'),
  -- DKR team
  ('admin.dkr',      'admin.dkr@cityhotel.mr',      'Ahmed',   'Ould Sidi',    '+22245290001', 'Directeur',      'DKR', 'ADMIN'),
  ('gerant.dkr',     'gerant.dkr@cityhotel.mr',     'Khadija', 'Mint Mokhtar', '+22245290002', 'Gérante',        'DKR', 'GERANT'),
  ('reception1.dkr', 'reception1.dkr@cityhotel.mr', 'Oumou',   'Sy',           '+22245290003', 'Réception',      'DKR', 'RESREC'),
  ('reception2.dkr', 'reception2.dkr@cityhotel.mr', 'Khadi',   'Diop',         '+22245290004', 'Audit nuit',     'DKR', 'NIGHTAUDIT'),
  ('restau.dkr',     'restau.dkr@cityhotel.mr',     'Boubacar','Wade',         '+22245290005', 'Chef caisse',    'DKR', 'RESTAURANT'),
  ('magasin.dkr',    'magasin.dkr@cityhotel.mr',    'Cheikh',  'Ould Mohamed', '+22245290006', 'Magasinier',     'DKR', 'MAGASIN'),
  ('menage1.dkr',    'menage1.dkr@cityhotel.mr',    'Aminata', 'Tall',         '+22245290007', 'Femme de chambre','DKR', 'MENAGE')
) AS u(username, email, prenom, nom, tel, poste, hotel_code, role_code)
JOIN hotels h ON h.hotel_code = u.hotel_code
JOIN roles  r ON r.role_code  = u.role_code
ON CONFLICT (username) DO NOTHING;

-- =====================================================
-- 4. CORE.PARAMETRES (5 paramètres globaux)
-- =====================================================
INSERT INTO core.parametres (cle, valeur, description, modifiable, categorie, created_at, updated_at, created_by, updated_by) VALUES
('app.timezone',              'Africa/Nouakchott',     'Fuseau horaire applicatif',                false, 'app',           NOW(), NOW(), 'system', 'system'),
('app.devise',                'MRU',                   'Devise par défaut (Ouguiya)',              false, 'app',           NOW(), NOW(), 'system', 'system'),
('notification.email.from',   'noreply@city-hotel.mr', 'Email expéditeur par défaut',              true,  'notification',  NOW(), NOW(), 'system', 'system'),
('audit.retention.days',      '365',                   'Durée rétention audit logs (jours)',       true,  'audit',         NOW(), NOW(), 'system', 'system'),
('app.maintenance.message',   '',                      'Message de maintenance applicatif (vide)', true,  'app',           NOW(), NOW(), 'system', 'system')
ON CONFLICT (cle) DO NOTHING;

\echo '=== CORE seeded ==='
SELECT 'roles' AS t, COUNT(*) FROM core.roles UNION ALL
SELECT 'hotels',     COUNT(*) FROM core.hotels UNION ALL
SELECT 'dbusers',    COUNT(*) FROM core.dbusers UNION ALL
SELECT 'parametres', COUNT(*) FROM core.parametres;
