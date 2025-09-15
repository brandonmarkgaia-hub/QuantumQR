package com.quantumqr.util


import com.quantumqr.R

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ReviewHelper {

    private const val PREFS = "quantumqr_prefs"
    private const val KEY_LAUNCH_COUNT = "launch_count"
    private const val KEY_LAST_PROMPT_VERSION = "last_prompt_version"
    private const val MIN_LAUNCHES = 4

    fun onAppLaunched(ctx: Context, versionCode: Int) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastVersion = p.getInt(KEY_LAST_PROMPT_VERSION, -1)
        if (lastVersion != versionCode) {
            // Reset counter on new version to let us ask again in future
            p.edit().putInt(KEY_LAST_PROMPT_VERSION, versionCode).apply()
            p.edit().putInt(KEY_LAUNCH_COUNT, 0).apply()
        }
        val c = p.getInt(KEY_LAUNCH_COUNT, 0) + 1
        p.edit().putInt(KEY_LAUNCH_COUNT, c).apply()
    }

    fun shouldAskNow(ctx: Context): Boolean {
        val c = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LAUNCH_COUNT, 0)
        return c >= MIN_LAUNCHES
    }

    /**
     * Shows a small heartfelt dialog, then tries GoogleÃ¢â‚¬â„¢s in-app review.
     * Call this after a "happy moment" (e.g. successful scan or QR generated),
     * but only if shouldAskNow(activity) is true.
     */
    fun maybeAskForReview(activity: Activity, appId: String = activity.packageName) {
        AlertDialog.Builder(activity)
            .setTitle("Help a local dev out? Ã¢Â­Â")
            .setMessage(
                "Hey! IÃ¢â‚¬â„¢m building QuantumQR as a one-man start-up. " +
                "Good ratings help other South Africans find the app and help me keep improving. " +
                "If you enjoy using it, could you drop me a 5Ã¢Ëœâ€¦ review? Premium also keeps the lights on Ã°Å¸â„¢Â"
            )
            .setPositiveButton("Sure, rate now") { d, _ ->
                d.dismiss()
                launchInAppReview(activity, appId)
            }
            .setNegativeButton("Maybe later") { d, _ -> d.dismiss() }
            .show()
    }

    private fun launchInAppReview(activity: Activity, appId: String) {
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    // Done or dismissed Ã¢â‚¬â€œ do nothing; Play decides if it shows again later
                }
            } else {
                // Fallback to Play Store page
                openPlayStore(activity, appId)
            }
        }
    }

    private fun openPlayStore(activity: Activity, appId: String) {
        val marketUri = Uri.parse("market://details?id=$appId")
        val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$appId")
        val intent = Intent(Intent.ACTION_VIEW, marketUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }
}