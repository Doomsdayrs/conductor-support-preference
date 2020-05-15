/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package androidx.preference

import android.R
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.RestrictTo
import androidx.appcompat.app.AlertDialog
import androidx.preference.DialogPreference.TargetFragment
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.SimpleSwapChangeHandler

/**
 * Abstract base class which presents a dialog associated with a
 * [androidx.preference.DialogPreference]. Since the preference object may
 * not be available during fragment re-creation, the necessary information for displaying the dialog
 * is read once during the initial call to [.onCreate] and saved/restored in the saved
 * instance state. Custom subclasses should also follow this pattern.
 */
abstract class PreferenceDialogController : Controller(), DialogInterface.OnClickListener {
	private var mPreference: DialogPreference? = null
	private var mDialogTitle: CharSequence? = null
	private var mPositiveButtonText: CharSequence? = null
	private var mNegativeButtonText: CharSequence? = null
	private var mDialogMessage: CharSequence? = null

	@LayoutRes
	private var mDialogLayoutRes = 0
	private var mDialogIcon: BitmapDrawable? = null

	/** Which button was clicked.  */
	private var mWhichButtonClicked = 0
	protected var dialog: Dialog? = null
		private set
	private var dismissed = false
	override fun onCreateView(inflater: LayoutInflater,
	                          container: ViewGroup,
	                          savedViewState: Bundle?): View {
		onCreate(savedViewState)
		dialog = onCreateDialog(savedViewState)
		dialog!!.setOwnerActivity(activity!!)
		dialog!!.setOnDismissListener { dismissDialog() }
		if (savedViewState != null) {
			val dialogState = savedViewState.getBundle(SAVE_DIALOG_STATE_TAG)
			if (dialogState != null) {
				dialog!!.onRestoreInstanceState(dialogState)
			}
		}
		return View(activity) //stub view
	}

	open fun onCreate(savedInstanceState: Bundle?) {
		val rawController = targetController
		check(rawController is TargetFragment) {
			"Target controller must implement TargetFragment" +
					" interface"
		}
		val controller = rawController as TargetFragment
		val key = args.getString(ARG_KEY)
		if (savedInstanceState == null) {
			mPreference = controller.findPreference<Preference>(key!!) as DialogPreference?
			mDialogTitle = mPreference!!.dialogTitle
			mPositiveButtonText = mPreference!!.positiveButtonText
			mNegativeButtonText = mPreference!!.negativeButtonText
			mDialogMessage = mPreference!!.dialogMessage
			mDialogLayoutRes = mPreference!!.dialogLayoutResource
			val icon = mPreference!!.dialogIcon
			mDialogIcon = if (icon == null || icon is BitmapDrawable) {
				icon as BitmapDrawable
			} else {
				val bitmap = Bitmap.createBitmap(icon.intrinsicWidth,
						icon.intrinsicHeight, Bitmap.Config.ARGB_8888)
				val canvas = Canvas(bitmap)
				icon.setBounds(0, 0, canvas.width, canvas.height)
				icon.draw(canvas)
				BitmapDrawable(resources, bitmap)
			}
		}
	}

