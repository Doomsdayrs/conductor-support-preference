/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import java.util.*

class MultiSelectListPreferenceDialogController : PreferenceDialogController() {
	var mNewValues: MutableSet<String> = HashSet()
	var mPreferenceChanged = false
	var mEntries: Array<CharSequence> = arrayOf()
	var mEntryValues: Array<CharSequence> = arrayOf()
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (savedInstanceState == null) {
			val preference = listPreference
			check(!(preference!!.entries == null || preference.entryValues == null)) {
				"MultiSelectListPreference requires an entries array and " +
						"an entryValues array."
			}
			mNewValues.clear()
			mNewValues.addAll(preference.values)
			mPreferenceChanged = false
			mEntries = preference.entries
			mEntryValues = preference.entryValues
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putStringArrayList(SAVE_STATE_VALUES, ArrayList(mNewValues))
		outState.putBoolean(SAVE_STATE_CHANGED, mPreferenceChanged)
		outState.putCharSequenceArray(SAVE_STATE_ENTRIES, mEntries)
		outState.putCharSequenceArray(SAVE_STATE_ENTRY_VALUES, mEntryValues)
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		super.onRestoreInstanceState(savedInstanceState)
		mNewValues.clear()
		mNewValues.addAll(savedInstanceState.getStringArrayList(SAVE_STATE_VALUES)!!)
		mPreferenceChanged = savedInstanceState.getBoolean(SAVE_STATE_CHANGED, false)
		mEntries = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRIES) ?: arrayOf()
		mEntryValues = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRY_VALUES) ?: arrayOf()
	}

	private val listPreference: MultiSelectListPreference?
		get() = preference as MultiSelectListPreference

	override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
		super.onPrepareDialogBuilder(builder)
		val entryCount = mEntryValues.size
		val checkedItems = BooleanArray(entryCount)
		for (i in 0 until entryCount) {
			checkedItems[i] = mNewValues.contains(mEntryValues[i].toString())
		}
		builder.setMultiChoiceItems(mEntries, checkedItems
		) { _, which, isChecked ->
			mPreferenceChanged = if (isChecked) {
				mPreferenceChanged or mNewValues.add(
						mEntryValues[which].toString())
			} else {
				mPreferenceChanged or mNewValues.remove(
						mEntryValues[which].toString())
			}
		}
	}

	override fun onDialogClosed(positiveResult: Boolean) {
		val preference = listPreference
		if (positiveResult && mPreferenceChanged) {
			val values: Set<String> = mNewValues
			if (preference!!.callChangeListener(values)) {
				preference.values = values
			}
		}
		mPreferenceChanged = false
	}

	companion object {
		private const val SAVE_STATE_VALUES = "MultiSelectListPreferenceDialogController.values"
		private const val SAVE_STATE_CHANGED = "MultiSelectListPreferenceDialogController.changed"
		private const val SAVE_STATE_ENTRIES = "MultiSelectListPreferenceDialogController.entries"
		private const val SAVE_STATE_ENTRY_VALUES = "MultiSelectListPreferenceDialogController.entryValues"
		fun newInstance(key: String?): MultiSelectListPreferenceDialogController {
			val controller = MultiSelectListPreferenceDialogController()
			controller.args.putString(ARG_KEY, key)
			return controller
		}
	}
}