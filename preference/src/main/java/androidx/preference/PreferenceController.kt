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

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RestrictTo
import androidx.annotation.XmlRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.preference.DialogPreference.TargetFragment
import androidx.preference.PreferenceGroup.PreferencePositionCallback
import androidx.preference.PreferenceManager.OnDisplayPreferenceDialogListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.bluelinelabs.conductor.Controller

/**
 * Shows a hierarchy of [Preference] objects as
 * lists. These preferences will
 * automatically save to [android.content.SharedPreferences] as the user interacts with
 * them. To retrieve an instance of [android.content.SharedPreferences] that the
 * preference hierarchy in this fragment will use, call
 * [PreferenceManager.getDefaultSharedPreferences]
 * with a context in the same package as this fragment.
 *
 *
 * Furthermore, the preferences shown will follow the visual style of system
 * preferences. It is easy to create a hierarchy of preferences (that can be
 * shown on multiple screens) via XML. For these reasons, it is recommended to
 * use this fragment (as a superclass) to deal with preferences in applications.
 *
 *
 * A [PreferenceScreen] object should be at the top of the preference
 * hierarchy. Furthermore, subsequent [PreferenceScreen] in the hierarchy
 * denote a screen break--that is the preferences contained within subsequent
 * [PreferenceScreen] should be shown on another screen. The preference
 * framework handles this by calling [.onNavigateToScreen].
 *
 *
 * The preference hierarchy can be formed in multiple ways:
 *  *  From an XML file specifying the hierarchy
 *  *  From different [Activities][android.app.Activity] that each specify its own
 * preferences in an XML file via [android.app.Activity] meta-data
 *  *  From an object hierarchy rooted with [PreferenceScreen]
 *
 *
 * To inflate from XML, use the [.addPreferencesFromResource]. The
 * root element should be a [PreferenceScreen]. Subsequent elements can point
 * to actual [Preference] subclasses. As mentioned above, subsequent
 * [PreferenceScreen] in the hierarchy will result in the screen break.
 *
 *
 * To specify an object hierarchy rooted with [PreferenceScreen], use
 * [.setPreferenceScreen].
 *
 *
 * As a convenience, this fragment implements a click listener for any
 * preference in the current hierarchy, see
 * [.onPreferenceTreeClick].
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
</div> *
 * For information about using `PreferenceFragment`,
 * read the [Settings]({@docRoot}guide/topics/ui/settings.html)
 * guide.
 *
 *
 * <a name="SampleCode"></a>
 * <h3>Sample Code</h3>
 *
 *
 * The following sample code shows a simple preference fragment that is
 * populated from a resource.  The resource it loads is:
 *
 * {@sample frameworks/support/samples/SupportPreferenceDemos/src/main/res/xml/preferences.xml preferences}
 *
 *
 * The fragment implementation itself simply populates the preferences
 * when created.  Note that the preferences framework takes care of loading
 * the current values out of the app preferences and writing them when changed:
 *
 * {@sample frameworks/support/samples/SupportPreferenceDemos/src/main/java/com/example/android/supportpreference/FragmentSupportPreferencesCompat.java
 * *      support_fragment_compat}
 *
 * @see Preference
 *
 * @see PreferenceScreen
 */
@SuppressLint("RestrictedApi")
abstract class PreferenceController : Controller, OnDisplayPreferenceDialogListener, TargetFragment {
	companion object {
		/**
		 * Fragment argument used to specify the tag of the desired root
		 * [androidx.preference.PreferenceScreen] object.
		 */
		const val ARG_PREFERENCE_ROOT = "androidx.preference.PreferenceFragmentCompat.PREFERENCE_ROOT"
		private const val PREFERENCES_TAG = "android:preferences"
		private const val DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG"
		private const val MSG_BIND_PREFERENCES = 1
	}

	/**
	 * Returns the [PreferenceManager] used by this fragment.
	 * @return The [PreferenceManager].
	 */
	var preferenceManager: PreferenceManager? = null
		private set
	var listView: RecyclerView? = null
	private var mHavePrefs = false
	private var mInitDone = false
	private var mStyledContext: Context? = null