	public override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putCharSequence(SAVE_STATE_TITLE, mDialogTitle)
		outState.putCharSequence(SAVE_STATE_POSITIVE_TEXT, mPositiveButtonText)
		outState.putCharSequence(SAVE_STATE_NEGATIVE_TEXT, mNegativeButtonText)
		outState.putCharSequence(SAVE_STATE_MESSAGE, mDialogMessage)
		outState.putInt(SAVE_STATE_LAYOUT, mDialogLayoutRes)
		if (mDialogIcon != null) {
			outState.putParcelable(SAVE_STATE_ICON, mDialogIcon!!.bitmap)
		}
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		super.onRestoreInstanceState(savedInstanceState)
		mDialogTitle = savedInstanceState.getCharSequence(SAVE_STATE_TITLE)
		mPositiveButtonText = savedInstanceState.getCharSequence(SAVE_STATE_POSITIVE_TEXT)
		mNegativeButtonText = savedInstanceState.getCharSequence(SAVE_STATE_NEGATIVE_TEXT)
		mDialogMessage = savedInstanceState.getCharSequence(SAVE_STATE_MESSAGE)
		mDialogLayoutRes = savedInstanceState.getInt(SAVE_STATE_LAYOUT, 0)
		val bitmap = savedInstanceState.getParcelable<Bitmap>(SAVE_STATE_ICON)
		if (bitmap != null) {
			mDialogIcon = BitmapDrawable(resources, bitmap)
		}
	}

	fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val context: Context? = activity
		mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE
		val builder = AlertDialog.Builder(context!!)
				.setTitle(mDialogTitle)
				.setIcon(mDialogIcon)
				.setPositiveButton(mPositiveButtonText, this)
				.setNegativeButton(mNegativeButtonText, this)
		val contentView = onCreateDialogView(context)
		if (contentView != null) {
			onBindDialogView(contentView)
			builder.setView(contentView)
		} else {
			builder.setMessage(mDialogMessage)
		}
		onPrepareDialogBuilder(builder)

		// Create the dialog
		val dialog: Dialog = builder.create()
		if (needInputMethod()) {
			requestInputMethod(dialog)
		}
		return dialog
	}

	override fun onSaveViewState(view: View, outState: Bundle) {
		super.onSaveViewState(view, outState)
		val dialogState = dialog!!.onSaveInstanceState()
		outState.putBundle(SAVE_DIALOG_STATE_TAG, dialogState)
	}

	override fun onAttach(view: View) {
		super.onAttach(view)
		dialog!!.show()
	}

	override fun onDetach(view: View) {
		super.onDetach(view)
		dialog!!.hide()
	}

	override fun onDestroyView(view: View) {
		super.onDestroyView(view)
		dialog!!.setOnDismissListener(null)
		dialog!!.dismiss()
		dialog = null
		mPreference = null
	}
	/**
	 * Display the dialog, create a transaction and pushing the controller.
	 *
	 * @param router The router on which the transaction will be applied
	 * @param tag    The tag for this controller
	 */
	/**
	 * Display the dialog, create a transaction and pushing the controller.
	 *
	 * @param router The router on which the transaction will be applied
	 */
	@JvmOverloads
	fun showDialog(router: Router, tag: String? = null) {
		dismissed = false
		router.pushController(RouterTransaction.with(this)
				.pushChangeHandler(SimpleSwapChangeHandler(false))
				.popChangeHandler(SimpleSwapChangeHandler(false))
				.tag(tag))
	}

	/**
	 * Dismiss the dialog and pop this controller
	 */
	fun dismissDialog() {
		if (dismissed) {
			return
		}
		onDialogClosed(mWhichButtonClicked == DialogInterface.BUTTON_POSITIVE)
		router.popController(this)
		dismissed = true
	}

	/**
	 * Get the preference that requested this dialog. Available after [.onCreate] has
	 * been called on the [PreferenceFragmentCompat] which launched this dialog.
	 *
	 * @return The [DialogPreference] associated with this
	 * dialog.
	 */
	val preference: DialogPreference?
		get() {
			if (mPreference == null) {
				val key = args.getString(ARG_KEY)
				val controller = targetController as TargetFragment?
				mPreference = controller!!.findPreference<Preference>(key!!) as DialogPreference?
			}
			return mPreference
		}

	/**
	 * Prepares the dialog builder to be shown when the preference is clicked.
	 * Use this to set custom properties on the dialog.
	 *
	 *
	 * Do not [AlertDialog.Builder.create] or
	 * [AlertDialog.Builder.show].
	 */
	protected open fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {}

	/**
	 * Returns whether the preference needs to display a soft input method when the dialog
	 * is displayed. Default is false. Subclasses should override this method if they need
	 * the soft input method brought up automatically.
	 *
	 * @hide
	 */
	@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
	protected open fun needInputMethod(): Boolean {
		return false
	}

	/**
	 * Sets the required flags on the dialog window to enable input method window to show up.
	 */
	private fun requestInputMethod(dialog: Dialog) {
		val window = dialog.window
		window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
	}

	/**
	 * Creates the content view for the dialog (if a custom content view is
	 * required). By default, it inflates the dialog layout resource if it is
	 * set.
	 *
	 * @return The content View for the dialog.
	 * @see DialogPreference.setLayoutResource
	 */
	protected fun onCreateDialogView(context: Context?): View? {
		val resId = mDialogLayoutRes
		if (resId == 0) {
			return null
		}
		val inflater = LayoutInflater.from(context)
		return inflater.inflate(resId, null)
	}

	/**
	 * Binds views in the content View of the dialog to data.
	 *
	 *
	 * Make sure to call through to the superclass implementation.
	 *
	 * @param view The content View of the dialog, if it is custom.
	 */
	protected open fun onBindDialogView(view: View) {
		val dialogMessageView = view.findViewById<View>(R.id.message)
		if (dialogMessageView != null) {
			val message = mDialogMessage
			var newVisibility = View.GONE
			if (!TextUtils.isEmpty(message)) {
				if (dialogMessageView is TextView) {
					dialogMessageView.text = message
				}
				newVisibility = View.VISIBLE
			}
			if (dialogMessageView.visibility != newVisibility) {
				dialogMessageView.visibility = newVisibility
			}
		}
	}

	override fun onClick(dialog: DialogInterface, which: Int) {
		mWhichButtonClicked = which
	}

	abstract fun onDialogClosed(positiveResult: Boolean)

	companion object {
		const val ARG_KEY = "key"
		private const val SAVE_DIALOG_STATE_TAG = "android:savedDialogState"
		private const val SAVE_STATE_TITLE = "PreferenceDialogController.title"
		private const val SAVE_STATE_POSITIVE_TEXT = "PreferenceDialogController.positiveText"
		private const val SAVE_STATE_NEGATIVE_TEXT = "PreferenceDialogController.negativeText"
		private const val SAVE_STATE_MESSAGE = "PreferenceDialogController.message"
		private const val SAVE_STATE_LAYOUT = "PreferenceDialogController.layout"
		private const val SAVE_STATE_ICON = "PreferenceDialogController.icon"
	}
}