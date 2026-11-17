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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Production page-curl surface.
 *
 * <p>The client owns bitmap decoding and page identity. This view owns only interaction,
 * settlement, and GL resource lifecycle. Public mutations must run on the Android main thread.
 *
 * <p>Local change (responsiveness): a touch or turn is NEVER refused just because a settlement is
 * running.
 * <ul>
 *   <li>Settlement that only returns to the original page (PageChange.NONE): a new touch takes the page
 *       over from its current position (no reset, no jump); a new turn continues from there.</li>
 *   <li>Settlement that commits a page change: turns are queued and chained; a new drag is deferred for
 *       a few frames while the running settlement is expedited, then it begins where the finger is.</li>
 *   <li>The one-frame gap between "settlement completed" and "GL thread swapped in the new deck" is
 *       bridged ({@code activationPending}) so no gesture ever drives the old, about-to-be-discarded model.</li>
 * </ul>
 * Superseded settlements are always reported through onSettlementCancelled BEFORE the next
 * onSettlementStarted, and never after, so listeners can treat callbacks as strictly ordered.
 */
public class PageSurfaceView extends GLSurfaceView {
    private static final float FLING_THRESHOLD_PX_PER_SECOND = 200f;
    public static final long NO_GESTURE_ID = -1L;
    private static final PageSurfaceListener NO_OP_LISTENER = new PageSurfaceListener() {};

    /** Local change: most turns that can wait behind a running settlement. */
    private static final int MAX_QUEUED_TURNS = 6;
    /** Local change: a queued turn older than this is dropped (never replayed long after the tap). */
    private static final long QUEUED_TURN_TTL_MILLIS = 1500L;
    /** Local change: a committing settlement is finished within this time when a finger lands on it. */
    private static final long EXPEDITE_DURATION_MILLIS = 110L;
    /** Local change: shortest turn when several are chained. */
    private static final long MIN_CHAINED_TURN_MILLIS = 260L;
    /** Local change: safety net so activationPending can never stay true if the GL thread is slow or paused. */
    private static final long ACTIVATION_FALLBACK_MILLIS = 300L;

    private final PageDeckCoordinator<Bitmap> deckCoordinator = new PageDeckCoordinator<>();
    private final DeckLeaseRegistry leaseRegistry = new DeckLeaseRegistry();
    private final Set<Long> preparedGenerations = new LinkedHashSet<>();
    private final ArrayDeque<QueuedTurn> turnQueue = new ArrayDeque<>();
    private final PageRenderer renderer;
    private final int touchSlop;

    private PageSurfaceListener pageSurfaceListener = NO_OP_LISTENER;
    private RenderCapabilities renderCapabilities;
    private ValueAnimator settlementAnimator;
    private SettlementContext activeSettlementContext;
    private Settlement activeSettlement;
    private VelocityTracker velocityTracker;
    private boolean settlementRunning;
    private boolean settlementExpedited;
    private boolean activationPending;
    private int activationToken;
    private boolean gestureAccepted;
    private boolean gestureMoved;
    private boolean gestureBeginPending;
    private boolean pendingMoved;
    private float pendingX;
    private float pendingY;
    private boolean surfaceVisible = true;
    private boolean attached;
    private boolean disposed;
    private long activeGestureId = NO_GESTURE_ID;
    private float gestureDownX;
    private float gestureDownY;
    private OnPageChangeListener onPageChangeListener;

