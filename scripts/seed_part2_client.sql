-- ============================================================================
-- SEED Part 2 — CLIENT (sociétés + clients)
-- ============================================================================

-- 5. CLIENT.SOCIETES (10 par hôtel = 20)
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels)
INSERT INTO client.societes (hotel_id, societe_nom, contact_principal, telephone, email, adresse, ville, pays, siret, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, s.nom, s.contact, s.tel, s.email, s.adr, s.ville, 'Mauritanie', s.siret, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','SNIM','Mohamed Lemine','+22245252100','contact@snim.mr','Boulevard Che Gueva','Nouakchott','MR-001-2010'),
  ('NKC','Mauritel','Sidi Ould Cheikh','+22245252101','contact@mauritel.mr','Avenue Kennedy','Nouakchott','MR-002-2008'),
  ('NKC','Banque Mauritanienne','Aicha Mint Mahmoud','+22245252102','direction@bmci.mr','Avenue Independance','Nouakchott','MR-003-2005'),
  ('NKC','Air Mauritanie','Brahim Ould Salem','+22245252103','vols@airmauritanie.mr','Aeroport NKC','Nouakchott','MR-004-2012'),
  ('NKC','MAEDEN Mining','Selma Mint Bowba','+22245252104','contact@maeden.mr','Zone industrielle','Nouakchott','MR-005-2015'),
  ('NKC','Kinross Tasiast','Yacoub Diop','+22245252105','contact@kinross.mr','Tasiast Mine','Nouakchott','MR-006-2018'),
  ('NKC','Total Mauritanie','Khadija Sy','+22245252106','direction@total.mr','Route Aeroport','Nouakchott','MR-007-2010'),
  ('NKC','Mosaique Diplomatie','Ahmed Ould Ahmed','+22245252107','reception@mosaique.mr','Tevragh Zeina','Nouakchott','MR-008-2003'),
  ('NKC','Universite Nouakchott','Cheikh Tijani','+22245252108','rectorat@univ-nkc.mr','Campus Universitaire','Nouakchott','MR-009-1981'),
  ('NKC','Hotel Diplomat NKC','Mariam Diallo','+22245252109','partenariat@diplomat.mr','Tevragh Zeina','Nouakchott','MR-010-2014'),
  ('DKR','Petromauritanie','Ousmane Wade','+22245290100','contact@petromauritanie.mr','Zone Petroliere','Dakhla','MR-011-2011'),
  ('DKR','Office Tourisme Dakhla','Halima Mint Ahmed','+22245290101','tourisme@dakhla.mr','Centre ville','Dakhla','MR-012-2009'),
  ('DKR','Pecheries Atlantiques','Boubacar Camara','+22245290102','direction@pecheriesatl.mr','Port Dakhla','Dakhla','MR-013-2007'),
  ('DKR','Cimenterie du Nord','Aminata Tall','+22245290103','direction@cimentnord.mr','Zone industrielle','Dakhla','MR-014-2013'),
  ('DKR','Mines El Aouina','Salem Ould Yarba','+22245290104','contact@minesaouina.mr','Zone Mining','Dakhla','MR-015-2016'),
  ('DKR','Banque Atlantique','Fatou Diallo','+22245290105','direction@atl.mr','Avenue Centrale','Dakhla','MR-016-2012'),
  ('DKR','Pecheries Sahel','Ahmed Sy','+22245290106','contact@pecheriesahel.mr','Port Dakhla','Dakhla','MR-017-2014'),
  ('DKR','Hotel Sahel Resort','Khadija Mokhtar','+22245290107','partenariat@sahelresort.mr','Plage Dakhla','Dakhla','MR-018-2010'),
  ('DKR','Construction Sahara','Mamadou Ba','+22245290108','direction@constsahara.mr','Zone industrielle','Dakhla','MR-019-2017'),
  ('DKR','Tour Operator Dakhla','Aboubacar Niang','+22245290109','contact@todakhla.mr','Avenue tourisme','Dakhla','MR-020-2019')
) AS s(hcode, nom, contact, tel, email, adr, ville, siret)
JOIN h ON h.hotel_code = s.hcode;

