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

/** Structured renderer failure reported to the client instead of thrown from GL callbacks. */
public final class RenderFailure {
    private final long generationId;
    private final boolean recoverable;
    private final RenderFailureReason reason;
    private final String message;
    private final Throwable cause;
    private final int requestedWidthPx;
    private final int requestedHeightPx;
    private final int maxTextureSize;
    private final long requiredBytes;
    private final long gpuBudgetBytes;

    public RenderFailure(
            long generationId,
            boolean recoverable,
            RenderFailureReason reason,
            String message,
            Throwable cause) {
        this(
                generationId,
                recoverable,
                reason,
                message,
                cause,
                0,
                0,
                0,
                0L,
                0L);
    }

    public RenderFailure(
            long generationId,
            boolean recoverable,
            RenderFailureReason reason,
            String message,
            Throwable cause,
            int requestedWidthPx,
            int requestedHeightPx,
            int maxTextureSize,
            long requiredBytes,
            long gpuBudgetBytes) {
        this.generationId = generationId;
        this.recoverable = recoverable;
        this.reason = reason;
        this.message = message;
        this.cause = cause;
        this.requestedWidthPx = requestedWidthPx;
        this.requestedHeightPx = requestedHeightPx;
        this.maxTextureSize = maxTextureSize;
        this.requiredBytes = requiredBytes;
        this.gpuBudgetBytes = gpuBudgetBytes;
    }

    public long getGenerationId() {
        return generationId;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    public RenderFailureReason getReason() {
        return reason;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getCause() {
        return cause;
    }

    public int getRequestedWidthPx() {
        return requestedWidthPx;
    }

    public int getRequestedHeightPx() {
        return requestedHeightPx;
    }

    public int getMaxTextureSize() {
        return maxTextureSize;
    }

    public long getRequiredBytes() {
        return requiredBytes;
    }

    public long getGpuBudgetBytes() {
        return gpuBudgetBytes;
    }
}
