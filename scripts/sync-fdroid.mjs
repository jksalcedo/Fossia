#!/usr/bin/env node
// @ts-check
/**
 * F-Droid + IzzyOnDroid Supabase sync script.
 */

import { createClient } from '@supabase/supabase-js'
import { JSONParser } from '@streamparser/json'

const FDROID_URL = 'https://f-droid.org/repo/index-v2.json'
const IZZY_URL = 'https://apt.izzysoft.de/fdroid/repo/index-v1.json'
const IZZY_ICON_BASE = 'https://apt.izzysoft.de/fdroid/repo/icons'
const BATCH_SIZE = 150

const SUPABASE_URL = process.env.SUPABASE_URL
const SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY
const REPO = process.env.SYNC_REPO ?? 'all' // 'all' | 'fdroid' | 'izzy'

if (!SUPABASE_URL || !SERVICE_ROLE_KEY) {
  console.error('Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY')
  process.exit(1)
}

const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

const now = new Date().toISOString()
let rows = []
let techRows = []
let totalProcessed = 0
let totalUpserted = 0
let totalTechUpserted = 0
let totalErrors = 0

async function flushBatches(force = false) {
  if (rows.length < BATCH_SIZE && !(force && rows.length > 0)) return

  const currentRows = rows
  const currentTechRows = techRows
  rows = []
  techRows = []

  const { data: parentData, error: parentError } = await supabase.rpc('upsert_solutions_guarded', {
    payload: currentRows,
  })

  if (parentError) {
    totalErrors += currentRows.length + currentTechRows.length
    console.error('Parent RPC failed:', parentError.message)
    return
  }

  const n = Array.isArray(parentData) && parentData[0]?.inserted_or_updated
    ? Number(parentData[0].inserted_or_updated)
    : 0
  totalUpserted += Number.isFinite(n) ? n : 0
  console.log(`  ↑ upserted ${n} rows (total: ${totalUpserted})`)

  if (currentTechRows.length > 0) {
    const { data: techData, error: techError } = await supabase.rpc('upsert_app_technical_info', {
      payload: currentTechRows,
    })
    if (techError) {
      totalErrors += currentTechRows.length
      console.error('Tech RPC failed:', techError.message)
    } else {
      const m = Array.isArray(techData) && techData[0]?.inserted_or_updated
        ? Number(techData[0].inserted_or_updated)
        : 0
      totalTechUpserted += Number.isFinite(m) ? m : 0
    }
  }
}

// =========================================================
// F-Droid streaming
// =========================================================
async function syncFdroid() {
  console.log('Fetching F-Droid index-v2.json (streaming)...')
  const res = await fetch(FDROID_URL, { headers: { Accept: 'application/json' } })
  if (!res.ok || !res.body) throw new Error(`F-Droid fetch failed: ${res.status}`)

  await new Promise((resolve, reject) => {
    const parser = new JSONParser({ paths: ['$.packages.*'], keepStack: false })

    parser.onValue = async ({ value, key }) => {
      const packageName = String(key ?? '')
      if (!packageName || !value) return

      try {
        const app = /** @type {Record<string, any>} */ (value)
        const versions = app.versions
        if (!versions || typeof versions !== 'object' || Object.keys(versions).length === 0) return

        const m = app.metadata ?? {}
        const name = sanitize(pickLocalized(m.name, packageName))
        const summary = pickLocalized(m.summary, '')
        const description = sanitize((pickLocalized(m.description, summary) || '').slice(0, 1000))
        const iconFile = pickLocalized(m.icon, '')
        const iconUrl = iconFile ? `https://f-droid.org/repo/icons-640/${iconFile}` : ''

        rows.push({
          package_name: packageName,
          name,
          description,
          icon_url: iconUrl,
          fdroid_id: packageName,
          repo_url: str(m.sourceCode),
          license: str(m.license),
          fdroid_synced: true,
          category: Array.isArray(m.categories) && m.categories[0] ? String(m.categories[0]) : null,
          source_repo: 'fdroid',
          source_priority: 100,
          last_synced_at: now,
        })

        const chosen = selectLatestVersion(versions)
        if (chosen) {
          const manifest = chosen.manifest ?? {}
          const usesSdk = manifest.usesSdk ?? {}
          const file = chosen.file ?? {}
          const permissions = [
            ...(manifest.usesPermission ?? []),
            ...(manifest.usesPermissionSdk23 ?? []),
          ].map((p) => (typeof p === 'string' ? p : p?.name)).filter(Boolean)

          techRows.push({
            package_name: packageName,
            version_name: manifest.versionName ?? null,
            version_code: manifest.versionCode ?? null,
            min_sdk_version: usesSdk.minSdkVersion ?? null,
            target_sdk_version: usesSdk.targetSdkVersion ?? null,
            apk_size_bytes: file.size ?? null,
            permissions,
            native_code: Array.isArray(manifest.nativecode) ? manifest.nativecode : [],
            added_at: chosen.added ? new Date(chosen.added).toISOString() : null,
            updated_at: now,
          })
        }

        totalProcessed++

        if (rows.length >= BATCH_SIZE) {
          // Pause the stream while we flush to avoid unbounded accumulation
          parser.pause?.()
          await flushBatches(false)
          parser.resume?.()
        }
      } catch (err) {
        totalErrors++
        console.error(`Error processing ${packageName}:`, err)
      }
    }

    parser.onEnd = () => resolve(undefined)
    parser.onError = (err) => reject(err)

    // Pipe the response body into the parser
    const reader = res.body.getReader()
    const pump = () => {
      reader.read().then(({ done, value }) => {
        if (done) {
          parser.end?.()
          return
        }
        parser.write(value)
        pump()
      }).catch(reject)
    }
    pump()
  })

  await flushBatches(true)
  console.log(`F-Droid done. Processed: ${totalProcessed}`)
}

