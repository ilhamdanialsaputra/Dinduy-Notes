/*
 * PlayLikeCurl page-curl engine (OpenGL ES 2.0), embedded verbatim as source.
 *
 * Original project : https://github.com/karankalsi/PlayLikeCurl
 * Maintained fork  : Arnab11/PlayLikeCurl (GLES2 renderer, production bitmap API v1, release 1.1.4)
 * Local changes    : package declaration (karacken.curl -> app.dinduy.silkcurl). Logic edits are marked "Local change"
 *                    in PageRenderer.updateMvp (fov 45f -> 55f), PageSurfaceView.completeSettlement, and
 *                    PlayLikeCurlGeometry.activeDepth (Play Books-like curl profile).
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

final class PlayLikeCurlGeometry {
    static final float CAMERA_DISTANCE = 2f;
    /** Local change: page x (screen units, 0 = spine side of the screen) where the hump crest is held. */
    private static final float CREST_SCREEN_X = 0.20f;
    /** Local change: grid columns over which the page rises to the crest (the visible bend near the spine). */
    private static final float RISE_COLUMNS = 10f;
    /** Local change: the descent toward the free edge is never shorter than this many columns. */
    private static final float MIN_FALL_COLUMNS = 4f;
    /** Local change: crest height as a multiple of the curl radius (upstream crest: 2.1). */
    private static final float PEAK_LIFT = 2.0f;
    /** Local change: base height of the page as a multiple of the curl radius (same as upstream). */
    private static final float BASE_LIFT = 0.1f;
    /** Local change: steepest average slope (dz/dx) allowed on the descent; lower = flatter right side. */
    private static final float MAX_FALL_SLOPE = 0.8f;

    private PlayLikeCurlGeometry() {
    }

    static PageGeometry createPage(
            PageRole role,
            int bitmapWidth,
            int bitmapHeight,
            PageOrientation orientation) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
            throw new IllegalArgumentException("Bitmap dimensions must be positive");
        }
        float bitmapRatio = bitmapRatio(bitmapWidth, bitmapHeight, orientation);
        int vertexCount = (PlayLikeCurlModel.GRID + 1) * (PlayLikeCurlModel.GRID + 1);
        PageGeometry page = new PageGeometry(
                role,
                bitmapRatio,
                new float[vertexCount * 3],
                createTextureCoordinates(),
                createIndices());
        update(
                page,
                role == PageRole.LEFT
                        ? PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION
                        : PlayLikeCurlModel.GRID,
                false);
        return page;
    }

    static void update(PageGeometry page, float curlPosition, boolean active) {
        int grid = PlayLikeCurlModel.GRID;
        float heightCorrection = (page.getBitmapRatio() - 1f) / 2f;
        for (int row = 0; row <= grid; row++) {
            for (int column = 0; column <= grid; column++) {
                int offset = 3 * (row * (grid + 1) + column);
                float normalizedX = column / (float) grid;
                if (page.getRole() == PageRole.FRONT) {
                    page.getPositions()[offset] = frontX(column, curlPosition);
                } else if (page.getRole() == PageRole.LEFT) {
                    page.getPositions()[offset] = leftX(column, curlPosition);
                } else {
                    page.getPositions()[offset] = normalizedX;
                }
                page.getPositions()[offset + 1] =
                        row / (float) grid * page.getBitmapRatio() - heightCorrection;
                page.getPositions()[offset + 2] = active
                        ? activeDepth(page.getRole(), column, curlPosition)
                        : depth(page.getRole());
            }
        }
    }

    static float projectionAspect(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        return height > width ? width / (float) height : height / (float) width;
    }

    static float bitmapRatio(int width, int height, PageOrientation orientation) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Bitmap dimensions must be positive");
        }
        return orientation == PageOrientation.PORTRAIT
                ? height / (float) width
                : width / (float) height;
    }

    static float foldEdgeX(PageRole role, float curlPosition) {
        if (role == PageRole.FRONT) {
            return frontX(PlayLikeCurlModel.GRID, curlPosition);
        }
        if (role == PageRole.LEFT) {
            return leftX(PlayLikeCurlModel.GRID, curlPosition);
        }
        return 1f;
    }

    static float foldEdgeDepth(PageRole role, float curlPosition) {
        return activeDepth(role, PlayLikeCurlModel.GRID, curlPosition);
    }

    static float projectXOntoDepthPlane(
            float sourceX,
            float sourceDepth,
            float targetDepth) {
        return 0.5f
                + (sourceX - 0.5f)
                * (CAMERA_DISTANCE - targetDepth)
                / (CAMERA_DISTANCE - sourceDepth);
    }

    private static float frontX(int column, float curlPosition) {
        float percentage = 1f - curlPosition / PlayLikeCurlModel.GRID;
        float radius = resolvedRadius(percentage);
        float movement = percentage > 0.05f ? percentage - 0.05f : 0f;
        return column / (float) PlayLikeCurlModel.GRID * (1f - radius) - movement;
    }

    private static float leftX(int column, float curlPosition) {
        float percentage = (1f - curlPosition / PlayLikeCurlModel.GRID) * 0.75f;
        float radius = resolvedRadius(percentage);
        return column / (float) PlayLikeCurlModel.GRID * (1f - radius) - percentage;
    }

    /**
     * Local change: depth profile of the page being turned, shaped like Google Play Books.
     *
     * <p>Upstream used a travelling sine wave, whose far slope dived steeply near the free (right) edge
     * and whose rising slope tilted that edge up toward the viewer as the page left the screen.
     *
     * <p>This profile is one hump: a smooth rise near the spine (left), a crest, then a long gentle
     * half-cosine descent that reaches the base height exactly at the free edge. The edge therefore has
     * zero slope at every progress value (it never dives and never faces up). The hump height is capped so
     * the descent never gets steeper than {@link #MAX_FALL_SLOPE}, which flattens the page smoothly as it
     * leaves the screen. The crest is pinned to a fixed screen position via the same x shift that
     * {@link #frontX} / {@link #leftX} use.
     */
    private static float activeDepth(PageRole role, int column, float curlPosition) {
        if (role == PageRole.RIGHT) return PlayLikeCurlModel.RIGHT_DEPTH;
        int grid = PlayLikeCurlModel.GRID;
        float rawPercentage = 1f - curlPosition / grid;
        float percentage = role == PageRole.LEFT ? rawPercentage * 0.75f : rawPercentage;
        float radius = resolvedRadius(percentage);
        float shift = role == PageRole.LEFT
                ? percentage
                : (percentage > 0.05f ? percentage - 0.05f : 0f);
        float span = 1f - radius;
        float crest = grid * (CREST_SCREEN_X + shift) / span;
        crest = Math.max(RISE_COLUMNS, Math.min(crest, grid - MIN_FALL_COLUMNS));
        float fall = grid - crest;
        float height = Math.min(
                radius * PEAK_LIFT,
                MAX_FALL_SLOPE * fall * (span / grid) / (float) (Math.PI / 2.0));
        float profile;
        if (column <= crest) {
            float t = (column - (crest - RISE_COLUMNS)) / RISE_COLUMNS;
            t = Math.max(0f, Math.min(1f, t));
            profile = 0.5f * (1f - (float) Math.cos(Math.PI * t));
        } else {
            float t = (column - crest) / fall;
            profile = 0.5f * (1f + (float) Math.cos(Math.PI * t));
        }
        return radius * BASE_LIFT + height * profile;
    }

    private static float resolvedRadius(float percentage) {
        return percentage < 0.20f
                ? PlayLikeCurlModel.RADIUS * percentage * 5f
                : PlayLikeCurlModel.RADIUS;
    }

    private static float depth(PageRole role) {
        if (role == PageRole.LEFT) return PlayLikeCurlModel.LEFT_DEPTH;
        if (role == PageRole.FRONT) return PlayLikeCurlModel.FRONT_DEPTH;
        return PlayLikeCurlModel.RIGHT_DEPTH;
    }

    private static float[] createTextureCoordinates() {
        int grid = PlayLikeCurlModel.GRID;
        float[] coordinates = new float[(grid + 1) * (grid + 1) * 2];
        for (int row = 0; row <= grid; row++) {
            for (int column = 0; column <= grid; column++) {
                int offset = 2 * (row * (grid + 1) + column);
                coordinates[offset] = column / (float) grid;
                coordinates[offset + 1] = 1f - row / (float) grid;
            }
        }
        return coordinates;
    }

    private static short[] createIndices() {
        int grid = PlayLikeCurlModel.GRID;
        short[] indices = new short[grid * grid * 6];
        for (int row = 0; row < grid; row++) {
            for (int column = 0; column < grid; column++) {
                int offset = 6 * (row * grid + column);
                indices[offset] = (short) (row * (grid + 1) + column);
                indices[offset + 1] = (short) (row * (grid + 1) + column + 1);
                indices[offset + 2] = (short) ((row + 1) * (grid + 1) + column);
                indices[offset + 3] = (short) (row * (grid + 1) + column + 1);
                indices[offset + 4] = (short) ((row + 1) * (grid + 1) + column + 1);
                indices[offset + 5] = (short) ((row + 1) * (grid + 1) + column);
            }
        }
        return indices;
    }
}
