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

final class PageGeometry {
    private final PageRole role;
    private final float bitmapRatio;
    private final float[] positions;
    private final float[] textureCoordinates;
    private final short[] indices;

    PageGeometry(
            PageRole role,
            float bitmapRatio,
            float[] positions,
            float[] textureCoordinates,
            short[] indices) {
        this.role = role;
        this.bitmapRatio = bitmapRatio;
        this.positions = positions;
        this.textureCoordinates = textureCoordinates;
        this.indices = indices;
    }

    PageRole getRole() {
        return role;
    }

    float getBitmapRatio() {
        return bitmapRatio;
    }

    float[] getPositions() {
        return positions;
    }

    float[] getTextureCoordinates() {
        return textureCoordinates;
    }

    short[] getIndices() {
        return indices;
    }

    float positionY(int column, int row) {
        return positions[3 * (row * (PlayLikeCurlModel.GRID + 1) + column) + 1];
    }
}
