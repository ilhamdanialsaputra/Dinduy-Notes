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

import java.util.Objects;

/**
 * Immutable client page identity paired with already prepared image content.
 *
 * <p>For the Android production API, {@code content} is an immutable, opaque
 * {@link android.graphics.Bitmap.Config#ARGB_8888 ARGB_8888} bitmap. Opaque base pages do not
 * need to report premultiplied alpha because every stored alpha value is fully opaque. The client
 * retains ownership but must not modify or recycle the bitmap after submitting its deck. The
 * client may release or recycle it only after
 * {@link PageSurfaceListener#onDeckReleased(long, DeckReleaseReason)} reports that generation.
 */
public final class PageImage<T> {
    private final long generationId;
    private final String logicalPageId;
    private final int ordinal;
    private final int widthPx;
    private final int heightPx;
    private final T content;
    private final T overlayContent;

    public PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            T content) {
        this(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                content,
                null);
    }

    /**
     * Creates a page with an optional ephemeral overlay sampled through the same deformation.
     *
     * <p>The overlay shares this page's identity, generation, dimensions, and bitmap lease. For
     * Android rendering it must be an ARGB_8888 premultiplied bitmap that reports alpha.
     */
    public PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            T content,
            T overlayContent) {
        if (logicalPageId == null || logicalPageId.isBlank()) {
            throw new IllegalArgumentException("logicalPageId must not be blank");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("page dimensions must be positive");
        }
        this.generationId = generationId;
        this.logicalPageId = logicalPageId;
        this.ordinal = ordinal;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.content = Objects.requireNonNull(content, "content");
        this.overlayContent = overlayContent;
    }

    public long getGenerationId() {
        return generationId;
    }

    public String getLogicalPageId() {
        return logicalPageId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public int getWidthPx() {
        return widthPx;
    }

    public int getHeightPx() {
        return heightPx;
    }

    public T getContent() {
        return content;
    }

    public boolean hasOverlay() {
        return overlayContent != null;
    }

    public T getOverlayContent() {
        return overlayContent;
    }

    String identityKey() {
        return generationId + "\u0000" + logicalPageId + "\u0000" + ordinal;
    }

    String overlayIdentityKey() {
        return identityKey() + "\u0000overlay";
    }
}
