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

/** Device and policy limits that must be known before a page deck is accepted. */
public final class RenderCapabilities {
    private static final int BYTES_PER_PIXEL = 4;

    private final int maxTextureSize;
    private final long gpuBudgetBytes;

    public RenderCapabilities(int maxTextureSize, long gpuBudgetBytes) {
        if (maxTextureSize <= 0) {
            throw new IllegalArgumentException("maxTextureSize must be positive");
        }
        if (gpuBudgetBytes <= 0) {
            throw new IllegalArgumentException("gpuBudgetBytes must be positive");
        }
        this.maxTextureSize = maxTextureSize;
        this.gpuBudgetBytes = gpuBudgetBytes;
    }

    public int getMaxTextureSize() {
        return maxTextureSize;
    }

    public long getGpuBudgetBytes() {
        return gpuBudgetBytes;
    }

    public int getBytesPerPixel() {
        return BYTES_PER_PIXEL;
    }
}
