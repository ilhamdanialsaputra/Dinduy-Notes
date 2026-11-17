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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** State-machine transcription of the original PlayLikeCurl renderer and surface view. */
public final class PlayLikeCurlModel {
    public static final int GRID = 25;
    public static final float RADIUS = 0.18f;
    public static final int LEFT_ENDPOINT_PERCENT = 100;
    public static final int RIGHT_ENDPOINT_PERCENT = -5;
    public static final long SETTLEMENT_DURATION_MILLIS = 300L;
    public static final float LEFT_DEPTH = -0.001f;
    public static final float FRONT_DEPTH = -0.002f;
    public static final float RIGHT_DEPTH = -0.003f;
    public static final float RIGHT_ENDPOINT_POSITION = GRID * (RIGHT_ENDPOINT_PERCENT / 100f);
    private static final float RELEASE_COMMIT_POSITION =
            (GRID + RIGHT_ENDPOINT_POSITION) / 2f;

    private final int pageCount;
    private final PageState leftPage = new PageState(
            PageRole.LEFT, LEFT_DEPTH, RIGHT_ENDPOINT_POSITION, 0);
    private final PageState frontPage = new PageState(
            PageRole.FRONT, FRONT_DEPTH, GRID, 0);
    private final PageState rightPage = new PageState(
            PageRole.RIGHT, RIGHT_DEPTH, GRID, 0);
    private final List<PageState> drawOrder = Collections.unmodifiableList(
            Arrays.asList(leftPage, frontPage, rightPage));

    private ActivePage activePage = ActivePage.CURRENT;
    private int currentPosition;
    private float gestureStartX;
    private float gestureStartCurlPosition = GRID;

    public PlayLikeCurlModel(int pageCount, int initialPosition) {
        if (pageCount <= 0) throw new IllegalArgumentException("PlayLikeCurl requires at least one page");
        if (initialPosition < 0 || initialPosition >= pageCount) {
            throw new IllegalArgumentException("Initial page is outside the adapter");
        }
        this.pageCount = pageCount;
        currentPosition = initialPosition;
        resetPages();
        updatePageIdentities();
    }

    List<PageState> getDrawOrder() {
        return drawOrder;
    }

    PageState getLeftPage() {
        return leftPage;
    }

    PageState getFrontPage() {
        return frontPage;
    }

    PageState getRightPage() {
        return rightPage;
    }

    ActivePage getActivePage() {
        return activePage;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void jumpTo(int position) {
        if (position < 0 || position >= pageCount) {
            throw new IllegalArgumentException("Page is outside the adapter");
        }
        currentPosition = position;
        resetPages();
        updatePageIdentities();
    }

    public void beginGesture(float x) {
        gestureStartX = x;
        setActivePage(ActivePage.CURRENT);
        gestureStartCurlPosition = activePageState().getCurlPosition();
    }

    public void dragTo(float x, float width) {
        if (width <= 0f) return;
        float delta = x - gestureStartX;
        float movedFraction = delta / width;
        if (delta > 0f) {
            if (gestureStartCurlPosition >= GRID && canSwipePrevious()) {
                setActivePage(ActivePage.LEFT);
                gestureStartCurlPosition = activePageState().getCurlPosition();
            }
            float value = gestureStartCurlPosition + movedFraction * GRID;
            if (value <= GRID) activePageState().setCurlPosition(value);
        } else if (delta < 0f) {
            float value = (1f - Math.abs(movedFraction)) * GRID
                    - (GRID - gestureStartCurlPosition);
            if (canSwipeNext()) activePageState().setCurlPosition(value);
        }
    }

    Settlement release() {
        if (activePage == ActivePage.LEFT) {
            if (canSwipePrevious()
                    && activePageState().getCurlPosition() >= RELEASE_COMMIT_POSITION) {
                return settlement(
                        LEFT_ENDPOINT_PERCENT,
                        PageChange.PREVIOUS,
                        SettlementInterpolator.DECELERATE);
            }
            return settlement(
                    RIGHT_ENDPOINT_PERCENT,
                    PageChange.NONE,
                    SettlementInterpolator.ACCELERATE_DECELERATE);
        }
        if (canSwipeNext()
                && activePageState().getCurlPosition() <= RELEASE_COMMIT_POSITION) {
            return settlement(
                    RIGHT_ENDPOINT_PERCENT,
                    PageChange.NEXT,
                    SettlementInterpolator.DECELERATE);
        }
        return settlement(
                LEFT_ENDPOINT_PERCENT,
                PageChange.NONE,
                SettlementInterpolator.ACCELERATE_DECELERATE);
    }

    Settlement flingTowardNext() {
        if (!canSwipeNext()) return release();
        return settlement(
                RIGHT_ENDPOINT_PERCENT,
                PageChange.NEXT,
                SettlementInterpolator.DECELERATE);
    }

    Settlement flingTowardPrevious() {
        if (!canSwipePrevious()) return release();
        return settlement(
                LEFT_ENDPOINT_PERCENT,
                PageChange.PREVIOUS,
                SettlementInterpolator.DECELERATE);
    }

    Settlement turn(PageChange pageChange) {
        if (pageChange == PageChange.PREVIOUS) {
            if (!canSwipePrevious()) return release();
            setActivePage(ActivePage.LEFT);
            leftPage.setCurlPosition(RIGHT_ENDPOINT_POSITION);
            return flingTowardPrevious();
        }
        if (pageChange == PageChange.NEXT) {
            if (!canSwipeNext()) return release();
            setActivePage(ActivePage.CURRENT);
            frontPage.setCurlPosition(GRID);
            return flingTowardNext();
        }
        return release();
    }

    void updateSettlement(float valuePercent) {
        activePageState().setCurlPosition(GRID * valuePercent / 100f);
    }

    void cancelGesture() {
        resetPages();
    }

    void completeSettlement(Settlement settlement) {
        resetPages();
        if (settlement.getPageChange() == PageChange.PREVIOUS) {
            currentPosition = Math.max(0, currentPosition - 1);
        } else if (settlement.getPageChange() == PageChange.NEXT) {
            currentPosition = Math.min(pageCount - 1, currentPosition + 1);
        }
        updatePageIdentities();
    }

    private Settlement settlement(
            int targetPercent,
            PageChange pageChange,
            SettlementInterpolator interpolator) {
        return new Settlement(
                targetPercent,
                SETTLEMENT_DURATION_MILLIS,
                interpolator,
                pageChange);
    }

    private void resetPages() {
        leftPage.setCurlPosition(RIGHT_ENDPOINT_POSITION);
        rightPage.setCurlPosition(GRID);
        frontPage.setCurlPosition(GRID);
        setActivePage(ActivePage.CURRENT);
    }

    private void updatePageIdentities() {
        leftPage.setPageIndex(Math.max(0, currentPosition - 1));
        frontPage.setPageIndex(currentPosition);
        rightPage.setPageIndex(Math.min(pageCount - 1, currentPosition + 1));
    }

    private void setActivePage(ActivePage page) {
        activePage = page;
    }

    private PageState activePageState() {
        switch (activePage) {
            case LEFT:
                return leftPage;
            case RIGHT:
                return rightPage;
            case CURRENT:
            default:
                return frontPage;
        }
    }

    private boolean canSwipePrevious() {
        return currentPosition > 0;
    }

    private boolean canSwipeNext() {
        return currentPosition < pageCount - 1;
    }
}
