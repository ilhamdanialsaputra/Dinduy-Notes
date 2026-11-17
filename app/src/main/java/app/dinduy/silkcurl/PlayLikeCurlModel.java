/*
 * PlayLikeCurl page-curl engine (OpenGL ES 2.0), embedded verbatim as source.
 *
 * Original project : https://github.com/karankalsi/PlayLikeCurl
 * Maintained fork  : Arnab11/PlayLikeCurl (GLES2 renderer, production bitmap API v1, release 1.1.4)
 * Local changes    : package declaration (karacken.curl -> app.dinduy.silkcurl). Logic edits are marked "Local change"
 *                    in PageRenderer.updateMvp (fov 45f -> 55f) PageSurfaceView (gesture / settlement lifecycle), PlayLikeCurlModel, Settlement, SettlementInterpolator (turn, revert, grab, queue).
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
    /** Duration of a fling / release that commits a page change (unchanged from upstream). */
    public static final long SETTLEMENT_DURATION_MILLIS = 300L;
    /** Local change: duration of a full edge-tap turn. Soft start, so it reads smooth rather than abrupt. */
    public static final long TURN_DURATION_MILLIS = 480L;
    /** Local change: duration of a full return to the original page after an uncompleted drag. */
    public static final long REVERT_DURATION_MILLIS = 240L;
    /** Local change: shortest settlement produced by {@link #timed}; keeps tiny corrections visible but quick. */
    public static final long MIN_SETTLEMENT_MILLIS = 120L;
    public static final float LEFT_DEPTH = -0.001f;
    public static final float FRONT_DEPTH = -0.002f;
    public static final float RIGHT_DEPTH = -0.003f;
    public static final float RIGHT_ENDPOINT_POSITION = GRID * (RIGHT_ENDPOINT_PERCENT / 100f);
    private static final float RELEASE_COMMIT_POSITION =
            (GRID + RIGHT_ENDPOINT_POSITION) / 2f;
    private static final float FULL_TRAVEL_PERCENT =
            LEFT_ENDPOINT_PERCENT - RIGHT_ENDPOINT_PERCENT;
    private static final float DISPLACED_EPSILON = 0.001f;

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

    /**
     * Local change: starts a gesture WITHOUT resetting a page that is still in motion (finger lands on a
     * page that is returning / settling). The drag continues from the page's current position, so there
     * is no jump. At rest this is exactly {@link #beginGesture(float)}.
     */
    void grabGesture(float x) {
        if (!isDisplaced()) {
            beginGesture(x);
            return;
        }
        gestureStartX = x;
        gestureStartCurlPosition = activePageState().getCurlPosition();
    }

    /** Local change: true when the active page is not at its rest position (mid drag / mid settlement). */
    boolean isDisplaced() {
        float rest = activePage == ActivePage.LEFT ? RIGHT_ENDPOINT_POSITION : GRID;
        return Math.abs(activePageState().getCurlPosition() - rest) > DISPLACED_EPSILON;
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
            // Local change: clamp instead of ignoring, so a grabbed page lands exactly on its rest pose.
            activePageState().setCurlPosition(Math.min(value, GRID));
        } else if (delta < 0f) {
            float value = (1f - Math.abs(movedFraction)) * GRID
                    - (GRID - gestureStartCurlPosition);
            if (canSwipeNext()) {
                activePageState().setCurlPosition(Math.max(value, RIGHT_ENDPOINT_POSITION));
            }
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
            return revert();
        }
        if (canSwipeNext()
                && activePageState().getCurlPosition() <= RELEASE_COMMIT_POSITION) {
            return settlement(
                    RIGHT_ENDPOINT_PERCENT,
                    PageChange.NEXT,
                    SettlementInterpolator.DECELERATE);
        }
        return revert();
    }

    /**
     * Local change: short, smooth return of the active page to its rest pose (page change NONE).
     * Duration scales with the remaining distance, so a page that is almost home returns almost instantly.
     */
    Settlement revert() {
        int target = activePage == ActivePage.LEFT
                ? RIGHT_ENDPOINT_PERCENT
                : LEFT_ENDPOINT_PERCENT;
        return timed(
                target,
                PageChange.NONE,
                SettlementInterpolator.FAST_OUT_SLOW_IN,
                REVERT_DURATION_MILLIS);
    }

    Settlement flingTowardNext() {
        if (!canSwipeNext()) return release();
        // Local change: flicking toward "next" while the PREVIOUS page is being dragged puts that page away
        // (revert). Upstream animated it away but still committed PageChange.NEXT.
        if (activePage == ActivePage.LEFT) return revert();
        return settlement(
                RIGHT_ENDPOINT_PERCENT,
                PageChange.NEXT,
                SettlementInterpolator.DECELERATE);
    }

    Settlement flingTowardPrevious() {
        if (!canSwipePrevious()) return release();
        // Local change: flicking toward "previous" while the CURRENT page is being dragged away brings it
        // back (revert). Upstream animated it home but still committed PageChange.PREVIOUS.
        if (activePage == ActivePage.CURRENT) return revert();
        return settlement(
                LEFT_ENDPOINT_PERCENT,
                PageChange.PREVIOUS,
                SettlementInterpolator.DECELERATE);
    }

    /**
     * Edge turn. If the matching page is still returning (an interrupted revert) the turn continues from
     * where that page is; otherwise it starts from the rest pose.
     */
    Settlement turn(PageChange pageChange) {
        if (pageChange == PageChange.PREVIOUS) {
            if (!canSwipePrevious()) return release();
            float left = leftPage.getCurlPosition();
            boolean continuing = activePage == ActivePage.LEFT
                    && left > RIGHT_ENDPOINT_POSITION
                    && left < GRID;
            if (!continuing) {
                resetPages();
                setActivePage(ActivePage.LEFT);
                leftPage.setCurlPosition(RIGHT_ENDPOINT_POSITION);
            }
            return timed(
                    LEFT_ENDPOINT_PERCENT,
                    PageChange.PREVIOUS,
                    SettlementInterpolator.FAST_OUT_SLOW_IN,
                    TURN_DURATION_MILLIS);
        }
        if (pageChange == PageChange.NEXT) {
            if (!canSwipeNext()) return release();
            float front = frontPage.getCurlPosition();
            boolean continuing = activePage == ActivePage.CURRENT
                    && front < GRID
                    && front > RIGHT_ENDPOINT_POSITION;
            if (!continuing) {
                resetPages();
                setActivePage(ActivePage.CURRENT);
                frontPage.setCurlPosition(GRID);
            }
            return timed(
                    RIGHT_ENDPOINT_PERCENT,
                    PageChange.NEXT,
                    SettlementInterpolator.FAST_OUT_SLOW_IN,
                    TURN_DURATION_MILLIS);
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

    /** Settlement whose duration is {@code baseMillis} scaled by the distance still to travel. */
    private Settlement timed(
            int targetPercent,
            PageChange pageChange,
            SettlementInterpolator interpolator,
            long baseMillis) {
        float fromPercent = activePageState().getCurlPosition() / GRID * 100f;
        float travel = Math.abs(fromPercent - targetPercent) / FULL_TRAVEL_PERCENT;
        travel = Math.max(0.35f, Math.min(1f, travel));
        long duration = Math.max(MIN_SETTLEMENT_MILLIS, Math.round(baseMillis * travel));
        return new Settlement(targetPercent, duration, interpolator, pageChange);
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
