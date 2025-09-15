package com.quantumqr
import android.annotation.SuppressLint
import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory

object ReviewPrompter {
    fun requestInAppReview(activity: Activity, onDone: (() -> Unit)? = null) {
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    showThanks(activity)
                    onDone?.invoke()
                }
            } else {
                showThanks(activity)
                onDone?.invoke()
            }
        }
    }

    @SuppressLint("InflateParams")
    fun showThanks(activity: Activity) {
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_review_thanks, null)
        dialog.setContentView(view)
        view.findViewById<View>(R.id.btn_ok)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