    public PageSurfaceView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        renderer = new PageRenderer(new PageRenderer.Events() {
            @Override
            public void onCapabilitiesAvailable(RenderCapabilities capabilities) {
                post(() -> handleCapabilitiesAvailable(capabilities));
            }

            @Override
            public void onDeckPrepared(long generationId) {
                post(() -> handleDeckPrepared(generationId));
            }

            @Override
            public void onDeckReleased(
                    long generationId,
                    DeckReleaseReason reason) {
                leaseRegistry.markReleaseRequested(generationId, reason);
                post(() -> handleDeckReleased(generationId, reason));
            }

            @Override
            public void onRenderFailure(RenderFailure failure) {
                post(() -> handleRenderFailure(failure));
            }
        });

        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setPreserveEGLContextOnPause(true);
        setClickable(true);
        setFocusable(true);
    }

    /** Marks the surface active for interaction and resumes its GL thread. */
    public void attach() {
        requireMainThread();
        if (disposed || attached) {
            return;
        }
        attached = true;
        onResume();
        requestRender();
    }

    /** Cancels interaction and pauses frame production without releasing the active deck. */
    public void detach() {
        requireMainThread();
        if (!attached) {
            return;
        }
        attached = false;
        cancelGesture();
        queueDeckRelease(deckCoordinator.releasePending(
                DeckReleaseReason.SESSION_DETACHED));
        requestRender();
        onPause();
    }

    /**
     * Submits one complete interaction window.
     *
     * <p>An idle submission becomes active after upload. During settlement, exactly one
     * replacement deck is retained and prepared. Acceptance starts a bitmap lease: every bitmap
     * in the deck must remain immutable and unrecycled until
     * {@link PageSurfaceListener#onDeckReleased(long, DeckReleaseReason)} reports the generation.
     * Rejected decks are never acquired.
     */
    public void submitDeck(PageDeck<Bitmap> deck) {
        requireMainThread();
        if (deck == null) {
            throw new IllegalArgumentException("deck must not be null");
        }
        if (disposed) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(),
                    DeckRejectionReason.DISPOSED);
            return;
        }
        if (!attached) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(),
                    DeckRejectionReason.SESSION_DETACHED);
            return;
        }
        if (renderCapabilities == null) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(),
                    DeckRejectionReason.CAPABILITIES_UNAVAILABLE);
            return;
        }
        PageDeckCoordinator.Offer<Bitmap> offer = deckCoordinator.offer(deck);
        if (offer.getPlacement() == PageDeckCoordinator.Placement.REJECTED) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(), offer.getRejectionReason());
            return;
        }
        if (offer.getPlacement() == PageDeckCoordinator.Placement.UNCHANGED) {
            return;
        }
        leaseRegistry.acquire(deck.getGenerationId(), pageSurfaceListener);
        for (PageDeckCoordinator.Release<Bitmap> release : offer.getReleases()) {
            queueDeckRelease(release);
        }
        boolean activateWhenPrepared =
                offer.getPlacement() == PageDeckCoordinator.Placement.ACTIVE;
        preparedGenerations.remove(deck.getGenerationId());
        queueEvent(() -> renderer.prepareDeck(deck, activateWhenPrepared));
        requestRender();
    }

    /** Supplies the current pixel viewport without changing page layout. */
    public void setViewport(int widthPx, int heightPx) {
        requireMainThread();
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        queueEvent(() -> renderer.setViewport(widthPx, heightPx));
        requestRender();
    }

    /**
     * Changes the active-plus-pending GPU budget before any deck is acquired.
     */
    public void setGpuBudgetBytes(long gpuBudgetBytes) {
        requireMainThread();
        if (gpuBudgetBytes <= 0) {
            throw new IllegalArgumentException("gpuBudgetBytes must be positive");
        }
        if (deckCoordinator.getActiveDeck() != null
                || deckCoordinator.getPendingDeck() != null) {
            throw new IllegalStateException(
                    "GPU budget cannot change while a deck is retained");
        }
        queueEvent(() -> renderer.setGpuBudgetBytes(gpuBudgetBytes));
        requestRender();
    }

    /** Controls rendering and gesture eligibility without discarding the prepared deck. */
    public void setVisible(boolean visible) {
        requireMainThread();
        surfaceVisible = visible;
        if (!visible) {
            cancelGesture();
        } else {
            requestRender();
        }
    }

    /** Cancels any partial gesture, queued turn or settlement without navigating. */
    public void cancelGesture() {
        cancelGesture(activeGestureId);
    }

    /** Cancels the identified gesture or settlement without navigating. */
    public void cancelGesture(long gestureId) {
        requireMainThread();
        SettlementContext cancelledSettlement = cancelSettlementAnimator();
        long cancelledGestureId = activeGestureId != NO_GESTURE_ID
                ? activeGestureId
                : gestureId;
        long generationId = activeGenerationId();
        boolean cancelledGesture = gestureAccepted;
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction != null) {
            interaction.cancelGesture();
        }
        deckCoordinator.cancelSettlement();
        turnQueue.clear();
        gestureAccepted = false;
        gestureMoved = false;
        gestureBeginPending = false;
        pendingMoved = false;
        recycleVelocityTracker();
        requestRender();
        notifySettlementCancelled(cancelledSettlement);
        if (cancelledSettlement == null && cancelledGesture) {
            notifyGestureCancelled(cancelledGestureId, generationId);
        }
        activeGestureId = NO_GESTURE_ID;
    }

    /**
     * Starts the same reference settlement used by a completed edge drag.
     *
     * <p>Returns false when the prepared deck cannot accept a turn or the requested direction is
     * outside the current deck boundary. Local change: while another settlement is running the turn
     * is queued (returns true) and chained right after it, with a slightly shorter duration so quick
     * repeated taps keep up. A turn issued while a revert is running continues from the page's current
     * position.
     */
    public boolean turn(PageChange pageChange) {
        return turn(pageChange, NO_GESTURE_ID);
    }

    public boolean turn(PageChange pageChange, long gestureId) {
        requireMainThread();
        if (pageChange != PageChange.PREVIOUS && pageChange != PageChange.NEXT) {
            return false;
        }
        if (!surfaceUsable() || deckCoordinator.getActiveDeck() == null) {
            rejectGesture(gestureId);
            return false;
        }
        if (gestureAccepted) {
            // A single-finger edge tap cannot coexist with a drag, so a gesture still marked active here is
            // stale (its UP / CANCEL never arrived). Drop it instead of letting it block every turn.
            abandonGesture();
        }
        if (!deckPrepared()) {
            return enqueueTurn(pageChange, gestureId);   // deck still uploading: run as soon as it is ready
        }
        if (settlementRunning) {
            if (activeSettlement != null
                    && activeSettlement.getPageChange() == PageChange.NONE
                    && matchesDisplacedPage(pageChange)
                    && canTurn(pageChange)) {
                // interrupted revert: take over from the current pose, no reset
                supersedeSettlement();
                return startTurn(pageChange, gestureId);
            }
            return enqueueTurn(pageChange, gestureId);
        }
        if (activationPending) {
            return enqueueTurn(pageChange, gestureId);
        }
        if (!canTurn(pageChange)) {
            return false;
        }
        return startTurn(pageChange, gestureId);
    }

    /** True while at least one turn is waiting behind a running settlement. */
    public boolean hasQueuedTurns() {
        pruneTurnQueue();
        return !turnQueue.isEmpty();
    }

    /** True while a finger is down but its drag waits for the previous settlement to hand over the page. */
    public boolean isGestureDeferred() {
        return gestureBeginPending;
    }

    /** Explicitly activates a prepared replacement after an idle generation change. */
    public void activatePendingDeck() {
        requireMainThread();
        PageDeckCoordinator.Promotion<Bitmap> promotion =
                deckCoordinator.activatePending();
        PageDeck<Bitmap> activated = promotion.getActivatedDeck();
        if (activated == null) {
            return;
        }
        markPromotionRelease(promotion);
        queueEvent(() -> renderer.activateDeck(activated.getGenerationId()));
        requestRender();
    }

    /** Releases a retained active or replacement deck and its GL textures. */
    public void releaseDeck(long generationId) {
        requireMainThread();
        preparedGenerations.remove(generationId);
        PageDeckCoordinator.Release<Bitmap> release =
                deckCoordinator.release(generationId);
        queueDeckRelease(release);
        requestRender();
    }

    /** Idempotently releases renderer, gesture, and deck state. */
    public void dispose() {
        requireMainThread();
        if (disposed) {
            return;
        }
        disposed = true;
        SettlementContext cancelledSettlement = cancelSettlementAnimator();
        recycleVelocityTracker();
        turnQueue.clear();
        gestureAccepted = false;
        gestureBeginPending = false;
        activationPending = false;
        preparedGenerations.clear();
        deckCoordinator.dispose();
        if (attached) {
            attached = false;
            setPreserveEGLContextOnPause(false);
            queueEvent(renderer::dispose);
            requestRender();
            onPause();
        }
        renderer.abandonClientState();
        notifySettlementCancelled(cancelledSettlement);
        releaseAllOutstandingLeases();
    }

    /**
     * Installs the main-thread callback boundary for future decks and capability events.
     *
     * <p>An accepted generation remains bound to the listener that acquired its bitmap lease,
     * even if this listener is replaced before that generation is released.
     */
    public void setPageSurfaceListener(PageSurfaceListener listener) {
        requireMainThread();
        pageSurfaceListener = listener == null ? NO_OP_LISTENER : listener;
        if (renderCapabilities != null) {
            pageSurfaceListener.onCapabilitiesAvailable(renderCapabilities);
        }
    }

    public PageSurfaceListener getPageSurfaceListener() {
        return pageSurfaceListener;
    }

    public RenderCapabilities getRenderCapabilities() {
        return renderCapabilities;
    }

    public int getCurrentPosition() {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        if (deck instanceof PortraitPageDeck) {
            return ((PortraitPageDeck<Bitmap>) deck).getCurrent().getOrdinal();
        }
        if (deck instanceof LandscapePageDeck) {
            return ((LandscapePageDeck<Bitmap>) deck).getCurrentLeft().getOrdinal();
        }
        return -1;
    }

    public OnPageChangeListener getOnPageChangeListener() {
        return onPageChangeListener;
    }

    public void setOnPageChangeListener(OnPageChangeListener listener) {
        requireMainThread();
        onPageChangeListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return handlePageTouchEvent(event, NO_GESTURE_ID);
    }

    private boolean handlePageTouchEvent(MotionEvent event, long gestureId) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            return handleDown(event, gestureId);
        }
        if (!gestureAccepted) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                gestureAccepted = false;
                gestureMoved = false;
                gestureBeginPending = false;
                recycleVelocityTracker();
                activeGestureId = NO_GESTURE_ID;
            }
            return true;
        }
        if (gestureBeginPending) {
            return handlePendingGestureEvent(event, action);
        }
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) {
            abandonGesture();
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                obtainVelocityTracker().addMovement(event);
                float deltaX = event.getX() - gestureDownX;
                float deltaY = event.getY() - gestureDownY;
                if (!gestureMoved
                        && Math.hypot(deltaX, deltaY) >= touchSlop) {
                    gestureMoved = true;
                }
                if (gestureMoved) {
                    dragInteraction(event.getX());
                    requestRender();
                }
                return true;
            case MotionEvent.ACTION_UP:
                obtainVelocityTracker().addMovement(event);
                if (!gestureMoved) {
                    gestureAccepted = false;
                    recycleVelocityTracker();
                    if (interaction.isDisplaced()) {
                        // a page that was caught mid-motion and let go without moving: carry on from where it is
                        settle(interaction.release());
                        return true;
                    }
                    interaction.cancelGesture();
                    requestRender();
                    performClick();
                    notifyGestureCancelled(activeGestureId, activeGenerationId());
                    activeGestureId = NO_GESTURE_ID;
                    return true;
                }
                dragInteraction(event.getX());
                VelocityTracker tracker = velocityTracker;
                tracker.computeCurrentVelocity(1000);
                float velocityX = tracker.getXVelocity();
                recycleVelocityTracker();
                Settlement settlement;
                if (Math.abs(velocityX) >= FLING_THRESHOLD_PX_PER_SECOND) {
                    settlement = velocityX < 0f
                            ? interaction.flingTowardNext()
                            : interaction.flingTowardPrevious();
                } else {
                    settlement = interaction.release();
                }
                gestureAccepted = false;
                settle(settlement);
                return true;
            case MotionEvent.ACTION_CANCEL:
                gestureAccepted = false;
                recycleVelocityTracker();
                if (gestureMoved || interaction.isDisplaced()) {
                    // Local change: a cancelled drag goes home with the short revert animation instead of snapping.
                    gestureMoved = false;
                    settle(interaction.revert());
                    return true;
                }
                interaction.cancelGesture();
                gestureMoved = false;
                requestRender();
                notifyGestureCancelled(activeGestureId, activeGenerationId());
                activeGestureId = NO_GESTURE_ID;
                return true;
            default:
                return true;
        }
    }

    private boolean handleDown(MotionEvent event, long gestureId) {
        activeGestureId = gestureId;
        gestureMoved = false;
        gestureBeginPending = false;
        pendingMoved = false;
        recycleVelocityTracker();
        turnQueue.clear();   // the finger wins over taps that were still waiting
        if (!surfaceUsable() || deckCoordinator.getActiveDeck() == null) {
            gestureAccepted = false;
            rejectGesture(activeGestureId);
            activeGestureId = NO_GESTURE_ID;
            return true;
        }
        gestureAccepted = true;
        gestureDownX = event.getX();
        gestureDownY = event.getY();
        obtainVelocityTracker().addMovement(event);

        if (settlementRunning) {
            if (activeSettlement != null
                    && activeSettlement.getPageChange() == PageChange.NONE) {
                // page is only going home: catch it right where it is
                supersedeSettlement();
            } else {
                // page change is committing: finish it quickly, then start the drag where the finger is
                deferGesture(event);
                expediteSettlement();
                return true;
            }
        } else if (activationPending || !deckPrepared()) {
            // deck swap / upload in flight: the drag starts as soon as it is done (resumeDeferred)
            deferGesture(event);
            return true;
        }
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) {
            abandonGesture();
            return true;
        }
        interaction.grabGesture(gestureDownX);
        return true;
    }

    private void deferGesture(MotionEvent event) {
        gestureBeginPending = true;
        pendingMoved = false;
        pendingX = event.getX();
        pendingY = event.getY();
    }

    private boolean handlePendingGestureEvent(MotionEvent event, int action) {
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                obtainVelocityTracker().addMovement(event);
                pendingX = event.getX();
                pendingY = event.getY();
                if (!pendingMoved
                        && Math.hypot(pendingX - gestureDownX, pendingY - gestureDownY)
                                >= touchSlop) {
                    pendingMoved = true;
                }
                return true;
            case MotionEvent.ACTION_UP: {
                obtainVelocityTracker().addMovement(event);
                float dx = event.getX() - gestureDownX;
                velocityTracker.computeCurrentVelocity(1000);
                float velocityX = velocityTracker.getXVelocity();
                boolean intent = pendingMoved
                        && (Math.abs(dx) >= getWidth() * 0.22f
                                || Math.abs(velocityX) >= FLING_THRESHOLD_PX_PER_SECOND);
                float direction = Math.abs(velocityX) >= FLING_THRESHOLD_PX_PER_SECOND
                        ? velocityX
                        : dx;
                abandonGesture();
                if (intent) {
                    // the finger was lifted before the page could be handed over: keep the flick as a queued turn
                    enqueueTurn(
                            direction < 0f ? PageChange.NEXT : PageChange.PREVIOUS,
                            NO_GESTURE_ID);
                    resumeDeferred();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                abandonGesture();
                return true;
            default:
                return true;
        }
    }

    /** Starts a drag that was waiting for the previous settlement / deck swap. */
    private void beginPendingGesture() {
        gestureBeginPending = false;
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) {
            abandonGesture();
            return;
        }
        gestureDownX = pendingX;
        gestureDownY = pendingY;
        interaction.grabGesture(pendingX);
        gestureMoved = pendingMoved;
        if (gestureMoved) {
            dragInteraction(pendingX);
        }
        requestRender();
    }

    /** Drops the current gesture without touching the model and tells its owner. */
    private void abandonGesture() {
        long gestureId = activeGestureId;
        gestureAccepted = false;
        gestureMoved = false;
        gestureBeginPending = false;
        pendingMoved = false;
        recycleVelocityTracker();
        activeGestureId = NO_GESTURE_ID;
        notifyGestureCancelled(gestureId, activeGenerationId());
    }

    public boolean onPageTouchEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    public boolean onPageTouchEvent(MotionEvent event, long gestureId) {
        return handlePageTouchEvent(event, gestureId);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        detach();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) {
            setViewport(width, height);
        }
    }

    private void handleDeckPrepared(long generationId) {
        if (disposed) {
            return;
        }
        PageSurfaceListener owner = leaseRegistry.ownerFor(generationId);
        if (owner == null) {
            return;
        }
        if (!preparedGenerations.add(generationId)) {
            return;
        }
        trimPreparedGenerations();
        owner.onDeckPrepared(generationId);
        requestRender();
        resumeDeferred();
    }

    private void handleRenderFailure(RenderFailure failure) {
        if (disposed) {
            return;
        }
        long generationId = failure.getGenerationId();
        PageSurfaceListener owner = generationId < 0
                ? pageSurfaceListener
                : leaseRegistry.ownerFor(generationId);
        if (owner != null) {
            owner.onRenderFailure(failure);
        }
    }

    private void handleCapabilitiesAvailable(RenderCapabilities capabilities) {
        if (disposed) {
            return;
        }
        renderCapabilities = capabilities;
        pageSurfaceListener.onCapabilitiesAvailable(capabilities);
    }

    private void handleDeckReleased(
            long generationId,
            DeckReleaseReason reason) {
        preparedGenerations.remove(generationId);
        deckCoordinator.release(generationId);
        DeckLeaseRegistry.Lease lease = leaseRegistry.release(generationId);
        if (lease != null) {
            DeckReleaseReason effectiveReason =
                    lease.getReleaseReason() == null ? reason : lease.getReleaseReason();
            lease.getListener().onDeckReleased(generationId, effectiveReason);
        }
    }

    private void trimPreparedGenerations() {
        while (preparedGenerations.size() > 2) {
            Iterator<Long> iterator = preparedGenerations.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    // ------------------------------------------------------------------ readiness

    private boolean surfaceUsable() {
        return !disposed && attached && surfaceVisible;
    }

    private boolean deckPrepared() {
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        return active != null && preparedGenerations.contains(active.getGenerationId());
    }

    /** Nothing in flight at all: a turn can start right now. */
    private boolean idleAndReady() {
        return surfaceUsable()
                && deckPrepared()
                && !settlementRunning
                && !activationPending
                && !gestureAccepted;
    }

    private void rejectGesture(long gestureId) {
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        long generationId = active == null ? -1L : active.getGenerationId();
        GestureRejectionReason reason;
        if (disposed) {
            reason = GestureRejectionReason.DISPOSED;
        } else if (!attached) {
            reason = GestureRejectionReason.SESSION_DETACHED;
        } else if (!surfaceVisible) {
            reason = GestureRejectionReason.NOT_VISIBLE;
        } else if (settlementRunning) {
            reason = GestureRejectionReason.SETTLEMENT_RUNNING;
        } else {
            reason = GestureRejectionReason.DECK_NOT_PREPARED;
        }
        listenerFor(generationId).onGestureRejected(gestureId, generationId, reason);
    }

    /**
     * Called whenever something that blocked work has cleared (settlement done, deck swapped in,
     * deck prepared): starts a deferred drag, or else the next queued turn.
     */
    private void resumeDeferred() {
        if (disposed || !attached || settlementRunning || activationPending) {
            return;
        }
        if (gestureBeginPending) {
            if (surfaceUsable() && deckPrepared()) {
                beginPendingGesture();
            }
            return;
        }
        drainTurnQueue();
    }

    // ------------------------------------------------------------------ turns

    private static final class QueuedTurn {
        final PageChange change;
        final long gestureId;
        final long enqueuedAt;

        QueuedTurn(PageChange change, long gestureId) {
            this.change = change;
            this.gestureId = gestureId;
            this.enqueuedAt = SystemClock.uptimeMillis();
        }
    }

    private boolean enqueueTurn(PageChange change, long gestureId) {
        pruneTurnQueue();
        if (turnQueue.size() >= MAX_QUEUED_TURNS) {
            return false;
        }
        turnQueue.addLast(new QueuedTurn(change, gestureId));
        return true;
    }

    private void pruneTurnQueue() {
        long now = SystemClock.uptimeMillis();
        while (!turnQueue.isEmpty()
                && now - turnQueue.peekFirst().enqueuedAt > QUEUED_TURN_TTL_MILLIS) {
            turnQueue.pollFirst();
        }
    }

    private void drainTurnQueue() {
        while (true) {
            pruneTurnQueue();
            if (turnQueue.isEmpty() || !idleAndReady()) {
                return;
            }
            QueuedTurn next = turnQueue.pollFirst();
            if (!canTurn(next.change)) {
                continue;   // ran into the end of the book: nothing to turn to
            }
            if (startTurn(next.change, next.gestureId)) {
                return;
            }
        }
    }

    private boolean startTurn(PageChange change, long gestureId) {
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) {
            return false;
        }
        Settlement settlement = interaction.turn(change);
        if (settlement.getPageChange() == PageChange.NONE) {
            interaction.cancelGesture();
            requestRender();
            return false;
        }
        int waiting = turnQueue.size();
        if (waiting > 0) {
            // taps are piling up: run each flip a little faster so the book keeps up with the finger
            float factor = waiting == 1 ? 0.75f : 0.6f;
            settlement = settlement.withDuration(Math.max(
                    MIN_CHAINED_TURN_MILLIS,
                    Math.round(settlement.getDurationMillis() * factor)));
        }
        activeGestureId = gestureId;
        settle(settlement);
        return true;
    }

    private boolean matchesDisplacedPage(PageChange change) {
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) {
            return false;
        }
        ActivePage active = interaction.getActivePage();
        return change == PageChange.PREVIOUS
                ? active == ActivePage.LEFT
                : active == ActivePage.CURRENT;
    }

    /**
     * Deck boundary: the client pads the ends of the book by repeating the edge page, so a neighbour
     * with the same ordinal as the current page means there is nothing to turn to.
     */
    private boolean canTurn(PageChange change) {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        if (deck instanceof PortraitPageDeck) {
            PortraitPageDeck<Bitmap> portrait = (PortraitPageDeck<Bitmap>) deck;
            int current = portrait.getCurrent().getOrdinal();
            int neighbour = change == PageChange.PREVIOUS
                    ? portrait.getPrevious().getOrdinal()
                    : portrait.getNext().getOrdinal();
            return neighbour != current;
        }
        if (deck instanceof LandscapePageDeck) {
            LandscapePageDeck<Bitmap> spread = (LandscapePageDeck<Bitmap>) deck;
            int current = spread.getCurrentLeft().getOrdinal();
            int neighbour = change == PageChange.PREVIOUS
                    ? spread.getPreviousLeft().getOrdinal()
                    : spread.getNextLeft().getOrdinal();
            return neighbour != current;
        }
        return false;
    }

    // ------------------------------------------------------------------ settlement

    private void dragInteraction(float x) {
        float gestureWidth = activeDeckIsLandscape()
                ? Math.max(1f, getWidth() / 2f)
                : Math.max(1f, getWidth());
        if (landscapeModelOrNull() != null) {
            landscapeModelOrNull().dragTo(x, gestureWidth);
        } else {
            interactionModelOrNull().dragTo(x, gestureWidth);
        }
    }

    private void settle(Settlement settlement) {
        if (settlementRunning) {
            supersedeSettlement();   // never silently ignore: that is how states used to get stuck
        }
        SettlementContext context = settlementContext(settlement.getPageChange());
        activeGestureId = NO_GESTURE_ID;
        activeSettlementContext = context;
        activeSettlement = settlement;
        settlementExpedited = false;
        deckCoordinator.beginSettlement();
        settlementRunning = true;
        listenerFor(context.generationId).onSettlementStarted(
                context.gestureId,
                context.generationId,
                context.sourceLogicalPageId,
                context.targetLogicalPageId,
                settlement.getPageChange());

        startSettlementAnimator(
                settlement, context, currentPagePercent(settlement.getTargetPercent()));
    }

    private void startSettlementAnimator(
            Settlement settlement, SettlementContext context, float startPercent) {
        if (Math.abs(startPercent - settlement.getTargetPercent()) < 0.01f) {
            completeSettlement(settlement, context);
            return;
        }
        ValueAnimator animator =
                ValueAnimator.ofFloat(startPercent, settlement.getTargetPercent());
        animator.setDuration(Math.max(1L, settlement.getDurationMillis()));
        animator.setInterpolator(toAndroidInterpolator(settlement.getInterpolator()));
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            LandscapeSpreadModel spread = landscapeModelOrNull();
            if (spread != null) {
                spread.updateSettlement(value);
            } else {
                PlayLikeCurlModel model = interactionModelOrNull();
                if (model != null) {
                    model.updateSettlement(value);
                }
            }
            requestRender();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // identity check: a replaced animator must never complete the settlement of its successor
                if (!cancelled && settlementAnimator == animation) {
                    completeSettlement(settlement, context);
                }
            }
        });
        settlementAnimator = animator;
        animator.start();
    }

    /** Finishes a committing settlement quickly (from where it is) so a waiting drag can take over. */
    private void expediteSettlement() {
        if (!settlementRunning
                || settlementExpedited
                || activeSettlement == null
                || activeSettlementContext == null) {
            return;
        }
        Settlement running = activeSettlement;
        SettlementContext context = activeSettlementContext;
        float from = currentPagePercent(running.getTargetPercent());
        stopSettlementAnimator();
        settlementExpedited = true;
        Settlement quick = running.withDuration(
                Math.min(EXPEDITE_DURATION_MILLIS, running.getDurationMillis()));
        activeSettlement = quick;
        startSettlementAnimator(quick, context, from);
    }

    private void completeSettlement(Settlement settlement, SettlementContext context) {
        LandscapeSpreadModel spread = landscapeModelOrNull();
        PlayLikeCurlModel portraitModel = spread != null ? null : interactionModelOrNull();
        settlementRunning = false;
        settlementAnimator = null;
        activeSettlementContext = null;
        activeSettlement = null;
        settlementExpedited = false;
        gestureMoved = false;

        PageDeckCoordinator.Promotion<Bitmap> promotion =
                deckCoordinator.completeSettlement();
        PageDeck<Bitmap> promoted = promotion.getActivatedDeck();
        if (promoted == null) {
            // Local change: when a replacement deck is promoted, the old model is discarded by
            // applyActiveDeck, so it must NOT be reset to the rest pose here. Resetting it first made
            // the GL thread occasionally draw one frame of the old page at rest before activation.
            if (spread != null) {
                spread.completeSettlement(settlement);
            } else if (portraitModel != null) {
                portraitModel.completeSettlement(settlement);
            }
        }
        if (promoted != null) {
            markPromotionRelease(promotion);
            // Local change: until the GL thread has swapped the new deck (and its fresh model) in, the model
            // reachable from here is the OLD one. Gestures and turns wait for the swap instead of driving it.
            beginActivationWait();
            queueEvent(() -> renderer.activateDeck(promoted.getGenerationId()));
            endActivationWhenSwapped();
        } else if (settlement.getPageChange() != PageChange.NONE) {
            preparedGenerations.remove(context.generationId);
        }
        requestRender();

        listenerFor(context.generationId).onSettlementCompleted(
                context.gestureId,
                context.generationId,
                context.targetLogicalPageId,
                context.targetOrdinal,
                settlement.getPageChange());
        if (settlement.getPageChange() != PageChange.NONE
                && onPageChangeListener != null) {
            onPageChangeListener.onPageChanged(context.targetOrdinal);
        }
        resumeDeferred();
    }

    private void beginActivationWait() {
        activationPending = true;
        activationToken++;
    }

    private void endActivationWhenSwapped() {
        final int token = activationToken;
        // runs on the GL thread right after activateDeck, then hops back to the main thread
        queueEvent(() -> post(() -> clearActivation(token)));
        postDelayed(() -> clearActivation(token), ACTIVATION_FALLBACK_MILLIS);
    }

    private void clearActivation(int token) {
        if (token != activationToken || !activationPending) {
            return;
        }
        activationPending = false;
        if (!disposed) {
            resumeDeferred();
        }
    }

    /** Stops the animator only. Flags and callbacks are the caller's business. */
    private void stopSettlementAnimator() {
        if (settlementAnimator != null) {
            ValueAnimator animator = settlementAnimator;
            settlementAnimator = null;
            animator.removeAllListeners();
            animator.removeAllUpdateListeners();
            animator.cancel();
        }
    }

    private SettlementContext cancelSettlementAnimator() {
        SettlementContext cancelledContext =
                settlementRunning ? activeSettlementContext : null;
        stopSettlementAnimator();
        settlementRunning = false;
        settlementExpedited = false;
        activeSettlementContext = null;
        activeSettlement = null;
        return cancelledContext;
    }

    /**
     * Ends the running settlement WITHOUT touching the model (the page stays exactly where it is) and
     * reports it cancelled. Used when a new touch or turn takes the page over.
     */
    private void supersedeSettlement() {
        SettlementContext cancelled = cancelSettlementAnimator();
        deckCoordinator.cancelSettlement();
        notifySettlementCancelled(cancelled);
    }

    private void notifySettlementCancelled(SettlementContext context) {
        if (context != null) {
            listenerFor(context.generationId).onSettlementCancelled(
                    context.gestureId,
                    context.generationId,
                    context.sourceLogicalPageId);
        }
    }

    private PageSurfaceListener listenerFor(long generationId) {
        return leaseRegistry.listenerFor(generationId, pageSurfaceListener);
    }

    private long activeGenerationId() {
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        return active == null ? -1L : active.getGenerationId();
    }

    private void notifyGestureCancelled(long gestureId, long generationId) {
        if (gestureId != NO_GESTURE_ID) {
            listenerFor(generationId).onGestureCancelled(gestureId, generationId);
        }
    }

    private void releaseAllOutstandingLeases() {
        for (DeckLeaseRegistry.Lease lease :
                leaseRegistry.releaseAll(DeckReleaseReason.DISPOSED)) {
            lease.getListener().onDeckReleased(
                    lease.getGenerationId(),
                    lease.getReleaseReason());
        }
    }

    private void queueDeckRelease(PageDeckCoordinator.Release<Bitmap> release) {
        if (release == null) {
            return;
        }
        long generationId = release.getDeck().getGenerationId();
        preparedGenerations.remove(generationId);
        leaseRegistry.markReleaseRequested(generationId, release.getReason());
        queueEvent(() -> renderer.releaseDeck(generationId, release.getReason()));
    }

    private void markPromotionRelease(
            PageDeckCoordinator.Promotion<Bitmap> promotion) {
        PageDeckCoordinator.Release<Bitmap> release = promotion.getRelease();
        if (release != null) {
            leaseRegistry.markReleaseRequested(
                    release.getDeck().getGenerationId(),
                    release.getReason());
        }
    }

    /** Current position of the page in motion, in settlement percent; [fallback] when no model is reachable. */
    private float currentPagePercent(float fallback) {
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) {
            return fallback;
        }
        PageState activeState;
        if (interaction.getActivePage() == ActivePage.LEFT) {
            activeState = interaction.getLeftPage();
        } else if (interaction.getActivePage() == ActivePage.RIGHT) {
            activeState = interaction.getRightPage();
        } else {
            activeState = interaction.getFrontPage();
        }
        return activeState.getCurlPosition() / PlayLikeCurlModel.GRID * 100f;
    }

    private SettlementContext settlementContext(PageChange pageChange) {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        if (deck instanceof PortraitPageDeck) {
            PortraitPageDeck<Bitmap> portrait = (PortraitPageDeck<Bitmap>) deck;
            PageImage<Bitmap> source = portrait.getCurrent();
            PageImage<Bitmap> target = pageChange == PageChange.PREVIOUS
                    ? portrait.getPrevious()
                    : pageChange == PageChange.NEXT
                            ? portrait.getNext()
                            : source;
            return SettlementContext.from(activeGestureId, source, target);
        }
        LandscapePageDeck<Bitmap> spread = (LandscapePageDeck<Bitmap>) deck;
        PageImage<Bitmap> source = spread.getCurrentLeft();
        PageImage<Bitmap> target = pageChange == PageChange.PREVIOUS
                ? spread.getPreviousLeft()
                : pageChange == PageChange.NEXT
                        ? spread.getNextLeft()
                        : source;
        return SettlementContext.from(activeGestureId, source, target);
    }

    private boolean activeDeckIsLandscape() {
        return deckCoordinator.getActiveDeck() instanceof LandscapePageDeck;
    }

    private PlayLikeCurlModel interactionModelOrNull() {
        LandscapeSpreadModel spread = landscapeModelOrNull();
        if (spread != null) {
            return spread.getMotionModel();
        }
        return rendererPortraitModel();
    }

    private PlayLikeCurlModel rendererPortraitModel() {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        if (!(deck instanceof PortraitPageDeck)) {
            return null;
        }
        return renderer.getPortraitModel();
    }

    private LandscapeSpreadModel landscapeModelOrNull() {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        if (!(deck instanceof LandscapePageDeck)) {
            return null;
        }
        return renderer.getLandscapeSpreadModel();
    }

    private VelocityTracker obtainVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        return velocityTracker;
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "PageSurfaceView mutations must run on the main thread");
        }
    }

    private static Interpolator toAndroidInterpolator(
            SettlementInterpolator interpolator) {
        switch (interpolator) {
            case DECELERATE:
                return new DecelerateInterpolator();
            case FAST_OUT_SLOW_IN:
                return new PathInterpolator(0.4f, 0f, 0.2f, 1f);
            case ACCELERATE_DECELERATE:
            default:
                return new AccelerateDecelerateInterpolator();
        }
    }

    public interface OnPageChangeListener {
        void onPageChanged(int position);
    }

    private static final class SettlementContext {
        private final long gestureId;
        private final long generationId;
        private final String sourceLogicalPageId;
        private final String targetLogicalPageId;
        private final int targetOrdinal;

        private SettlementContext(
                long gestureId,
                long generationId,
                String sourceLogicalPageId,
                String targetLogicalPageId,
                int targetOrdinal) {
            this.gestureId = gestureId;
            this.generationId = generationId;
            this.sourceLogicalPageId = sourceLogicalPageId;
            this.targetLogicalPageId = targetLogicalPageId;
            this.targetOrdinal = targetOrdinal;
        }

        static SettlementContext from(
                long gestureId,
                PageImage<Bitmap> source,
                PageImage<Bitmap> target) {
            return new SettlementContext(
                    gestureId,
                    source.getGenerationId(),
                    source.getLogicalPageId(),
                    target.getLogicalPageId(),
                    target.getOrdinal());
        }
    }
}
