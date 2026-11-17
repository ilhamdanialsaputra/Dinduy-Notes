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

/**
 * Event boundary between the renderer and the reader that owns page preparation.
 *
 * <p>All callbacks are delivered on the Android main thread. Callbacks for one generation are
 * ordered. A generation accepted by the surface receives exactly one terminal
 * {@link #onDeckReleased(long, DeckReleaseReason)} callback.
 */
public interface PageSurfaceListener {
    default void onCapabilitiesAvailable(RenderCapabilities capabilities) {}

    default void onDeckPrepared(long generationId) {}

    default void onDeckRejected(long generationId, DeckRejectionReason reason) {}

    default void onDeckReleased(long generationId, DeckReleaseReason reason) {}

    default void onRenderFailure(RenderFailure failure) {}

    default void onGestureRejected(
            long generationId,
            GestureRejectionReason reason) {}

    default void onGestureRejected(
            long gestureId,
            long generationId,
            GestureRejectionReason reason) {
        onGestureRejected(generationId, reason);
    }

    default void onGestureCancelled(
            long gestureId,
            long generationId) {}

    default void onSettlementStarted(
            long generationId,
            String sourceLogicalPageId,
            String targetLogicalPageId,
            PageChange pageChange) {}

    default void onSettlementStarted(
            long gestureId,
            long generationId,
            String sourceLogicalPageId,
            String targetLogicalPageId,
            PageChange pageChange) {
        onSettlementStarted(
                generationId,
                sourceLogicalPageId,
                targetLogicalPageId,
                pageChange);
    }

    default void onSettlementCompleted(
            long generationId,
            String currentLogicalPageId,
            int currentOrdinal,
            PageChange pageChange) {}

    default void onSettlementCompleted(
            long gestureId,
            long generationId,
            String currentLogicalPageId,
            int currentOrdinal,
            PageChange pageChange) {
        onSettlementCompleted(
                generationId,
                currentLogicalPageId,
                currentOrdinal,
                pageChange);
    }

    default void onSettlementCancelled(
            long generationId,
            String currentLogicalPageId) {}

    default void onSettlementCancelled(
            long gestureId,
            long generationId,
            String currentLogicalPageId) {
        onSettlementCancelled(generationId, currentLogicalPageId);
    }
}