	@SuppressLint("PrivateResource")
	private var mLayoutResId = R.layout.preference_list_fragment
	private var mDividerDecoration: DividerDecoration? = null

	private val mHandler: Handler = @SuppressLint("HandlerLeak")
	object : Handler() {
		override fun handleMessage(msg: Message) {
			when (msg.what) {
				MSG_BIND_PREFERENCES -> bindPreferences()
			}
		}
	}

	private val mRequestFocus: Runnable = Runnable { listView!!.focusableViewAvailable(listView) }
	private var mSelectPreferenceRunnable: Runnable? = null

	/**
	 * Interface that PreferenceFragment's containing activity should
	 * implement to be able to process preference items that wish to
	 * switch to a specified fragment.
	 */
	interface OnPreferenceStartFragmentCallback {
		/**
		 * Called when the user has clicked on a Preference that has
		 * a fragment class name associated with it.  The implementation
		 * should instantiate and switch to an instance of the given
		 * fragment.
		 * @param caller The fragment requesting navigation.
		 * @param pref The preference requesting the fragment.
		 * @return true if the fragment creation has been handled
		 */
		fun onPreferenceStartFragment(caller: PreferenceFragmentCompat?, pref: Preference?): Boolean
	}

	/**
	 * Interface that PreferenceFragment's containing activity should
	 * implement to be able to process preference items that wish to
	 * switch to a new screen of preferences.
	 */
	interface OnPreferenceStartScreenCallback {
		/**
		 * Called when the user has clicked on a PreferenceScreen item in order to navigate to a new
		 * screen of preferences.
		 * @param caller The fragment requesting navigation.
		 * @param pref The preference screen to navigate to.
		 * @return true if the screen navigation has been handled
		 */
		fun onPreferenceStartScreen(caller: PreferenceFragmentCompat?, pref: PreferenceScreen?): Boolean
	}

	interface OnPreferenceDisplayDialogCallback {
		/**
		 * @param caller The fragment containing the preference requesting the dialog.
		 * @param pref   The preference requesting the dialog.
		 * @return true if the dialog creation has been handled.
		 */
		fun onPreferenceDisplayDialog(caller: PreferenceController?, pref: Preference?): Boolean
	}

	/**
	 * Convenience constructor for use when no arguments are needed.
	 */
	constructor() : super(null) {}

	/**
	 * Constructor that takes arguments that need to be retained across restarts.
	 *
	 * @param args Any arguments that need to be retained.
	 */
	constructor(args: Bundle) : super(args) {}

