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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalized unique page sequence used by the unchanged PlayLikeCurl position model. */
final class PageDeckWindow<T> {
    private final List<PageImage<T>> pages;
    private final int currentIndex;

    private PageDeckWindow(List<PageImage<T>> pages, int currentIndex) {
        this.pages = Collections.unmodifiableList(pages);
        this.currentIndex = currentIndex;
    }

    static <T> PageDeckWindow<T> from(PageDeck<T> deck) {
        PageImage<T> current;
        if (deck instanceof PortraitPageDeck) {
            current = ((PortraitPageDeck<T>) deck).getCurrent();
        } else if (deck instanceof LandscapePageDeck) {
            current = ((LandscapePageDeck<T>) deck).getCurrentLeft();
        } else {
            throw new IllegalArgumentException("Unsupported page deck type");
        }

        Map<String, PageImage<T>> uniquePages = new LinkedHashMap<>();
        for (PageImage<T> page : deck.getPages()) {
            uniquePages.putIfAbsent(page.identityKey(), page);
        }
        List<PageImage<T>> normalized = new ArrayList<>(uniquePages.values());
        int normalizedCurrentIndex = -1;
        for (int index = 0; index < normalized.size(); index++) {
            if (normalized.get(index).identityKey().equals(current.identityKey())) {
                normalizedCurrentIndex = index;
                break;
            }
        }
        if (normalizedCurrentIndex < 0) {
            throw new IllegalArgumentException("Deck does not contain its current page");
        }
        return new PageDeckWindow<>(normalized, normalizedCurrentIndex);
    }

    List<PageImage<T>> getPages() {
        return pages;
    }

    int getCurrentIndex() {
        return currentIndex;
    }
}
