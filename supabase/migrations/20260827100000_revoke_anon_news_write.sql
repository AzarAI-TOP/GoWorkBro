-- Ingestion RPC hardening: `upsert_ustc_news` was executable by `anon`,
-- so anyone holding the (public) publishable key could overwrite any
-- published edition — unauthenticated content injection rendered to every
-- user. Ingestion moves to the service-role key supplied via env to the
-- upload script; the app only ever reads `ustc_news` via table select.
revoke execute on function public.upsert_ustc_news(text, text, text)
  from public, anon;
grant execute on function public.upsert_ustc_news(text, text, text)
  to service_role;
