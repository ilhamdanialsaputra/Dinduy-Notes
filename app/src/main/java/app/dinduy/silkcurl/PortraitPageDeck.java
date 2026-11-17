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

/** Previous, current, and next portrait pages for one interaction window. */
public final class PortraitPageDeck<T> implements PageDeck<T> {
    private final long generationId;
    private final PageImage<T> previous;
    private final PageImage<T> current;
    private final PageImage<T> next;
    private final List<PageImage<T>> pages;

    public PortraitPageDeck(
            PageImage<T> previous,
            PageImage<T> current,
            PageImage<T> next) {
        this.previous = Objects.requireNonNull(previous, "previous");
        this.current = Objects.requireNonNull(current, "current");
        this.next = Objects.requireNonNull(next, "next");
        generationId = current.getGenerationId();
        requireGeneration(previous);
        requireGeneration(next);
        pages = List.of(previous, current, next);
    }

    @Override
    public long getGenerationId() {
        return generationId;
    }

    @Override
    public PageDeckMode getMode() {
        return PageDeckMode.PORTRAIT;
    }

    @Override
    public List<PageImage<T>> getPages() {
        return pages;
    }

    public PageImage<T> getPrevious() {
        return previous;
    }

    public PageImage<T> getCurrent() {
        return current;
    }

    public PageImage<T> getNext() {
        return next;
    }

    private void requireGeneration(PageImage<T> page) {
        if (page.getGenerationId() != generationId) {
            throw new IllegalArgumentException("All portrait pages must share one generation");
        }
    }
}
