/*
 * PlayLikeCurl page-curl engine (OpenGL ES 2.0), embedded verbatim as source.
 *
 * Original project : https://github.com/karankalsi/PlayLikeCurl
 * Maintained fork  : Arnab11/PlayLikeCurl (GLES2 renderer, production bitmap API v1, release 1.1.4)
 * Local changes    : package declaration (karacken.curl -> app.dinduy.silkcurl). Logic edits are marked "Local change"
 *                    in PageRenderer.updateMvp (fov 45f -> 55f) and PageSurfaceView.completeSettlement only.
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

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure active-plus-pending texture sizing used before any GLES allocation. */
final class TextureBudget {
    private static final long BYTES_PER_PIXEL = 4L;

    static final class Result {
        private final RenderFailureReason failureReason;
        private final long requiredBytes;
        private final long gpuBudgetBytes;
        private final int requestedWidthPx;
        private final int requestedHeightPx;
        private final int maxTextureSize;

        private Result(
                RenderFailureReason failureReason,
                long requiredBytes,
                long gpuBudgetBytes,
                int requestedWidthPx,
                int requestedHeightPx,
                int maxTextureSize) {
            this.failureReason = failureReason;
            this.requiredBytes = requiredBytes;
            this.gpuBudgetBytes = gpuBudgetBytes;
            this.requestedWidthPx = requestedWidthPx;
            this.requestedHeightPx = requestedHeightPx;
            this.maxTextureSize = maxTextureSize;
        }

        RenderFailureReason getFailureReason() {
            return failureReason;
        }

        long getRequiredBytes() {
            return requiredBytes;
        }

        long getGpuBudgetBytes() {
            return gpuBudgetBytes;
        }

        int getRequestedWidthPx() {
            return requestedWidthPx;
        }

        int getRequestedHeightPx() {
            return requestedHeightPx;
        }

        int getMaxTextureSize() {
            return maxTextureSize;
        }
    }

    private TextureBudget() {}

    static Result evaluate(
            PageDeck<?> activeDeck,
            PageDeck<?> pendingDeck,
            int maxTextureSize,
            long gpuBudgetBytes) {
        if (maxTextureSize <= 0) {
            throw new IllegalArgumentException("maxTextureSize must be positive");
        }
        if (gpuBudgetBytes <= 0) {
            throw new IllegalArgumentException("gpuBudgetBytes must be positive");
        }

        Map<String, PageImage<?>> uniquePages = new LinkedHashMap<>();
        collect(activeDeck, uniquePages);
        collect(pendingDeck, uniquePages);

        long requiredBytes = 0L;
        for (PageImage<?> page : uniquePages.values()) {
            int widthPx = page.getWidthPx();
            int heightPx = page.getHeightPx();
            if (widthPx > maxTextureSize || heightPx > maxTextureSize) {
                return new Result(
                        RenderFailureReason.TEXTURE_TOO_LARGE,
                        requiredBytes,
                        gpuBudgetBytes,
                        widthPx,
                        heightPx,
                        maxTextureSize);
            }
            requiredBytes = Math.addExact(
                    requiredBytes,
                    Math.multiplyExact(
                            Math.multiplyExact((long) widthPx, (long) heightPx),
                            BYTES_PER_PIXEL));
        }

        RenderFailureReason failureReason =
                requiredBytes > gpuBudgetBytes
                        ? RenderFailureReason.GPU_BUDGET_EXCEEDED
                        : null;
        return new Result(
                failureReason,
                requiredBytes,
                gpuBudgetBytes,
                0,
                0,
                maxTextureSize);
    }

    private static void collect(
            PageDeck<?> deck,
            Map<String, PageImage<?>> uniquePages) {
        if (deck == null) {
            return;
        }
        for (PageImage<?> page : deck.getPages()) {
            uniquePages.putIfAbsent(page.identityKey(), page);
            if (page.hasOverlay()) {
                uniquePages.putIfAbsent(page.overlayIdentityKey(), page);
            }
        }
    }
}