// =========================================================
// IzzyOnDroid (small feed, plain JSON is fine)
// =========================================================
async function syncIzzy() {
  console.log('Fetching IzzyOnDroid...')
  const res = await fetch(IZZY_URL, { headers: { Accept: 'application/json' } })
  if (!res.ok) throw new Error(`Izzy fetch failed: ${res.status}`)

  const izzyJson = await res.json()
  const apps = Array.isArray(izzyJson?.apps) ? izzyJson.apps : []
  const beforeCount = totalProcessed

  for (const app of apps) {
    try {
      const packageName = str(app?.packageName).trim()
      if (!packageName) continue

      const name = str(app?.name) || packageName
      const summary = str(app?.summary)
      const description = (str(app?.description) || summary).slice(0, 1000)
      const icon = str(app?.icon)
      const iconUrl = icon ? `${IZZY_ICON_BASE}/${icon}` : ''
      const repoUrl = str(app?.sourceCode) || str(app?.webSite)
      const category = Array.isArray(app?.categories) && app.categories[0]
        ? String(app.categories[0])
        : null

      rows.push({
        package_name: packageName,
        name,
        description,
        icon_url: iconUrl,
        fdroid_id: packageName,
        repo_url: repoUrl,
        license: str(app?.license),
        fdroid_synced: true,
        category,
        source_repo: 'izzyondroid',
        source_priority: 50,
        last_synced_at: now,
      })
      totalProcessed++
    } catch {
      totalErrors++
    }

    await flushBatches(false)
  }

  await flushBatches(true)
  console.log(`Izzy done. Processed: ${totalProcessed - beforeCount}`)
}

// =========================================================
// Helpers
// =========================================================
function selectLatestVersion(versions) {
  let latestStable = null
  let latestAny = null

  for (const version of Object.values(versions)) {
    const code = Number(version?.manifest?.versionCode ?? 0)
    if (!latestAny || code > Number(latestAny?.manifest?.versionCode ?? 0)) latestAny = version
    const isStable = !Array.isArray(version?.releaseChannels) || version.releaseChannels.length === 0
    if (isStable && (!latestStable || code > Number(latestStable?.manifest?.versionCode ?? 0))) {
      latestStable = version
    }
  }

  return latestStable ?? latestAny
}

function pickLocalized(obj, fallback) {
  if (!obj || typeof obj !== 'object') return fallback
  return obj['en-US'] ?? obj['en'] ?? Object.values(obj).find((v) => typeof v === 'string') ?? fallback
}

function str(v) { return typeof v === 'string' ? v : '' }

function sanitize(s) {
  return s.replace(/\u0000/g, '').replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, '')
}

// =========================================================
// Entry
// =========================================================
const started = Date.now()
try {
  if (REPO === 'all' || REPO === 'fdroid') await syncFdroid()
  if (REPO === 'all' || REPO === 'izzy') await syncIzzy()

  const stats = {
    totalProcessed,
    totalUpserted,
    totalTechUpserted,
    totalErrors,
    seconds: ((Date.now() - started) / 1000).toFixed(2),
  }
  console.log('✅ Sync complete', stats)
} catch (err) {
  console.error('❌ Sync failed:', err)
  process.exit(1)
}
