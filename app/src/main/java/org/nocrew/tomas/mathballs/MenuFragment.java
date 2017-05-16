package org.nocrew.tomas.mathballs;

import android.util.DisplayMetrics;
import android.app.Activity;
import android.app.Fragment;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Build;
import android.preference.PreferenceManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Toast;
import android.app.ActionBar;
import android.content.res.Configuration;

import org.nocrew.tomas.mathballs.R;

public class MenuFragment extends Fragment
    implements AdapterView.OnItemSelectedListener
{
    private final static String TAG = "MB-Menu";

    public interface Listener {
	public void doStartGame(boolean startnew);
	public void doOpenSettings();
	public void doOpenRate();
	public void doShowAbout();
        public void doShowAchievements();
        public void doShowAllLeaderboards();
	public int getGameType();
	public void setGameType(int gameType);
	public String getGameTypeString(int gameType);
    }

    private Listener listener = null;

    private MenuSurfaceView surfaceView;
    private FrameLayout layout = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
			     Bundle savedInstanceState) {
	View v = inflater.inflate(R.layout.menu, container, false);

	ActionBar ab = getActivity().getActionBar();
	ab.setDisplayShowCustomEnabled(true);
	ab.setDisplayHomeAsUpEnabled(false);
	ab.setDisplayShowHomeEnabled(true);

	View abv = inflater.inflate(R.layout.menu_actionbar, null);
	ab.setCustomView(abv);

	Spinner spinner = (Spinner)abv.findViewById(R.id.gametype);
	ArrayAdapter<CharSequence> adapter =
	    ArrayAdapter.createFromResource(getActivity(),
					    R.array.gametype_array,
					    android.R.layout.simple_spinner_item);
	adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	spinner.setAdapter(adapter);
	spinner.setOnItemSelectedListener(this);
	spinner.setSelection(listener.getGameType());

	setHasOptionsMenu(true);

	DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().
	    getMetrics(metrics);

	surfaceView = new MenuSurfaceView(getActivity(), metrics.density, this);
	layout = (FrameLayout)v.findViewById(R.id.mainlayout);
	layout.addView(surfaceView);

	return v;
    }

    public void setListener(Listener l) {
	listener = l;
    }

    @Override
    public void onDestroyView() {
	super.onStop();
	Debug.log(TAG, "onDestroyView");
	layout = null;
    }

    @Override
    public void onPause() {
	super.onPause();
	Debug.log(TAG, "onPause");
	surfaceView.onPause();
    }

    @Override
    public void onResume() {
	super.onResume();
	Debug.log(TAG, "onResume");
	surfaceView.onResume();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	inflater.inflate(R.menu.menu_options, menu);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view,
			       int pos, long id) {
	listener.setGameType(pos);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}
