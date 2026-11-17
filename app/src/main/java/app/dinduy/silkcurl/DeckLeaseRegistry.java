/*
 * PlayLikeCurl page-curl engine (OpenGL ES 2.0), embedded verbatim as source.
 *
 * Original project : https://github.com/karankalsi/PlayLikeCurl
 * Maintained fork  : Arnab11/PlayLikeCurl (GLES2 renderer, production bitmap API v1, release 1.1.4)
 * Local changes    : ONLY the package declaration (karacken.curl -> app.dinduy.silkcurl). No logic was changed.
 *
 * MIT License
 *
 * Copyright (c) [year] [fullname]
 *   (copyright line exactly as shipped in the upstream LICENSE.txt, which still contains placeholders)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package app.dinduy.silkcurl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps each accepted bitmap lease bound to the listener that acquired it.
 *
 * <p>The registry also makes terminal release idempotent when renderer and lifecycle cleanup
 * converge on the same generation.
 */
final class DeckLeaseRegistry {
    static final class Lease {
        private final long generationId;
        private final PageSurfaceListener listener;
        private final DeckReleaseReason releaseReason;

        private Lease(
                long generationId,
                PageSurfaceListener listener,
                DeckReleaseReason releaseReason) {
            this.generationId = generationId;
            this.listener = listener;
            this.releaseReason = releaseReason;
        }

        long getGenerationId() {
            return generationId;
        }

        PageSurfaceListener getListener() {
            return listener;
        }

        DeckReleaseReason getReleaseReason() {
            return releaseReason;
        }
    }

    private final Map<Long, PageSurfaceListener> owners = new LinkedHashMap<>();
    private final Map<Long, DeckReleaseReason> requestedReleaseReasons =
            new LinkedHashMap<>();

    synchronized boolean acquire(long generationId, PageSurfaceListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (owners.containsKey(generationId)) {
            return false;
        }
        owners.put(generationId, listener);
        return true;
    }

    synchronized PageSurfaceListener listenerFor(
            long generationId,
            PageSurfaceListener fallback) {
        PageSurfaceListener owner = owners.get(generationId);
        return owner == null ? fallback : owner;
    }

    synchronized PageSurfaceListener ownerFor(long generationId) {
        return owners.get(generationId);
    }

    synchronized void markReleaseRequested(
            long generationId,
            DeckReleaseReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (owners.containsKey(generationId)) {
            requestedReleaseReasons.putIfAbsent(generationId, reason);
        }
    }

    synchronized Lease release(long generationId) {
        PageSurfaceListener listener = owners.remove(generationId);
        DeckReleaseReason reason = requestedReleaseReasons.remove(generationId);
        return listener == null ? null : new Lease(generationId, listener, reason);
    }

    synchronized List<Lease> releaseAll(DeckReleaseReason fallbackReason) {
        Objects.requireNonNull(fallbackReason, "fallbackReason");
        List<Lease> leases = new ArrayList<>(owners.size());
        for (Map.Entry<Long, PageSurfaceListener> entry : owners.entrySet()) {
            DeckReleaseReason reason =
                    requestedReleaseReasons.getOrDefault(entry.getKey(), fallbackReason);
            leases.add(new Lease(entry.getKey(), entry.getValue(), reason));
        }
        owners.clear();
        requestedReleaseReasons.clear();
        return List.copyOf(leases);
    }

    synchronized boolean hasOutstandingLeases() {
        return !owners.isEmpty();
    }
}
