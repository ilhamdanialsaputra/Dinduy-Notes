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

import java.util.List;
import java.util.Objects;

/** Previous, current, and next two-leaf spreads for one landscape interaction window. */
public final class LandscapePageDeck<T> implements PageDeck<T> {
    private final long generationId;
    private final PageImage<T> previousLeft;
    private final PageImage<T> previousRight;
    private final PageImage<T> currentLeft;
    private final PageImage<T> currentRight;
    private final PageImage<T> nextLeft;
    private final PageImage<T> nextRight;
    private final List<PageImage<T>> pages;

    public LandscapePageDeck(
            PageImage<T> previousLeft,
            PageImage<T> previousRight,
            PageImage<T> currentLeft,
            PageImage<T> currentRight,
            PageImage<T> nextLeft,
            PageImage<T> nextRight) {
        this.previousLeft = Objects.requireNonNull(previousLeft, "previousLeft");
        this.previousRight = Objects.requireNonNull(previousRight, "previousRight");
        this.currentLeft = Objects.requireNonNull(currentLeft, "currentLeft");
        this.currentRight = Objects.requireNonNull(currentRight, "currentRight");
        this.nextLeft = Objects.requireNonNull(nextLeft, "nextLeft");
        this.nextRight = Objects.requireNonNull(nextRight, "nextRight");
        generationId = currentLeft.getGenerationId();
        requireGeneration(previousLeft);
        requireGeneration(previousRight);
        requireGeneration(currentRight);
        requireGeneration(nextLeft);
        requireGeneration(nextRight);
        pages = List.of(
                previousLeft,
                previousRight,
                currentLeft,
                currentRight,
                nextLeft,
                nextRight);
    }

    @Override
    public long getGenerationId() {
        return generationId;
    }

    @Override
    public PageDeckMode getMode() {
        return PageDeckMode.LANDSCAPE;
    }

    @Override
    public List<PageImage<T>> getPages() {
        return pages;
    }

    public PageImage<T> getPreviousLeft() {
        return previousLeft;
    }

    public PageImage<T> getPreviousRight() {
        return previousRight;
    }

    public PageImage<T> getCurrentLeft() {
        return currentLeft;
    }

    public PageImage<T> getCurrentRight() {
        return currentRight;
    }

    public PageImage<T> getNextLeft() {
        return nextLeft;
    }

    public PageImage<T> getNextRight() {
        return nextRight;
    }

    private void requireGeneration(PageImage<T> page) {
        if (page.getGenerationId() != generationId) {
            throw new IllegalArgumentException("All landscape leaves must share one generation");
        }
    }
}
