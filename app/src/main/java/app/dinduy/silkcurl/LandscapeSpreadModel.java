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

/**
 * Two-page spread adapter around PlayLikeCurl's unchanged gesture and settlement model.
 * Page indices are zero based, so the first visible spread is 0 | 1.
 */
final class LandscapeSpreadModel {
    private final int pageCount;
    private final int spreadCount;
    private final PlayLikeCurlModel motionModel;

    LandscapeSpreadModel(int pageCount, int initialPageIndex) {
        if (pageCount <= 0) {
            throw new IllegalArgumentException("A landscape spread requires at least one page");
        }
        if (initialPageIndex < 0 || initialPageIndex >= pageCount) {
            throw new IllegalArgumentException("Initial page is outside the adapter");
        }
        this.pageCount = pageCount;
        spreadCount = (pageCount + 1) / 2;
        motionModel = new PlayLikeCurlModel(spreadCount, initialPageIndex / 2);
    }

    PlayLikeCurlModel getMotionModel() {
        return motionModel;
    }

    int getCurrentLeftPageIndex() {
        return pageIndexForSpread(motionModel.getCurrentPosition(), false);
    }

    int getCurrentRightPageIndex() {
        return pageIndexForSpread(motionModel.getCurrentPosition(), true);
    }

    int getPreviousLeftPageIndex() {
        return pageIndexForSpread(Math.max(0, motionModel.getCurrentPosition() - 1), false);
    }

    int getPreviousRightPageIndex() {
        return pageIndexForSpread(Math.max(0, motionModel.getCurrentPosition() - 1), true);
    }

    int getNextLeftPageIndex() {
        return pageIndexForSpread(
                Math.min(spreadCount - 1, motionModel.getCurrentPosition() + 1), false);
    }

    int getNextRightPageIndex() {
        return pageIndexForSpread(
                Math.min(spreadCount - 1, motionModel.getCurrentPosition() + 1), true);
    }

    int getForwardTurningPageIndex() {
        return getCurrentRightPageIndex();
    }

    int getForwardReversePageIndex() {
        return getNextLeftPageIndex();
    }

    int getForwardUnderneathPageIndex() {
        return getNextRightPageIndex();
    }

    int getBackwardTurningPageIndex() {
        return getCurrentLeftPageIndex();
    }

    int getBackwardReversePageIndex() {
        return getPreviousRightPageIndex();
    }

    int getBackwardUnderneathPageIndex() {
        return getPreviousLeftPageIndex();
    }

    int getCurrentPageIndex() {
        return getCurrentLeftPageIndex();
    }

    void jumpTo(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pageCount) {
            throw new IllegalArgumentException("Page is outside the adapter");
        }
        motionModel.jumpTo(pageIndex / 2);
    }

    void beginGesture(float x) {
        motionModel.beginGesture(x);
    }

    void dragTo(float x, float leafWidth) {
        float endpointAdjustedWidth = leafWidth
                * PlayLikeCurlModel.GRID
                / (PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION);
        motionModel.dragTo(x, endpointAdjustedWidth);
    }

    LandscapeSpreadTransition getTransition() {
        return LandscapeSpreadTransition.from(motionModel);
    }

    Settlement release() {
        return motionModel.release();
    }

    Settlement flingTowardNext() {
        return motionModel.flingTowardNext();
    }

    Settlement flingTowardPrevious() {
        return motionModel.flingTowardPrevious();
    }

    void updateSettlement(float valuePercent) {
        motionModel.updateSettlement(valuePercent);
    }

    void cancelGesture() {
        motionModel.cancelGesture();
    }

    void completeSettlement(Settlement settlement) {
        motionModel.completeSettlement(settlement);
    }

    private int pageIndexForSpread(int spreadIndex, boolean right) {
        int rawIndex = spreadIndex * 2 + (right ? 1 : 0);
        return Math.min(pageCount - 1, rawIndex);
    }
}
