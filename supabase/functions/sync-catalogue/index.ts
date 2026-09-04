import { createClient } from 'jsr:@supabase/supabase-js@2'

/**
 * Sync is handled by the GitHub Actions workflow (.github/workflows/sync-fdroid.yml).
 * This endpoint only reports the last-synced timestamp from the database.
 */
Deno.serve(async () => {
  const supabaseUrl = Deno.env.get('SUPABASE_URL')
  const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (!supabaseUrl || !serviceKey) {
    return json({ success: false, message: 'Missing environment variables' }, 500)
  }

  const supabase = createClient(supabaseUrl, serviceKey)

  const { data, error } = await supabase
    .from('solutions')
    .select('source_repo, last_synced_at')
    .in('source_repo', ['fdroid', 'izzyondroid'])
    .order('last_synced_at', { ascending: false })
    .limit(2)

  if (error) {
    return json({ success: false, message: error.message }, 500)
  }

  const byRepo = Object.fromEntries(
    (data ?? []).map((r) => [r.source_repo, r.last_synced_at])
  )

  return json({
    success: true,
    message: 'Sync is handled by scheduled GitHub Actions workflow.',
    last_synced: byRepo,
  })
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' },
  })
}
