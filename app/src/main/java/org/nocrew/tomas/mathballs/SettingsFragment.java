package org.nocrew.tomas.mathballs;

import android.os.Bundle;
import android.os.Handler;
import android.content.Context;
import android.app.ActionBar;
import android.view.View;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import org.nocrew.tomas.mathballs.R;

public class SettingsFragment extends PreferenceFragment
    implements OnSharedPreferenceChangeListener,
	       Preference.OnPreferenceClickListener {
    private final static String TAG = "MB-Settings";

    public interface Listener {
	public boolean getIsSignedIn();
	public String getSignedInUser();
	public void doSignInClicked();
	public void doSignOutClicked();
	public void doShowGPGSSettings();
    }

    private Listener listener = null;
    private Handler updateHandler = new Handler();
    private final static long UPDATE_TIME_INTERVAL = 1000; // ms

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

	ActionBar ab = getActivity().getActionBar();
	ab.setDisplayShowCustomEnabled(false);
	ab.setDisplayHomeAsUpEnabled(true);

	setHasOptionsMenu(false);

	Preference pref;

	pref = findPreference("pref_gpgs_signin");
	pref.setOnPreferenceClickListener(this);

	pref = findPreference("pref_gpgs_settings");
	pref.setOnPreferenceClickListener(this);
	
	updatePreferenceSummaries();
    }

    private Runnable updateCallback = new Runnable() {
	    public void run() {
		updatePreferenceSummaries();
		updateHandler.postDelayed(updateCallback,
					  UPDATE_TIME_INTERVAL);
	    }
	};
	    

    public void setListener(Listener l) {
	listener = l;
    }

    @Override
    public void onPause(){
	super.onPause();
	getPreferenceScreen().getSharedPreferences()
	    .unregisterOnSharedPreferenceChangeListener(this);

	updateHandler.removeCallbacks(updateCallback);
    }

    @Override
    public void onResume() {
	super.onResume();
	getPreferenceScreen().getSharedPreferences()
	    .registerOnSharedPreferenceChangeListener(this);
	updatePreferenceSummaries();

	updateHandler.postDelayed(updateCallback,
				  UPDATE_TIME_INTERVAL);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
					  String key) {
        updatePreferenceSummaries();
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
	String key = pref.getKey();

	if(key.equals("pref_gpgs_signin")) {
	    listener.doSignInClicked();
	    return true;
	} else if(key.equals("pref_gpgs_settings")) {
	    listener.doShowGPGSSettings();
	    return true;
	}

	return false;
    }

    private void updatePreferenceSummaries() {
	CheckBoxPreference cbpref;
	Preference pref;

	cbpref = (CheckBoxPreference)findPreference("pref_gpgs_enable");
	if(!cbpref.isChecked()) {
	    if(listener.getIsSignedIn()) {
		listener.doSignOutClicked();
	    }

	    pref = findPreference("pref_gpgs_signin");
	    pref.setEnabled(false);
	    pref.setSummary(R.string.pref_gpgs_signin_summary_disabled);
	    pref = findPreference("pref_gpgs_settings");
	    pref.setEnabled(false);
	    pref.setSummary(R.string.pref_gpgs_settings_summary_disabled);
	} else {
	    boolean signedIn = listener.getIsSignedIn();
	    String userName = null;
	    String summary = "";
	    if(signedIn)
		userName = listener.getSignedInUser();

	    pref = findPreference("pref_gpgs_signin");
	    if(signedIn) {
		pref.setEnabled(false);
		summary =
		    String.format(getString(R.string.pref_gpgs_signin_summary_enabled_signedin), userName);
	    } else {
		pref.setEnabled(true);
		summary = getString(R.string.pref_gpgs_signin_summary_enabled_signedout);
	    }
	    pref.setSummary(summary);

	    pref = findPreference("pref_gpgs_settings");
	    if(signedIn) {
		pref.setEnabled(true);
		pref.setSummary(R.string.pref_gpgs_settings_summary_enabled_signedin);
	    } else {
		pref.setEnabled(false);
		pref.setSummary(R.string.pref_gpgs_settings_summary_enabled_signedout);
	    }
	}
    }
}