-- 6. CLIENT.CLIENTS (15 par hôtel = 30)
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     soc AS (SELECT societe_id, hotel_id, societe_nom FROM client.societes)
INSERT INTO client.clients (hotel_id, numero_client, prenom, nom, email, telephone, adresse, ville, pays, date_naissance, societe_id, actif, created_at, updated_at, created_by)
SELECT h.hotel_id, c.num, c.prenom, c.nom, c.email, c.tel, c.adr, c.ville, 'Mauritanie', c.dn::date, s.societe_id, true, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','CLI-NKC-2026-0001','Mohamed','Ould Salem','m.salem@gmail.com','+22246001001','Tevragh Zeina','Nouakchott','1985-03-15','SNIM'),
  ('NKC','CLI-NKC-2026-0002','Aicha','Mint Brahim','a.brahim@yahoo.fr','+22246001002','Sebkha','Nouakchott','1990-07-22',NULL),
  ('NKC','CLI-NKC-2026-0003','Sidi','Ould Mokhtar','s.mokhtar@hotmail.com','+22246001003','Ksar','Nouakchott','1978-11-03','Mauritel'),
  ('NKC','CLI-NKC-2026-0004','Fatima','Mint Ahmed','f.ahmed@gmail.com','+22246001004','Riyad','Nouakchott','1992-01-18',NULL),
  ('NKC','CLI-NKC-2026-0005','Ahmed','Diallo','a.diallo@gmail.com','+22246001005','El Mina','Nouakchott','1983-09-09','Air Mauritanie'),
  ('NKC','CLI-NKC-2026-0006','Khadija','Sy','k.sy@yahoo.fr','+22246001006','Toujounine','Nouakchott','1995-05-30',NULL),
  ('NKC','CLI-NKC-2026-0007','Boubacar','Wade','b.wade@hotmail.com','+22246001007','Arafat','Nouakchott','1988-12-12','MAEDEN Mining'),
  ('NKC','CLI-NKC-2026-0008','Mariam','Mint Cheikh','m.cheikh@gmail.com','+22246001008','Tevragh Zeina','Nouakchott','1996-08-25',NULL),
  ('NKC','CLI-NKC-2026-0009','Yacoub','Ba','y.ba@yahoo.fr','+22246001009','Sebkha','Nouakchott','1980-04-04','Banque Mauritanienne'),
  ('NKC','CLI-NKC-2026-0010','Salma','Mint Mokhtar','s.mokhtar2@gmail.com','+22246001010','Ksar','Nouakchott','1991-10-17',NULL),
  ('NKC','CLI-NKC-2026-0011','Ousmane','Niang','o.niang@hotmail.com','+22246001011','Riyad','Nouakchott','1987-02-28','Total Mauritanie'),
  ('NKC','CLI-NKC-2026-0012','Halima','Mint Sidi','h.sidi@gmail.com','+22246001012','El Mina','Nouakchott','1993-06-11',NULL),
  ('NKC','CLI-NKC-2026-0013','Brahim','Ould Cheikh','b.cheikh@yahoo.fr','+22246001013','Toujounine','Nouakchott','1979-09-19','Kinross Tasiast'),
  ('NKC','CLI-NKC-2026-0014','Aminata','Diallo','a.diallo2@gmail.com','+22246001014','Arafat','Nouakchott','1994-12-05',NULL),
  ('NKC','CLI-NKC-2026-0015','Moctar','Camara','m.camara@hotmail.com','+22246001015','Tevragh Zeina','Nouakchott','1986-07-07','Universite Nouakchott'),
  ('DKR','CLI-DKR-2026-0001','Cheikh','Ould Mohamed','c.mohamed@gmail.com','+22246002001','Centre','Dakhla','1982-05-14','Petromauritanie'),
  ('DKR','CLI-DKR-2026-0002','Khadija','Mint Mokhtar','k.mokhtar@yahoo.fr','+22246002002','Plage','Dakhla','1989-08-22',NULL),
  ('DKR','CLI-DKR-2026-0003','Mamadou','Ba','m.ba@hotmail.com','+22246002003','Centre','Dakhla','1976-11-01','Pecheries Atlantiques'),
  ('DKR','CLI-DKR-2026-0004','Aboubacar','Niang','a.niang@gmail.com','+22246002004','Industriel','Dakhla','1991-03-30',NULL),
  ('DKR','CLI-DKR-2026-0005','Fatou','Diallo','f.diallo@yahoo.fr','+22246002005','Plage','Dakhla','1984-09-18','Banque Atlantique'),
  ('DKR','CLI-DKR-2026-0006','Ahmed','Sy','a.sy@gmail.com','+22246002006','Centre','Dakhla','1990-01-25',NULL),
  ('DKR','CLI-DKR-2026-0007','Halima','Mint Ahmed','h.ahmed@hotmail.com','+22246002007','Industriel','Dakhla','1985-12-04','Cimenterie du Nord'),
  ('DKR','CLI-DKR-2026-0008','Boubacar','Camara','b.camara@gmail.com','+22246002008','Plage','Dakhla','1993-04-16',NULL),
  ('DKR','CLI-DKR-2026-0009','Aminata','Tall','a.tall@yahoo.fr','+22246002009','Centre','Dakhla','1988-06-21','Mines El Aouina'),
  ('DKR','CLI-DKR-2026-0010','Salem','Ould Yarba','s.yarba@hotmail.com','+22246002010','Industriel','Dakhla','1981-10-08',NULL),
  ('DKR','CLI-DKR-2026-0011','Mariam','Diallo','m.diallo3@gmail.com','+22246002011','Plage','Dakhla','1995-02-11','Construction Sahara'),
  ('DKR','CLI-DKR-2026-0012','Yacoub','Wade','y.wade@yahoo.fr','+22246002012','Centre','Dakhla','1986-08-29',NULL),
  ('DKR','CLI-DKR-2026-0013','Khadi','Diop','k.diop@hotmail.com','+22246002013','Industriel','Dakhla','1979-05-13','Tour Operator Dakhla'),
  ('DKR','CLI-DKR-2026-0014','Ousmane','Cheikh','o.cheikh@gmail.com','+22246002014','Plage','Dakhla','1992-11-27',NULL),
  ('DKR','CLI-DKR-2026-0015','Aicha','Mint Sidi','a.sidi@yahoo.fr','+22246002015','Centre','Dakhla','1990-07-19','Hotel Sahel Resort')
) AS c(hcode, num, prenom, nom, email, tel, adr, ville, dn, societe_nom)
JOIN h ON h.hotel_code = c.hcode
LEFT JOIN soc s ON s.societe_nom = c.societe_nom AND s.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, numero_client) DO NOTHING;

\echo '=== CLIENT seeded ==='
SELECT 'societes' AS t, COUNT(*) FROM client.societes UNION ALL
SELECT 'clients',       COUNT(*) FROM client.clients;
