-- Follow-up to 20260816135322_secure_news_ingestion_and_api_hardening:
-- that migration granted EXECUTE on upsert_ustc_news to anon before its
-- blanket revoke step, which removed the grant again. Re-apply the grant
-- after the revokes so the anon-key upload script can call the RPC.
grant execute on function public.upsert_ustc_news(text, text, text) to anon;
