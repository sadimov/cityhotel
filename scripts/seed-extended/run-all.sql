-- ============================================================================
-- SEED EXTENDED — run-all
-- Execute les 7 scripts dans l'ordre (avec validation finale).
--
-- Usage :
--   psql -U postgres -d cityprojectdb -f scripts/seed-extended/run-all.sql
--
-- Note : \ir = include relative au fichier courant (pas au CWD du shell).
-- Compatible psql >= 9.0.
-- ============================================================================

\echo '################################################################'
\echo '#       SEED EXTENDED (Tour 52) — Demarrage execution           #'
\echo '################################################################'

\ir 01-core-extra.sql
\ir 02-hebergement-extra.sql
\ir 03-inventory-extra.sql
\ir 04-restaurant-extra.sql
\ir 05-finance-extra.sql
\ir 06-menage-extra.sql
\ir 08-resync-numerotation.sql
\ir 07-validation.sql

\echo '################################################################'
\echo '#       SEED EXTENDED — Termine                                 #'
\echo '################################################################'
