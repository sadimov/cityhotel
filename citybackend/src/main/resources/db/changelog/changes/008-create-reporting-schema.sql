--liquibase formatted sql

--changeset cityteam:008-1-create-reporting-schema
--comment Cree le schema `reporting` (stub pre-Vague-3). Les tables seront ajoutees lors du tour /integrate-module reporting.
CREATE SCHEMA IF NOT EXISTS reporting;
--rollback DROP SCHEMA IF EXISTS reporting CASCADE;
