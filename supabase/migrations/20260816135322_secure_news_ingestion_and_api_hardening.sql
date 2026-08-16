-- ============================================================
-- Secure news ingestion + harden the public API surface
-- ============================================================

-- 1. Drop the anonymous write policies. A daily edition is immutable and is
--    now written only through the validated RPC below.
drop policy if exists "Anon can insert USTC news" on public.ustc_news;
drop policy if exists "Anon can update USTC news" on public.ustc_news;

-- 2. Public read policy targets the two API roles explicitly instead of the
--    PUBLIC role.
drop policy if exists "Anyone can read USTC news" on public.ustc_news;
create policy "Anyone can read USTC news" on public.ustc_news
  for select to anon, authenticated
  using (true);

-- 3. Validated ingestion RPC. SECURITY DEFINER so the upload script needs no
--    secret key, but writes are constrained to this exact operation: inputs
--    are validated (ISO format, not in the future, size caps) and the daily
--    row is upserted idempotently.
create or replace function public.upsert_ustc_news(
  p_date text,
  p_title text,
  p_content text
)
returns public.ustc_news
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_date date;
  v_row public.ustc_news;
begin
  if p_date !~ '^\d{4}-\d{2}-\d{2}$' then
    raise exception 'date must be a valid ISO date (YYYY-MM-DD)';
  end if;
  v_date := to_date(p_date, 'YYYY-MM-DD');
  if v_date > current_date then
    raise exception 'news date cannot be in the future';
  end if;
  if p_title is null or btrim(p_title) = '' then
    raise exception 'title is required';
  end if;
  if p_content is null or btrim(p_content) = '' then
    raise exception 'content is required';
  end if;
  if length(p_title) > 300 then
    raise exception 'title exceeds 300 characters';
  end if;
  if length(p_content) > 500000 then
    raise exception 'content exceeds 500000 characters';
  end if;

  insert into public.ustc_news (date, title, content)
  values (p_date, btrim(p_title), p_content)
  on conflict (date) do update
    set title = excluded.title,
        content = excluded.content
  returning * into v_row;

  return v_row;
end;
$$;

-- 4. Function visibility: revoke the default PUBLIC grant, then allow only
--    the anon role (the upload script's API key role).
revoke execute on function public.upsert_ustc_news(text, text, text) from public;
grant execute on function public.upsert_ustc_news(text, text, text) to anon;

-- 5. API surface hardening (Supabase guide): objects created in the future no
--    longer leak grants automatically.
alter default privileges in schema public revoke all on tables from anon, authenticated;
alter default privileges in schema public revoke all on sequences from anon, authenticated;
alter default privileges in schema public revoke all on functions from anon, authenticated;

-- 6. Revoke blanket grants on existing objects, then re-grant the explicit
--    matrix below. Row-level access remains enforced by RLS policies.
revoke all on all tables in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
revoke all on all functions in schema public from anon, authenticated;

-- Read: public news
grant select on table public.ustc_news to anon, authenticated;

-- Read/write: the user's own synced tables (rows filtered by RLS)
grant select, insert, update, delete on table public.todos to authenticated;
grant select, insert, update, delete on table public.habits to authenticated;
grant select, insert, update, delete on table public.focus_sessions to authenticated;
grant select, insert, update, delete on table public.countdowns to authenticated;
grant select, insert, update, delete on table public.sleep_records to authenticated;
grant select, insert, update, delete on table public.user_settings to authenticated;

-- The LWW trigger function executes as the user performing the DML, so
-- authenticated users need EXECUTE on it.
grant execute on function public.merge_user_setting_lww() to authenticated;
