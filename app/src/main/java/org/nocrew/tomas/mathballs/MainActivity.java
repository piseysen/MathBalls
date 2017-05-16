package org.nocrew.tomas.mathballs;

import android.util.DisplayMetrics;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
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
import android.widget.Toast;
import android.app.ActionBar;
import android.content.res.Configuration;
import android.media.SoundPool;
import android.media.AudioManager;
import android.net.Uri;

import com.google.android.gms.games.Games;
import com.google.android.gms.games.Player;

import org.nocrew.tomas.mathballs.basegameutils.*;

import org.nocrew.tomas.mathballs.R;

public class MainActivity extends BaseGameActivity
    implements MenuFragment.Listener,
	       GameFragment.Listener,
	       SettingsFragment.Listener
{
    private final static String TAG = "MB-Main";

    private MenuFragment menuFragment;
    private GameFragment gameFragment;
    private SettingsFragment settingsFragment;

    private FrameLayout layout;
    private boolean showAds = false;

    private final static int FRAG_MENU = 0;
    private final static int FRAG_GAME = 1;
    private final static int FRAG_SETTINGS = 2;
    private int currFragment = FRAG_MENU;

    // request codes we use when invoking an external activity
    private static final int RC_RESOLVE = 5000, RC_UNUSED = 5001;
    private static final int RC_REQUEST = 10001;

    private SoundPool soundPool;
    private int sounds[];
    private int streams[];
    public static final int SOUND_DROP_FIRST          = 0;
    public static final int SOUND_DROP_1              = 0;
    public static final int SOUND_DROP_2              = 1;
    public static final int SOUND_DROP_3              = 2;
    public static final int SOUND_DROP_NUM            = 3;
    public static final int SOUND_POP_FIRST           = 3;
    public static final int SOUND_POP_1               = 3;
    public static final int SOUND_POP_2               = 4;
    public static final int SOUND_POP_3               = 5;
    public static final int SOUND_POP_4               = 6;
    public static final int SOUND_POP_5               = 7;
    public static final int SOUND_POP_6               = 8;
    public static final int SOUND_POP_NUM             = 6;
    public static final int SOUND_NUM                 = 9;

    public final static int DIFFICULTY_EASY   = 0;
    public final static int DIFFICULTY_NORMAL = 1;
    public final static int DIFFICULTY_HARD   = 2;
    public int gameType = DIFFICULTY_NORMAL;

    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.main);

	SharedPreferences prefs =
	    PreferenceManager.getDefaultSharedPreferences(this);

	// Play Game Services

	mHelper.setConnectOnStart(prefs.getBoolean("pref_gpgs_enable", true));

	// Setup fragments

	menuFragment = new MenuFragment();
	gameFragment = new GameFragment();
	settingsFragment = new SettingsFragment();

	menuFragment.setListener(this);
	gameFragment.setListener(this);
	settingsFragment.setListener(this);

	getFragmentManager().beginTransaction().add(R.id.fragment_container,
						    menuFragment).commit();

	setVolumeControlStream(AudioManager.STREAM_MUSIC);
	setupSounds();
    }

    private void setupSounds() {
	soundPool = new SoundPool(SOUND_NUM, AudioManager.STREAM_MUSIC, 0);
	sounds = new int[SOUND_NUM];
	streams = new int[SOUND_NUM];
	sounds[SOUND_DROP_1] = soundPool.load(this, R.raw.waterdrop_1, 1);
	sounds[SOUND_DROP_2] = soundPool.load(this, R.raw.waterdrop_2, 1);
	sounds[SOUND_DROP_3] = soundPool.load(this, R.raw.waterdrop_3, 1);
	sounds[SOUND_POP_1] = soundPool.load(this, R.raw.pop_1, 1);
	sounds[SOUND_POP_2] = soundPool.load(this, R.raw.pop_2, 1);
	sounds[SOUND_POP_3] = soundPool.load(this, R.raw.pop_3, 1);
	sounds[SOUND_POP_4] = soundPool.load(this, R.raw.pop_4, 1);
	sounds[SOUND_POP_5] = soundPool.load(this, R.raw.pop_5, 1);
	sounds[SOUND_POP_6] = soundPool.load(this, R.raw.pop_6, 1);
    }

    private void switchToFragment(Fragment newFrag, boolean addToStack) {
	FragmentManager fm = getFragmentManager();
	FragmentTransaction ft = fm.beginTransaction();
	
	ft.replace(R.id.fragment_container, newFrag);

	if(addToStack) {
	    ft.addToBackStack(null);
	}

	ft.commit();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
	super.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
	int itemId = item.getItemId();

	switch(itemId) {
        case android.R.id.home:
	    doBackFragment();
            break;

	case R.id.menu_pref:
	    doOpenSettings();
	    break;

	case R.id.menu_rate:
	    doOpenRate();
	    break;

	case R.id.menu_about:
	    showAbout();
	    break;

	default:
	    return false;
	}

	return true;
    }

    @Override
    public void onBackPressed() {
	if(!doBackFragment())
	    super.onBackPressed();
    }

    private void showAbout() {
	AlertDialog.Builder builder;
	AlertDialog alert;
	LayoutInflater inflater;

	inflater =
	    (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);

	builder = new AlertDialog.Builder(this);
	builder.setTitle(R.string.about_title);
	builder.setIcon(R.drawable.ic_home);
	builder.setCancelable(true);
	builder.setPositiveButton(R.string.about_button_ok, null);
	builder.setView(inflater.inflate(R.layout.about, null));
	alert = builder.create();
	alert.show();
    }


    public void onSignInSucceeded() {
    }

    public void onSignInFailed() {
	SharedPreferences prefs =
	    PreferenceManager.getDefaultSharedPreferences(this);

	if(prefs.getBoolean("pref_gpgs_enable", true))
	    showAlert(getString(R.string.gamehelper_sign_in_failed));
    }


    private int getLeaderboardByGameType(int gameType) {
	if(gameType == MainActivity.DIFFICULTY_EASY)
	    return R.string.leaderboard_high_scores__easy;
	else if(gameType == MainActivity.DIFFICULTY_HARD)
	    return R.string.leaderboard_high_scores__hard;
	else
	    return R.string.leaderboard_high_scores__normal;
    }


    // Called from fragments

    public void doStartGame(boolean startnew) {
	gameFragment.setGameType(startnew);
	if(currFragment == FRAG_GAME) {
	    gameFragment.resetGame(true);
	} else {
	    switchToFragment(gameFragment, true);
	    currFragment = FRAG_GAME;
	}
    }

    public boolean doBackFragment() {
	FragmentManager fm = getFragmentManager();

	if(fm.getBackStackEntryCount() <= 0)
	    return false;

	fm.popBackStack();
	currFragment = FRAG_MENU;

	return true;
    }

    public void doOpenSettings() {
	switchToFragment(settingsFragment, true);
	currFragment = FRAG_SETTINGS;
    }

    public void doOpenRate() {
	Intent marketIntent =
	    new Intent(Intent.ACTION_VIEW,
		       Uri.parse("market://details?id=org.nocrew.tomas.mathballs"));
	marketIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
	startActivity(marketIntent);
    }

    public void doShowAbout() {
	showAbout();
    }

    public void doShowAchievements() {
	if(isSignedIn()) {
	    startActivityForResult(Games.Achievements.
				   getAchievementsIntent(getApiClient()),
				   RC_UNUSED);
	} else {
	    showAlert(getString(R.string.gms_alert_achievements));
	}
    }

    public void doShowAllLeaderboards() {
	if(isSignedIn()) {
	    startActivityForResult(Games.Leaderboards.
				   getAllLeaderboardsIntent(getApiClient()),
				   RC_UNUSED);
	} else {
	    showAlert(getString(R.string.gms_alert_leaderboards));
	}
    }

    public void doShowLeaderboard(int gameType) {
	int leaderboard = getLeaderboardByGameType(gameType);

	if(isSignedIn()) {
	    startActivityForResult(Games.Leaderboards.
				   getLeaderboardIntent(getApiClient(),
							getString(leaderboard)),
				   RC_UNUSED);
	} else {
	    showAlert(getString(R.string.gms_alert_leaderboards));
	}
    }

    public void doShowGPGSSettings() {
	if(!isSignedIn())
	    return;

	startActivityForResult(Games.
			       getSettingsIntent(getApiClient()),
			       RC_UNUSED);
    }

    public boolean getIsSignedIn() {
	return isSignedIn();
    }

    public String getSignedInUser() {
	Player p = Games.Players.getCurrentPlayer(getApiClient());
	if(p == null)
	    return "Unknown User";
	else
	    return p.getDisplayName();
    }

    public void doSignInClicked() {
	beginUserInitiatedSignIn();
    }

    public void doSignOutClicked() {
	signOut();
    }

    public void doUnlockAchievement(int id) {
	if(!isSignedIn())
	    return;

	Games.Achievements.unlock(getApiClient(), getString(id));
    }

    public void doSubmitHighscore(int score, int gameType) {
	if(!isSignedIn())
	    return;

	int leaderboard = getLeaderboardByGameType(gameType);

	Games.Leaderboards.submitScore(getApiClient(),
				       getString(leaderboard),
				       score);
    }

    public void doPlaySound(int snd) {
	soundPool.stop(streams[snd]);
	streams[snd] = soundPool.play(sounds[snd],
				      1.0f, 1.0f,
				      0, 0, 1.0f);
    }

    public void doStopSoundAll() {
	for(int i = 0 ; i < SOUND_NUM ; i++)
	    soundPool.stop(streams[i]);
    }

    public int getGameType() {
	SharedPreferences prefs =
	    PreferenceManager.getDefaultSharedPreferences(this);
	int gameType = prefs.getInt("game_default_type", DIFFICULTY_NORMAL);
	if(gameType < DIFFICULTY_EASY || gameType > DIFFICULTY_HARD)
	    gameType = DIFFICULTY_NORMAL;

	return gameType;
    }

    public void setGameType(int gameType) {
	SharedPreferences prefs =
	    PreferenceManager.getDefaultSharedPreferences(this);
	SharedPreferences.Editor editor = prefs.edit();
	editor.putInt("game_default_type", gameType);
	editor.commit();
    }

    public String getGameTypeString(int gameType) {
	String[] gameTypeArray =
	    getResources().getStringArray(R.array.gametype_array);
	return gameTypeArray[gameType];
    }
}
