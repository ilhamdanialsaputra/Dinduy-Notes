/*
 * PlayLikeCurl page-curl engine (OpenGL ES 2.0), embedded verbatim as source.
 *
 * Original project : https://github.com/karankalsi/PlayLikeCurl
 * Maintained fork  : Arnab11/PlayLikeCurl (GLES2 renderer, production bitmap API v1, release 1.1.4)
 * Local changes    : package declaration (karacken.curl -> app.dinduy.silkcurl). Logic edits are marked "Local change"
 *                    in PageRenderer (updateMvp fov 45f -> 55f, uShade), PageSurfaceView (gesture / settlement lifecycle),
 *                    PlayLikeCurlModel, Settlement, SettlementInterpolator, PlayLikeCurlGeometry (rigid hinge rotation),
 *                    PageGeometry (per-column scratch arrays), FoldShadowModel (variable width).
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
 * Local change: the moving sheet is no longer squeezed with an S-shaped wave. It is a (nearly) rigid sheet that rotates
 * around its left edge (the book spine, world x = 0) in 3D. The free edge rises toward the camera, so the perspective
 * projection itself makes the sheet grow, tilts the top / bottom edges, and swings it out past the left edge of the view.
 * A very small bend (the free edge leads slightly) keeps it from looking like a perfectly flat board.
 *
 * The same sheet-angle function drives both directions: FRONT (current page, forward) goes flat -> raised, LEFT (previous
 * page, backward) goes raised -> flat, so backward is the exact time-reverse of forward.
 */
final class PlayLikeCurlGeometry {
    static final float CAMERA_DISTANCE = 2f;

    /** Sheet angle (rad) at which the sheet is fully outside the left edge of the view (about 80 deg). */
    static final float MAX_SHEET_ANGLE = 1.40f;
    /** Scale of the sheet's rise toward the camera (1 = true rigid rotation). Lower = weaker zoom-in. */
    static final float SHEET_LIFT = 0.85f;
    /** Extra angle (rad) at the free edge, relative to the hinge. Small, smooth curvature. */
    static final float SHEET_BEND = 0.12f;
    /** How much the raised sheet darkens as it turns away (0 = none). */
    static final float SHEET_SHADE = 0.30f;
    /** > 1 so the sheet lifts quickly while the finger drags, and still eases out softly at the far end. */
    private static final float ANGLE_EXPONENT = 1.5f;

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
        PageRole role = page.getRole();
        float heightCorrection = (page.getBitmapRatio() - 1f) / 2f;
        boolean lifted = active && role != PageRole.RIGHT;
        float[] columnX = page.getColumnX();
        float[] columnZ = page.getColumnZ();
        if (lifted) {
            fillSheet(role, curlPosition, columnX, columnZ);
        }
        float baseDepth = depth(role);
        float[] positions = page.getPositions();
        for (int row = 0; row <= grid; row++) {
            float y = row / (float) grid * page.getBitmapRatio() - heightCorrection;
            for (int column = 0; column <= grid; column++) {
                int offset = 3 * (row * (grid + 1) + column);
                float x;
                float z;
                if (lifted) {
                    x = columnX[column];
                    z = baseDepth + columnZ[column];
                } else {
                    x = column / (float) grid;
                    // a resting previous page waits flat, fully outside the left edge of the view
                    if (role == PageRole.LEFT) x -= 1f;
                    z = baseDepth;
                }
                positions[offset] = x;
                positions[offset + 1] = y;
                positions[offset + 2] = z;
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

    /** Rotation of the sheet around the spine, in radians: 0 = lying flat on the right, MAX_SHEET_ANGLE = gone to the left. */
    static float sheetAngle(PageRole role, float curlPosition) {
        if (role == PageRole.RIGHT) return 0f;
        float range = PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION;
        float gone;
        if (role == PageRole.FRONT) {
            gone = (PlayLikeCurlModel.GRID - curlPosition) / range;
        } else {
            gone = 1f - (curlPosition - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / range;
        }
        gone = Math.max(0f, Math.min(1f, gone));
        return MAX_SHEET_ANGLE * (1f - (float) Math.pow(1f - gone, ANGLE_EXPONENT));
    }

    /** Brightness multiplier of the moving sheet (1 = untouched). */
    static float sheetShade(PageRole role, float curlPosition) {
        float angle = sheetAngle(role, curlPosition);
        return 1f - SHEET_SHADE * (float) Math.sin(angle);
    }

    static float foldEdgeX(PageRole role, float curlPosition) {
        if (role == PageRole.RIGHT) return 1f;
        float theta = sheetAngle(role, curlPosition);
        float bend = SHEET_BEND * (float) Math.sin(theta);
        int grid = PlayLikeCurlModel.GRID;
        float step = 1f / grid;
        float x = 0f;
        for (int i = 1; i <= grid; i++) {
            x += (float) Math.cos(theta + bend * ((i - 0.5f) * step)) * step;
        }
        return x;
    }

    static float foldEdgeDepth(PageRole role, float curlPosition) {
        if (role == PageRole.RIGHT) return PlayLikeCurlModel.RIGHT_DEPTH;
        float theta = sheetAngle(role, curlPosition);
        float bend = SHEET_BEND * (float) Math.sin(theta);
        int grid = PlayLikeCurlModel.GRID;
        float step = 1f / grid;
        float z = 0f;
        for (int i = 1; i <= grid; i++) {
            z += (float) Math.sin(theta + bend * ((i - 0.5f) * step)) * step;
        }
        return depth(role) + SHEET_LIFT * z;
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

    /** Integrates the sheet along its width so its arc length stays constant (no stretching / compressing). */
    private static void fillSheet(PageRole role, float curlPosition, float[] columnX, float[] columnZ) {
        float theta = sheetAngle(role, curlPosition);
        float bend = SHEET_BEND * (float) Math.sin(theta);
        int grid = PlayLikeCurlModel.GRID;
        float step = 1f / grid;
        columnX[0] = 0f;
        columnZ[0] = 0f;
        for (int i = 1; i <= grid; i++) {
            float angle = theta + bend * ((i - 0.5f) * step);
            columnX[i] = columnX[i - 1] + (float) Math.cos(angle) * step;
            columnZ[i] = columnZ[i - 1] + SHEET_LIFT * (float) Math.sin(angle) * step;
        }
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
