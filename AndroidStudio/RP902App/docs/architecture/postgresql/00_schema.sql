-- TraceLink PostgreSQL contract implementation: schemas and extensions.
-- Run this before the table and function scripts.

create schema if not exists api;
create schema if not exists tracelink_internal;

-- gen_random_uuid() and digest() are used for result IDs and payload hashes.
-- Managed PostgreSQL environments may require this extension to be enabled by an administrator.
create extension if not exists pgcrypto;

comment on schema api is
    'Public Android-facing schema. Android must use only api.fn_* functions.';

comment on schema tracelink_internal is
    'Internal TraceLink storage. Android must not read or write these tables directly.';
