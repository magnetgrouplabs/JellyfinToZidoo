package com.jellyfintozidoo;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the poll loop gates that keep the one-time audio and subtitle selection off a
 * paused Zidoo player.
 *
 * A setAudio or setSubtitle call sent while the Realtek pipeline is paused re-primes it and
 * resumes playback on its own, which showed up as a pause that undid itself about eleven
 * seconds later. The selection therefore waits for two consecutive polls whose reported
 * position moved forward.
 */
public class TrackSelectionGateTest {

    @Test
    public void firstPollHasNoBaselineSoSelectionIsDeferred() {
        assertFalse(Play.shouldApplyTrackSelection(false, -1, 30515));
    }

    @Test
    public void pausedPlayerRepeatsThePositionSoSelectionIsDeferred() {
        assertFalse(Play.shouldApplyTrackSelection(false, 30515, 30515));
    }

    @Test
    public void positionGoingBackwardsDefersSelection() {
        assertFalse(Play.shouldApplyTrackSelection(false, 30515, 12000));
    }

    @Test
    public void advancingPositionAppliesSelection() {
        assertTrue(Play.shouldApplyTrackSelection(false, 30515, 40520));
    }

    @Test
    public void selectionIsAppliedOnlyOncePerEpisode() {
        assertFalse(Play.shouldApplyTrackSelection(true, 30515, 40520));
    }

    @Test
    public void zeroPositionNeverAppliesSelection() {
        assertFalse(Play.shouldApplyTrackSelection(false, -1, 0));
        assertFalse(Play.shouldApplyTrackSelection(false, 0, 0));
    }

    @Test
    public void episodeChangeResetIsHonoured() {
        // The reset paths set tracksSet = false and lastPollPositionMs = -1 together, so the new
        // episode has to earn its baseline again before the selection is sent.
        assertFalse(Play.shouldApplyTrackSelection(false, -1, 1000));
        assertTrue(Play.shouldApplyTrackSelection(false, 1000, 6000));
    }

    @Test
    public void pausedIsUnchangedPositionWithABaseline() {
        assertTrue(Play.isPlayerPaused(30515, 30515));
    }

    @Test
    public void advancingPositionIsNotPaused() {
        assertFalse(Play.isPlayerPaused(30515, 40520));
    }

    @Test
    public void noBaselineIsReportedAsNotPaused() {
        assertFalse(Play.isPlayerPaused(-1, 30515));
        assertFalse(Play.isPlayerPaused(-1, 0));
    }

    @Test
    public void seekBackwardsIsNotPaused() {
        assertFalse(Play.isPlayerPaused(30515, 12000));
    }
}
