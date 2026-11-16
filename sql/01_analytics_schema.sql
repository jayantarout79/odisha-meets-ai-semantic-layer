-- Governance layer for the semantic layer.
-- Cube never reads raw tables: it reads PII-free views through a read-only role.
-- Run once in the Supabase SQL editor (or as a migration).

create schema if not exists analytics;   -- do NOT add this schema to the Data API "exposed schemas"

create or replace view analytics.v_bookings as
select id, launchpad_application_id, status, source, duration_minutes,
       visitor_timezone, booked_at, created_at, cancelled_at, completed_at
from public.bookings;

create or replace view analytics.v_movement_signups as
select id, booking_id, persona, source, status, wants_call, joined_mission, created_at
from public.movement_signups;

create or replace view analytics.v_launchpad_applications as
select id, persona, status, weekly_hours, has_used_ai, ai_tools_used,
       ai_knowledge_rating, country, created_at, reviewed_at
from public.launchpad_applications;

create or replace view analytics.v_build_club_sessions as
select id, title, topic, status, session_date, created_at
from public.build_club_sessions;

create or replace view analytics.v_build_club_registrations as
select id, session_id, role, referral_source, live_build_interest,
       status, state, country, created_at
from public.build_club_registrations;

create or replace view analytics.v_resources as
select id, title, category, file_type, published, created_at
from public.resources;

-- Unique people without exposing who they are: hash the email.
create or replace view analytics.v_resource_downloads as
select id, resource_id, md5(lower(trim(email))) as downloader_key, created_at
from public.resource_downloads;

-- Least-privilege role: can read ONLY the analytics schema.
create role cube_reader nologin;
grant usage on schema analytics to cube_reader;
grant select on all tables in schema analytics to cube_reader;
alter default privileges in schema analytics grant select on tables to cube_reader;

-- Then, yourself (keeps the password out of version control):
--   alter role cube_reader with login password '<strong password>';
