package io.github.alirezajavan.permpilot

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Targets the pure grant-resolution functions in AndroidPermissionMapping.kt directly -- no
 * Context/Activity/Robolectric needed, since [canShowRationale] and `sdkInt` are passed in rather
 * than read from ActivityCompat/Build.VERSION. This is exactly the logic behind two hard-won bugs
 * in this library: the Denied-vs-PermanentlyDenied ambiguity Accompanist shipped and later fixed,
 * and the coarse-location-misreported-as-Denied bug found in this repo's own audit (PLAN.md §9.1).
 */
class AndroidPermissionResolutionTest {

    // --- resolveDeniedStateFrom ---------------------------------------------------------------

    @Test
    fun `first-ever denial with no rationale is Denied, not PermanentlyDenied`() {
        // The exact ambiguity Accompanist shipped a bug around: shouldShowRequestPermissionRationale
        // returns false both before the very first request AND after a permanent denial.
        val state = resolveDeniedStateFrom(canShowRationale = false, hadRequestedBefore = false)
        assertEquals(PermissionState.Denied(canRequestAgain = true), state)
    }

    @Test
    fun `denial after a previous request with no rationale is PermanentlyDenied`() {
        val state = resolveDeniedStateFrom(canShowRationale = false, hadRequestedBefore = true)
        assertEquals(PermissionState.PermanentlyDenied, state)
    }

    @Test
    fun `denial while rationale is showable is always Denied, regardless of request history`() {
        assertEquals(
            PermissionState.Denied(canRequestAgain = true),
            resolveDeniedStateFrom(canShowRationale = true, hadRequestedBefore = false)
        )
        assertEquals(
            PermissionState.Denied(canRequestAgain = true),
            resolveDeniedStateFrom(canShowRationale = true, hadRequestedBefore = true)
        )
    }

    // --- resolveGrantResult (generic grouped/single permission grant) ------------------------

    @Test
    fun `single permission granted resolves to Granted`() {
        val state = resolveGrantResult(
            manifestPermissions = listOf(Manifest.permission.CAMERA),
            results = mapOf(Manifest.permission.CAMERA to true),
            hadRequestedBefore = false,
            canShowRationale = false,
        )
        assertEquals(PermissionState.Granted, state)
    }

    @Test
    fun `grouped permission requires every member granted`() {
        val state = resolveGrantResult(
            manifestPermissions = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            results = mapOf(Manifest.permission.READ_CALENDAR to true, Manifest.permission.WRITE_CALENDAR to false),
            hadRequestedBefore = true,
            canShowRationale = false,
        )
        assertEquals(PermissionState.PermanentlyDenied, state)
    }

    // --- resolveForegroundLocationGrantResult (the coarse-only bugfix) -----------------------

    @Test
    fun `fine granted resolves to Granted regardless of coarse`() {
        val state = resolveForegroundLocationGrantResult(
            results = mapOf(
                Manifest.permission.ACCESS_FINE_LOCATION to true,
                Manifest.permission.ACCESS_COARSE_LOCATION to false,
            ),
            hadRequestedBefore = false,
            canShowRationale = false,
        )
        assertEquals(PermissionState.Granted, state)
    }

    @Test
    fun `coarse-only grant on the very first request is Limited, not Denied`() {
        // This is the exact bug found in the audit: the old all-or-nothing resolver reported
        // Denied(canRequestAgain = true) here even though the user granted working coarse access.
        val state = resolveForegroundLocationGrantResult(
            results = mapOf(
                Manifest.permission.ACCESS_FINE_LOCATION to false,
                Manifest.permission.ACCESS_COARSE_LOCATION to true,
            ),
            hadRequestedBefore = false,
            canShowRationale = false,
        )
        assertEquals(PermissionState.Limited(LimitedReason.ApproximateLocationOnly), state)
    }

    @Test
    fun `coarse-only grant stays Limited on every later check, never flips to Denied`() {
        // The bug was specifically that this misreported as Denied on every *subsequent* check,
        // not just the first -- verify the already-requested path is also unaffected.
        val state = resolveForegroundLocationGrantResult(
            results = mapOf(
                Manifest.permission.ACCESS_FINE_LOCATION to false,
                Manifest.permission.ACCESS_COARSE_LOCATION to true,
            ),
            hadRequestedBefore = true,
            canShowRationale = true,
        )
        assertEquals(PermissionState.Limited(LimitedReason.ApproximateLocationOnly), state)
    }

    @Test
    fun `neither fine nor coarse granted resolves through the normal denial path`() {
        val state = resolveForegroundLocationGrantResult(
            results = mapOf(
                Manifest.permission.ACCESS_FINE_LOCATION to false,
                Manifest.permission.ACCESS_COARSE_LOCATION to false,
            ),
            hadRequestedBefore = true,
            canShowRationale = false,
        )
        assertEquals(PermissionState.PermanentlyDenied, state)
    }

    // --- resolvePhotoLibraryGrantResult (API 34 / 33 / pre-33 tiers) -------------------------

    @Test
    fun `API 34 full media grant resolves to Granted`() {
        val state = resolvePhotoLibraryGrantResult(
            sdkInt = 34,
            results = mapOf(
                Manifest.permission.READ_MEDIA_IMAGES to true,
                Manifest.permission.READ_MEDIA_VIDEO to true,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to false,
            ),
            hadRequestedBefore = false,
            canShowRationale = false,
        )
        assertEquals(PermissionState.Granted, state)
    }

    @Test
    fun `API 34 partial selection grant resolves to Limited PartialMediaAccess`() {
        val state = resolvePhotoLibraryGrantResult(
            sdkInt = 34,
            results = mapOf(
                Manifest.permission.READ_MEDIA_IMAGES to false,
                Manifest.permission.READ_MEDIA_VIDEO to false,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to true,
            ),
            hadRequestedBefore = false,
            canShowRationale = false,
        )
        assertEquals(PermissionState.Limited(LimitedReason.PartialMediaAccess), state)
    }

    @Test
    fun `API 33 has no partial tier -- only full images plus video counts as Granted`() {
        val state = resolvePhotoLibraryGrantResult(
            sdkInt = 33,
            results = mapOf(
                Manifest.permission.READ_MEDIA_IMAGES to true,
                Manifest.permission.READ_MEDIA_VIDEO to false,
            ),
            hadRequestedBefore = true,
            canShowRationale = false,
        )
        assertEquals(PermissionState.PermanentlyDenied, state)
    }

    @Test
    fun `pre-33 falls back to READ_EXTERNAL_STORAGE`() {
        val state = resolvePhotoLibraryGrantResult(
            sdkInt = 29,
            results = mapOf(Manifest.permission.READ_EXTERNAL_STORAGE to true),
            hadRequestedBefore = false,
            canShowRationale = false,
        )
        assertEquals(PermissionState.Granted, state)
    }
}