	/**
	 * Called during [.onCreate] to supply the preferences for this fragment.
	 * Subclasses are expected to call [.setPreferenceScreen] either
	 * directly or via helper methods such as [.addPreferencesFromResource].
	 *
	 * @param savedInstanceState If the fragment is being re-created from
	 * a previous saved state, this is the state.
	 * @param rootKey If non-null, this preference fragment should be rooted at the
	 * [androidx.preference.PreferenceScreen] with this key.
	 */
	abstract fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)


	@SuppressLint("PrivateResource")
	public override fun onCreateView(inflater: LayoutInflater, container: ViewGroup,
	                                 savedInstanceState: Bundle?): View {
		mInitDone = false
		mHavePrefs = false
		val tv = TypedValue()
		activity!!.theme.resolveAttribute(R.attr.preferenceTheme, tv, true)
		var theme = tv.resourceId
		if (theme == 0) {
			// Fallback to default theme.
			theme = R.style.PreferenceThemeOverlay
		}
		mStyledContext = ContextThemeWrapper(activity, theme)
		preferenceManager = PreferenceManager(mStyledContext)
		val rootKey = args.getString(ARG_PREFERENCE_ROOT)
		onCreatePreferences(savedInstanceState, rootKey)
		val a = mStyledContext!!.obtainStyledAttributes(null,
				R.styleable.PreferenceFragmentCompat,
				R.attr.preferenceFragmentCompatStyle,
				0)
		mLayoutResId = a.getResourceId(R.styleable.PreferenceFragmentCompat_android_layout,
				mLayoutResId)
		mDividerDecoration = DividerDecoration()
		val divider = a.getDrawable(
				R.styleable.PreferenceFragmentCompat_android_divider)
		val dividerHeight = a.getDimensionPixelSize(
				R.styleable.PreferenceFragmentCompat_android_dividerHeight, -1)
		val allowDividerAfterLastItem = a.getBoolean(
				R.styleable.PreferenceFragmentCompat_allowDividerAfterLastItem, true)
		a.recycle()
		val themedInflater = inflater.cloneInContext(mStyledContext)
		val view = themedInflater.inflate(mLayoutResId, container, false)
		val rawListContainer = view.findViewById<View>(AndroidResources.ANDROID_R_LIST_CONTAINER)
		if (rawListContainer !is ViewGroup) {
			throw RuntimeException("Content has view with id attribute "
					+ "'android.R.id.list_container' that is not a ViewGroup class")
		}
		val listView: RecyclerView = onCreateRecyclerView(
				themedInflater,
				rawListContainer,
				savedInstanceState
		)
		this.listView = listView
		listView.addItemDecoration(mDividerDecoration!!)
		setDivider(divider)
		if (dividerHeight != -1) {
			setDividerHeight(dividerHeight)
		}
		mDividerDecoration!!.setAllowDividerAfterLastItem(allowDividerAfterLastItem)

		// If mList isn't present in the view hierarchy, add it. mList is automatically inflated
		// on an Auto device so don't need to add it.
		if (listView.parent == null) {
			rawListContainer.addView(this.listView)
		}
		mHandler.post(mRequestFocus)
		onViewCreated(view, savedInstanceState)
		return view
	}

	/**
	 * Sets the drawable that will be drawn between each item in the list.
	 *
	 *
	 * **Note:** If the drawable does not have an intrinsic
	 * height, you should also call [.setDividerHeight].
	 *
	 * @param divider the drawable to use
	 * @attr ref R.styleable#PreferenceFragmentCompat_android_divider
	 */
	fun setDivider(divider: Drawable?) = mDividerDecoration?.setDivider(divider)

	/**
	 * Sets the height of the divider that will be drawn between each item in the list. Calling
	 * this will override the intrinsic height as set by [.setDivider]
	 *
	 * @param height The new height of the divider in pixels.
	 * @attr ref R.styleable#PreferenceFragmentCompat_android_dividerHeight
	 */
	fun setDividerHeight(height: Int) {
		mDividerDecoration?.setDividerHeight(height)
	}

	fun onViewCreated(@Suppress("UNUSED_PARAMETER") view: View?, savedInstanceState: Bundle?) {
		savedInstanceState?.getBundle(PREFERENCES_TAG)?.let {
			val preferenceScreen = preferenceScreen
			preferenceScreen?.restoreHierarchyState(it)
		}
		if (mHavePrefs) {
			bindPreferences()
			mSelectPreferenceRunnable?.let { it.run();mSelectPreferenceRunnable = null }
		}
		mInitDone = true
	}

	public override fun onAttach(view: View) {
		super.onAttach(view)
		preferenceManager!!.onDisplayPreferenceDialogListener = this
	}

	public override fun onDetach(view: View) {
		super.onDetach(view)
		preferenceManager!!.onDisplayPreferenceDialogListener = null
	}

	public override fun onDestroyView(view: View) {
		mHandler.removeCallbacks(mRequestFocus)
		mHandler.removeMessages(MSG_BIND_PREFERENCES)
		if (mHavePrefs) {
			unbindPreferences()
		}
		listView = null
		preferenceManager = null
		mStyledContext = null
		mDividerDecoration = null
		super.onDestroyView(view)
	}

	override fun onSaveViewState(view: View, outState: Bundle) {
		super.onSaveViewState(view, outState)
		val preferenceScreen = preferenceScreen
		preferenceScreen?.let {
			it.saveHierarchyState(bundleOf())
			outState.putBundle(PREFERENCES_TAG, bundleOf())
		}
	}

	/**
	 * Gets the root of the preference hierarchy that this fragment is showing.
	 *
	 * @return The [PreferenceScreen] that is the root of the preference
	 * hierarchy.
	 */
	/**
	 * Sets the root of the preference hierarchy that this fragment is showing.
	 *
	 * @param preferenceScreen The root [PreferenceScreen] of the preference hierarchy.
	 */
	var preferenceScreen: PreferenceScreen?
		get() = preferenceManager!!.preferenceScreen
		set(preferenceScreen) {
			if (preferenceManager!!.setPreferences(preferenceScreen) && preferenceScreen != null) {
				onUnbindPreferences()
				mHavePrefs = true
				if (mInitDone) {
					postBindPreferences()
				}
			}
		}

	/**
	 * Inflates the given XML resource and adds the preference hierarchy to the current
	 * preference hierarchy.
	 *
	 * @param preferencesResId The XML resource ID to inflate.
	 */
	fun addPreferencesFromResource(@XmlRes preferencesResId: Int) {
		requirePreferenceManager()
		preferenceScreen = preferenceManager!!.inflateFromResource(mStyledContext,
				preferencesResId, preferenceScreen)
	}

	/**
	 * Inflates the given XML resource and replaces the current preference hierarchy (if any) with
	 * the preference hierarchy rooted at `key`.
	 *
	 * @param preferencesResId The XML resource ID to inflate.
	 * @param key The preference key of the [androidx.preference.PreferenceScreen]
	 * to use as the root of the preference hierarchy, or null to use the root
	 * [androidx.preference.PreferenceScreen].
	 */
	fun setPreferencesFromResource(@XmlRes preferencesResId: Int, key: String?) {
		requirePreferenceManager()
		val xmlRoot = preferenceManager!!.inflateFromResource(mStyledContext,
				preferencesResId, null)
		val root: Preference?
		if (key != null) {
			root = xmlRoot.findPreference(key)
			require(root is PreferenceScreen) {
				("Preference object with key " + key
						+ " is not a PreferenceScreen")
			}
		} else {
			root = xmlRoot
		}
		preferenceScreen = root as PreferenceScreen?
	}

	/**
	 * Finds a [Preference] based on its key.
	 *
	 * @param key The key of the preference to retrieve.
	 * @return The [Preference] with the key, or null.
	 * @see androidx.preference.PreferenceGroup.findPreference
	 */
	override fun <T : Preference?> findPreference(key: CharSequence): T? {
		return preferenceManager?.findPreference<T>(key)
	}

	private fun requirePreferenceManager() =
			preferenceManager?.let { }
					?: throw RuntimeException("This should be called after super.onCreate.")

	private fun postBindPreferences() {
		if (mHandler.hasMessages(MSG_BIND_PREFERENCES)) return
		mHandler.obtainMessage(MSG_BIND_PREFERENCES).sendToTarget()
	}

	private fun bindPreferences() {
		val preferenceScreen = preferenceScreen
		preferenceScreen?.let {
			listView!!.adapter = onCreateAdapter(it)
			it.onAttached()
		}
		onBindPreferences()
	}

	private fun unbindPreferences() {
		val preferenceScreen = preferenceScreen
		preferenceScreen?.onDetached()
		onUnbindPreferences()
	}

	/** @hide
	 */
	@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
	protected fun onBindPreferences() {
	}

	/** @hide
	 */
	@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
	protected fun onUnbindPreferences() {
	}

	/**
	 * Creates the [RecyclerView] used to display the preferences.
	 * Subclasses may override this to return a customized
	 * [RecyclerView].
	 * @param inflater The LayoutInflater object that can be used to inflate the
	 * [RecyclerView].
	 * @param parent The parent [android.view.View] that the RecyclerView will be attached to.
	 * This method should not add the view itself, but this can be used to generate
	 * the LayoutParams of the view.
	 * @param savedInstanceState If non-null, this view is being re-constructed from a previous
	 * saved state as given here
	 * @return A new RecyclerView object to be placed into the view hierarchy
	 */
	fun onCreateRecyclerView(
			inflater: LayoutInflater,
			parent: ViewGroup,
			@Suppress("UNUSED_PARAMETER") savedInstanceState: Bundle?
	): RecyclerView {
		// If device detected is Auto, use Auto's custom layout that contains a custom ViewGroup
		// wrapping a RecyclerView
		if (mStyledContext!!.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
			return parent.findViewById(R.id.recycler_view)
		}
		val recyclerView = inflater
				.inflate(R.layout.preference_recyclerview, parent, false) as RecyclerView
		recyclerView.layoutManager = onCreateLayoutManager()
		recyclerView.setAccessibilityDelegateCompat(
				PreferenceRecyclerViewAccessibilityDelegate(recyclerView))
		return recyclerView
	}

	/**
	 * Called from [.onCreateRecyclerView] to create the
	 * [RecyclerView.LayoutManager] for the created
	 * [RecyclerView].
	 * @return A new [RecyclerView.LayoutManager] instance.
	 */
	fun onCreateLayoutManager(): RecyclerView.LayoutManager = LinearLayoutManager(activity)

	/**
	 * Creates the root adapter.
	 *
	 * @param preferenceScreen Preference screen object to create the adapter for.
	 * @return An adapter that contains the preferences contained in this [PreferenceScreen].
	 */
	private fun onCreateAdapter(preferenceScreen: PreferenceScreen?): RecyclerView.Adapter<*> =
			PreferenceGroupAdapter(preferenceScreen)

	/**
	 * Called when a preference in the tree requests to display a dialog. Subclasses should
	 * override this method to display custom dialogs or to handle dialogs for custom preference
	 * classes.
	 *
	 * @param preference The Preference object requesting the dialog.
	 */
	override fun onDisplayPreferenceDialog(preference: Preference) {
		var handled = false
		if (callbackFragment is OnPreferenceDisplayDialogCallback) {
			handled = (callbackFragment as OnPreferenceDisplayDialogCallback?)!!
					.onPreferenceDisplayDialog(this, preference)
		}
		if (!handled && activity is OnPreferenceDisplayDialogCallback) {
			handled = (activity as OnPreferenceDisplayDialogCallback?)!!
					.onPreferenceDisplayDialog(this, preference)
		}
		if (handled) {
			return
		}

		// check if dialog is already showing
		if (router.getControllerWithTag(DIALOG_FRAGMENT_TAG) != null) {
			return
		}
		val f: PreferenceDialogController = when (preference) {
			is EditTextPreference ->
				EditTextPreferenceDialogController.newInstance(preference.getKey())

			is ListPreference ->
				ListPreferenceDialogController.newInstance(preference.getKey())

			is MultiSelectListPreference ->
				MultiSelectListPreferenceDialogController.newInstance(preference.getKey())

			else ->
				throw IllegalArgumentException("Tried to display dialog for unknown " +
						"preference type. Did you forget to override onDisplayPreferenceDialog()?")

		}
		f.targetController = this
		f.showDialog(router, DIALOG_FRAGMENT_TAG)
	}

	/**
	 * Basically a wrapper for getParentFragment which is v17+. Used by the leanback preference lib.
	 * @return Fragment to possibly use as a callback
	 * @hide
	 */
	@get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
	val callbackFragment: Fragment?
		get() = null

	fun scrollToPreference(key: String?) =
			scrollToPreferenceInternal(null, key)

	fun scrollToPreference(preference: Preference?) =
			scrollToPreferenceInternal(preference, null)

	private fun scrollToPreferenceInternal(preference: Preference?, key: String?) {
		val r: Runnable = object : Runnable {
			override fun run() {
				val adapter = listView!!.adapter
				if (adapter !is PreferencePositionCallback) {
					// Adapter was set to null, so don't scroll I guess?
					check(adapter == null) {
						("Adapter must implement "
								+ "PreferencePositionCallback")
					}
					// Adapter was set to null, so don't scroll I guess?
					return
				}
				val position: Int
				position = if (preference != null) {
					(adapter as PreferencePositionCallback)
							.getPreferenceAdapterPosition(preference)
				} else {
					(adapter as PreferencePositionCallback)
							.getPreferenceAdapterPosition(key)
				}
				if (position != RecyclerView.NO_POSITION) {
					listView!!.scrollToPosition(position)
				} else {
					// Item not found, wait for an update and try again
					adapter.registerAdapterDataObserver(
							ScrollToPreferenceObserver(adapter, listView, preference, key))
				}
			}
		}
		if (listView == null) {
			mSelectPreferenceRunnable = r
		} else {
			r.run()
		}
	}

	private class ScrollToPreferenceObserver(
			private val mAdapter: RecyclerView.Adapter<*>,
			private val mList: RecyclerView?,
			private val mPreference: Preference?,
			private val mKey: String?
	) : AdapterDataObserver() {
		private fun scrollToPreference() {
			mAdapter.unregisterAdapterDataObserver(this)
			val position: Int = if (mPreference != null) {
				(mAdapter as PreferencePositionCallback)
						.getPreferenceAdapterPosition(mPreference)
			} else {
				(mAdapter as PreferencePositionCallback)
						.getPreferenceAdapterPosition(mKey)
			}
			if (position != RecyclerView.NO_POSITION) {
				mList!!.scrollToPosition(position)
			}
		}

		override fun onChanged() {
			scrollToPreference()
		}

		override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
			scrollToPreference()
		}

		override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
			scrollToPreference()
		}

		override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
			scrollToPreference()
		}

		override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
			scrollToPreference()
		}

		override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
			scrollToPreference()
		}

	}

	private inner class DividerDecoration internal constructor() : ItemDecoration() {
		private var mDivider: Drawable? = null
		private var mDividerHeight = 0
		private var mAllowDividerAfterLastItem = true
		override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
			if (mDivider == null) {
				return
			}
			val childCount = parent.childCount
			val width = parent.width
			for (childViewIndex in 0 until childCount) {
				val view = parent.getChildAt(childViewIndex)
				if (shouldDrawDividerBelow(view, parent)) {
					val top = view.y.toInt() + view.height
					mDivider!!.setBounds(0, top, width, top + mDividerHeight)
					mDivider!!.draw(c)
				}
			}
		}

		override fun getItemOffsets(
				outRect: Rect,
				view: View,
				parent: RecyclerView,
				state: RecyclerView.State
		) {
			if (shouldDrawDividerBelow(view, parent)) {
				outRect.bottom = mDividerHeight
			}
		}

		private fun shouldDrawDividerBelow(view: View, parent: RecyclerView): Boolean {
			val holder = parent.getChildViewHolder(view)
			val dividerAllowedBelow = (holder is PreferenceViewHolder
					&& holder.isDividerAllowedBelow)
			if (!dividerAllowedBelow) {
				return false
			}
			var nextAllowed = mAllowDividerAfterLastItem
			val index = parent.indexOfChild(view)
			if (index < parent.childCount - 1) {
				val nextView = parent.getChildAt(index + 1)
				val nextHolder = parent.getChildViewHolder(nextView)
				nextAllowed = (nextHolder is PreferenceViewHolder
						&& nextHolder.isDividerAllowedAbove)
			}
			return nextAllowed
		}

		fun setDivider(divider: Drawable?) {
			mDividerHeight = divider?.intrinsicHeight ?: 0
			mDivider = divider
			listView!!.invalidateItemDecorations()
		}

		fun setDividerHeight(dividerHeight: Int) {
			mDividerHeight = dividerHeight
			listView!!.invalidateItemDecorations()
		}

		fun setAllowDividerAfterLastItem(allowDividerAfterLastItem: Boolean) {
			mAllowDividerAfterLastItem = allowDividerAfterLastItem
		}
	}
}