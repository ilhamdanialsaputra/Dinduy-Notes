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

/** Two-phase leaf transition derived from PlayLikeCurl's unchanged curl position. */
final class LandscapeSpreadTransition {
    private final boolean forward;
    private final float progress;
    private final float turningCurlPosition;
    private final float incomingCurlPosition;

    private LandscapeSpreadTransition(
            boolean forward,
            float progress,
            float turningCurlPosition,
            float incomingCurlPosition) {
        this.forward = forward;
        this.progress = progress;
        this.turningCurlPosition = turningCurlPosition;
        this.incomingCurlPosition = incomingCurlPosition;
    }

    static LandscapeSpreadTransition from(PlayLikeCurlModel motion) {
        boolean forward = motion.getActivePage() != ActivePage.LEFT;
        float curlPosition = forward
                ? motion.getFrontPage().getCurlPosition()
                : motion.getLeftPage().getCurlPosition();
        float range = PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION;
        float progress = forward
                ? (PlayLikeCurlModel.GRID - curlPosition) / range
                : (curlPosition - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / range;
        progress = clamp(progress);
        float turningProgress = clamp(progress * 2f);
        float incomingProgress = clamp((progress - 0.5f) * 2f);
        return new LandscapeSpreadTransition(
                forward,
                progress,
                lerp(
                        PlayLikeCurlModel.GRID,
                        PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION,
                        turningProgress),
                lerp(
                        PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION,
                        PlayLikeCurlModel.GRID,
                        incomingProgress));
    }

    boolean isForward() {
        return forward;
    }

    float getProgress() {
        return progress;
    }

    float getTurningCurlPosition() {
        return turningCurlPosition;
    }

    float getIncomingCurlPosition() {
        return incomingCurlPosition;
    }

    boolean isTurningCurrentLeafVisible() {
        return progress > 0f && progress < 0.5f;
    }

    boolean isIncomingReverseLeafVisible() {
        return progress > 0.5f;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
}
