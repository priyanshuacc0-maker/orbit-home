package com.orbit.home.apps

import android.app.ActivityManager
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.orbit.home.R
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Loads and caches real system app icons on demand, decoded at the pixel size
 * actually needed. Only the slots and rows currently on screen ever request an
 * icon, so starting the launcher never decodes the whole inventory.
 */
class AppIconLoader(private val context: Context) {

    /**
     * How many decoded icons to keep, scaled to what this device can afford.
     * Declared before [cache]: Kotlin initialises properties in order, so the
     * cache would otherwise be built with a size of zero.
     */
    private val maxEntries: Int = run {
        val mb = (context.applicationContext
            .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.memoryClass ?: 128
        when {
            mb >= 512 -> 640
            mb >= 256 -> 400
            else -> 220
        }
    }

    // Keyed by "id@bucket" so the same app requested at two sizes keeps two
    // entries. The size is bucketed: keying on the exact pixel size meant every
    // step of an icon-size slider created a brand-new key, and the drawer alone
    // (one entry per app) could evict the wheel and dock icons (P1-38).
    private val cache = LruCache<String, ImageBitmap>(maxEntries)

    // Stops an icon started before an invalidation from repopulating the cache with stale pixels.
    private val cacheRevision = AtomicInteger(0)

    /**
     * When on, an icon is asked for through [android.content.pm.LauncherApps],
     * which is what returns the platform's themed (monochrome) icon on Android 13+
     * where the app has one (P2). Flipping it clears the cache, so no stale icon is
     * served from the other mode.
     */
    /**
     * Bumped whenever the cache is invalidated wholesale. Composables key their
     * `remember`/`LaunchedEffect` on it, so flipping [themedIcons] re-requests the
     * icons already on screen instead of leaving the old ones until a restart
     * (remediation 2.5).
     */
    val generation = mutableIntStateOf(0)

    /**
     * Icons asked for but not yet loaded. Deduplicated by cache key and bounded, so a
     * fast spin asks once per icon and cannot grow this without limit.
     */
    private val pending = ArrayDeque<String>()
    private val pendingSet = HashSet<String>()
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var preloadWorker: Job? = null

    @Volatile
    var themedIcons: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                cacheRevision.incrementAndGet()
                cache.evictAll()
                generation.intValue++
            }
        }

    /**
     * Asks for an icon to be warmed in the cache, without waiting for it.
     *
     * Deliberately not suspending. The wheel's preload effect is keyed on the current
     * slot, so a suspending call there is cancelled mid-load every time the wheel
     * moves — which is what made fast rotation look like it was loading. Queueing
     * hands the work to a worker that outlives the caller.
     *
     * Deduplicated by cache key, so spinning back and forth over the same icons asks
     * once each rather than once per frame.
     */
    fun request(id: String, px: Int) {
        val size = bucket(px)
        if (size <= 0) return
        val key = "$id@$size"
        if (cache.get(key) != null) return
        synchronized(pending) {
            if (!pendingSet.add(key)) return
            pending.addLast(key)
            while (pending.size > MAX_PENDING) {
                pendingSet.remove(pending.removeFirst())
            }
        }
        startPreloadWorker()
    }

    private fun startPreloadWorker() {
        synchronized(pending) {
            if (preloadWorker?.isActive == true) return
            // Worker ownership and the empty-queue handoff share the pending lock. If a request
            // arrives as the current worker exits, it either gets picked up here or sees a null
            // worker and starts another one; it cannot become stranded in the queue.
            preloadWorker = preloadScope.launch {
                while (true) {
                    val key = synchronized(pending) {
                        val next = pending.removeFirstOrNull()
                        if (next == null) {
                            preloadWorker = null
                        }
                        next
                    } ?: break

                    try {
                        val at = key.lastIndexOf('@')
                        if (at > 0) {
                            // Failures are swallowed by load(); the queue continues either way.
                            val size = key.substring(at + 1).toIntOrNull()
                            if (size != null) load(key.substring(0, at), size)
                        }
                    } finally {
                        // Keep a key deduplicated while it is in flight, not just while queued.
                        synchronized(pending) { pendingSet.remove(key) }
                    }
                }
            }
        }
    }

    suspend fun load(id: String, px: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val size = bucket(px)
        if (size <= 0) return@withContext null
        val key = "$id@$size"
        val revision = cacheRevision.get()
        cache.get(key)?.let {
            if (cacheRevision.get() == revision) return@withContext it
        }

        val bitmap = try {
            val parts = id.split("/")
            if (parts.size != 2) {
                null
            } else {
                val cn = ComponentName(parts[0], parts[1])
                // Decoded at the bucketed size, so the bitmap matches its key. The drawable is
                // the icon's own artwork where that can be had, and the shaping into a circle is
                // ours - see CircleIcon for why that matters.
                val drawable = if (themedIcons) {
                    themedIcon(cn, size) ?: rawIcon(cn) ?: systemIcon(cn)
                } else {
                    rawIcon(cn) ?: systemIcon(cn)
                }
                CircleIcon.render(drawable, size)?.asImageBitmap()
            }
        } catch (c: CancellationException) {
            // Never swallow cancellation: the caller may be leaving the screen.
            throw c
        } catch (t: Exception) {
            // Uninstalled, no icon, or a bad component: fail soft.
            null
        }

        if (bitmap != null && cacheRevision.get() == revision) cache.put(key, bitmap)
        bitmap
    }

    /**
     * The icon's own artwork, before the phone shapes it.
     *
     * getActivityIcon() returns the icon with the phone's mask already applied, which is the whole
     * reason icons came out as squircles here. Reading the resource the app itself declares gives
     * the unmasked drawable - an AdaptiveIconDrawable, layers intact, for anything modern - and a
     * flat picture for anything older.
     */
    private fun rawIcon(cn: ComponentName): android.graphics.drawable.Drawable? = runCatching {
        val pm = context.packageManager
        val info = pm.getActivityInfo(cn, 0)
        val res = info.iconResource
        if (res != 0) {
            pm.getDrawable(cn.packageName, res, info.applicationInfo)
        } else {
            // No icon of its own: the app's own icon is the honest answer.
            pm.getApplicationIcon(info.applicationInfo)
        }
    }.getOrNull()

    /** The plain system icon for a component. Last resort, mask and all. */
    private fun systemIcon(cn: ComponentName): android.graphics.drawable.Drawable? =
        runCatching { context.packageManager.getActivityIcon(cn) }.getOrNull()

    /**
     * The themed icon, where the platform offers one. Best-effort: any failure
     * falls back to the plain icon, so turning this on can never lose an icon.
     */
    private fun themedIcon(cn: ComponentName, size: Int): android.graphics.drawable.Drawable? =
        runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
            launcherApps
                .getActivityList(cn.packageName, android.os.Process.myUserHandle())
                .firstOrNull { it.componentName == cn }
                ?.getIcon(size)
        }.getOrNull()

    /**
     * Synchronous cache lookup. Lets a composable show an already-decoded icon
     * on the very first frame instead of flashing a placeholder while it loads.
     */
    fun peek(id: String, px: Int): ImageBitmap? {
        val size = bucket(px)
        return if (size <= 0) null else cache.get("$id@$size")
    }

    /** Drops everything, e.g. after a full refresh or a launch failure. */
    fun clear() {
        synchronized(pending) {
            pending.clear()
            pendingSet.clear()
            preloadWorker?.cancel()
            preloadWorker = null
        }
        cacheRevision.incrementAndGet()
        cache.evictAll()
        generation.intValue++
    }

    /**
     * Drops only the given packages' icons, keeping every other decoded icon
     * warm. Cache keys look like `pkg/activity@px`, so match on the prefix.
     */
    fun evictPackages(packages: Set<String>) {
        if (packages.isEmpty()) return
        // Advance first: a load already in progress must not put its old bitmap back afterwards.
        cacheRevision.incrementAndGet()
        cache.removeWhere { key -> packages.any { key.startsWith("$it/") } }
        // rememberAppIcon observes this state and reloads icons already rendered on screen.
        generation.intValue++
    }

    companion object {
        /** Icon sizes are rounded to this many pixels before they key the cache. */
        const val BUCKET_PX = 16

        /**
         * Rounds a requested pixel size to a cache bucket. The nearest bucket is
         * visually indistinguishable, and it stops a moving slider from filling
         * the cache with near-duplicates. Static so it is testable without a
         * Context.
         */
        fun bucket(px: Int): Int =
            if (px <= 0) 0 else ((px + BUCKET_PX / 2) / BUCKET_PX) * BUCKET_PX

        /** How many icons may wait to be warmed. Beyond this the oldest is dropped. */
        private const val MAX_PENDING = 64
    }
}

/**
 * A tiny LruCache wrapper. Using a hand-rolled map keeps this class free of any
 * Android framework import so it stays trivially testable and thread-safe enough
 * for a launcher (all access is on the IO dispatcher).
 */
private class LruCache<K, V>(private val maxSize: Int) {
    // accessOrder = true, so the eldest entry is the least recently used.
    private val map = LinkedHashMap<K, V>(maxSize, 0.75f, true)

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
        if (map.size > maxSize) {
            val iterator = map.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun removeWhere(predicate: (K) -> Boolean) {
        map.keys.removeAll { predicate(it) }
    }

    @Synchronized
    fun evictAll() {
        map.clear()
    }
}
