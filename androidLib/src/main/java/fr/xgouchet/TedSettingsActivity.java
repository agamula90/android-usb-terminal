package fr.xgouchet;

import static fr.xgouchet.texteditor.common.Constants.EXTRA_REQUEST_CODE;
import static fr.xgouchet.texteditor.common.Constants.PREFERENCES_NAME;
import static fr.xgouchet.texteditor.common.Constants.PREFERENCE_COLOR_THEME;
import static fr.xgouchet.texteditor.common.Constants.PREFERENCE_ENCODING;
import static fr.xgouchet.texteditor.common.Constants.PREFERENCE_END_OF_LINES;
import static fr.xgouchet.texteditor.common.Constants.PREFERENCE_MAX_RECENTS;
import static fr.xgouchet.texteditor.common.Constants.PREFERENCE_MAX_UNDO_STACK;
import static fr.xgouchet.texteditor.common.Constants.PREFERENCE_SELECT_FONT;
import static fr.xgouchet.texteditor.common.Constants.PREFERENCE_TEXT_SIZE;
import static fr.xgouchet.texteditor.common.Constants.PREFERENCE_USE_HOME_PAGE;
import static fr.xgouchet.texteditor.common.Constants.REQUEST_FONT;
import static fr.xgouchet.texteditor.common.Constants.REQUEST_HOME_PAGE;
import static fr.xgouchet.texteditor.common.Constants.TAG;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;

import fr.xgouchet.BuildConfig;
import fr.xgouchet.R;

import java.io.File;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
import fr.xgouchet.androidlib.data.FileUtils;
import fr.xgouchet.texteditor.common.Settings;
import fr.xgouchet.texteditor.ui.view.AdvancedEditText;

@SuppressWarnings("deprecation")
public class TedSettingsActivity extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	protected AdvancedEditText mSampleTED;

	protected boolean mPreviousHP;

	/**
	 * @see PreferenceActivity#onCreate(Bundle)
	 */

	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		getPreferenceManager().setSharedPreferencesName(PREFERENCES_NAME);

		addPreferencesFromResource(R.xml.ted_prefs);
		setContentView(R.layout.layout_prefs);

		getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener
				(this);

		mSampleTED = (AdvancedEditText) findViewById(R.id.sampleEditor);

		Settings.updateFromPreferences(getPreferenceManager().getSharedPreferences());
		mSampleTED.updateFromSettings();
		mSampleTED.setEnabled(false);

		mPreviousHP = Settings.USE_HOME_PAGE;

		findPreference(PREFERENCE_SELECT_FONT).setOnPreferenceClickListener(new
				                                                                    OnPreferenceClickListener() {

					public boolean onPreferenceClick(Preference preference) {
						Intent selectFont = new Intent();
						selectFont.setClass(getApplicationContext(), TedFontActivity.class);
						try {
							startActivityForResult(selectFont, REQUEST_FONT);
						} catch (ActivityNotFoundException e) {
							Crouton.showText(TedSettingsActivity.this, R.string
									.toast_activity_open, Style.ALERT);
						}
						return true;
					}
				});

		updateSummaries();
	}

	/**
	 * @see SharedPreferences
	 * .OnSharedPreferenceChangeListener#onSharedPreferenceChanged(android.content
	 * .SharedPreferences,
	 * java.lang.String)
	 */
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		Settings.updateFromPreferences(sharedPreferences);
		mSampleTED.updateFromSettings();
		updateSummaries();

		CheckBoxPreference checkBox = (CheckBoxPreference) findPreference
				(PREFERENCE_USE_HOME_PAGE);
		checkBox.setSummaryOn(Settings.HOME_PAGE_PATH);

		if (Settings.USE_HOME_PAGE && !mPreviousHP) {
			Intent setHomePage = new Intent();
			setHomePage.setClass(this, TedOpenActivity.class);
			setHomePage.putExtra(EXTRA_REQUEST_CODE, REQUEST_HOME_PAGE);
			try {
				startActivityForResult(setHomePage, REQUEST_HOME_PAGE);
			} catch (ActivityNotFoundException e) {
				Crouton.showText(this, R.string.toast_activity_open, Style.ALERT);
			}
		}

		mPreviousHP = Settings.USE_HOME_PAGE;
	}

	/**
	 * @see PreferenceActivity#onActivityResult(int, int,
	 * Intent)
	 */
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Bundle extras;

		if (BuildConfig.DEBUG)
			Log.d(TAG, "onActivityResult");

		if (resultCode == RESULT_CANCELED) {
			if (BuildConfig.DEBUG)
				Log.d(TAG, "Result canceled");
			((CheckBoxPreference) findPreference(PREFERENCE_USE_HOME_PAGE)).setChecked(false);
			return;
		}

		if ((resultCode != RESULT_OK) || (data == null)) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, "Result error or null data! / " + resultCode);
			((CheckBoxPreference) findPreference(PREFERENCE_USE_HOME_PAGE)).setChecked(false);
			return;
		}

		switch (requestCode) {
			case REQUEST_HOME_PAGE:
				extras = data.getExtras();
				if (extras != null) {
					if (BuildConfig.DEBUG)
						Log.d(TAG, "Open : " + extras.getString("path"));
					Settings.HOME_PAGE_PATH = extras.getString("path");
					Settings.saveHomePage(getSharedPreferences(PREFERENCES_NAME, Context
							.MODE_PRIVATE));
					updateSummaries();
				}
				break;
			case REQUEST_FONT:
				Log.i(TAG, "Selected Font : " + data.getData().getPath());
				File src = new File(data.getData().getPath());
				FileUtils.copyFile(src, Settings.getFontFile(this));
				mSampleTED.updateFromSettings();
				break;
		}
	}

	/**
	 * Updates the summaries for every list
	 */
	protected void updateSummaries() {
		ListPreference listPref;
		CheckBoxPreference cbPref;

		listPref = (ListPreference) findPreference(PREFERENCE_COLOR_THEME);
		listPref.setSummary(listPref.getEntry());

		listPref = (ListPreference) findPreference(PREFERENCE_TEXT_SIZE);
		listPref.setSummary(listPref.getEntry());

		listPref = (ListPreference) findPreference(PREFERENCE_END_OF_LINES);
		listPref.setSummary(listPref.getEntry());

		listPref = (ListPreference) findPreference(PREFERENCE_ENCODING);
		listPref.setSummary(listPref.getEntry());

		listPref = (ListPreference) findPreference(PREFERENCE_MAX_RECENTS);
		listPref.setSummary(listPref.getEntry());

		listPref = (ListPreference) findPreference(PREFERENCE_MAX_UNDO_STACK);
		listPref.setSummary(listPref.getEntry());

		cbPref = (CheckBoxPreference) findPreference(PREFERENCE_USE_HOME_PAGE);
		if (cbPref.isChecked()) {
			cbPref.setSummaryOn(Settings.HOME_PAGE_PATH);
		}
	}

}
