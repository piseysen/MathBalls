package org.nocrew.tomas.mathballs;

import android.util.DisplayMetrics;
import android.app.Activity;
import android.app.Fragment;
import android.app.AlertDialog;
import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuInflater;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.Toast;

import org.nocrew.tomas.mathballs.R;

public class GameFragment extends Fragment
{
    private final static String TAG = "MB-Game";

    public interface Listener {
	public void doStartGame(boolean startnew);
	public boolean doBackFragment();
        public void doShowLeaderboard(int gameType);
        public void doUnlockAchievement(int id);
        public void doSubmitHighscore(int score, int gameType);
	public void doPlaySound(int snd);
	public void doStopSoundAll();
	public int getGameType();
	public String getGameTypeString(int gameType);
    }

    private Listener listener = null;

    private GameSurfaceView surfaceView;
    private boolean startNew = true;

    private TextView tvTargetSum;
    private TextView tvScore;
    private int lastTargetSum = -1;
    private int lastPressedSum = -1;
    private int lastScore = -1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
			     Bundle savedInstanceState) {
	Debug.log(TAG, "onCreateView");
	View v = inflater.inflate(R.layout.game, container, false);

	ActionBar ab = getActivity().getActionBar();
	ab.setDisplayShowCustomEnabled(true);
	ab.setDisplayHomeAsUpEnabled(true);

	View abv = inflater.inflate(R.layout.game_actionbar, null);
	ab.setCustomView(abv);
	ImageButton ib = (ImageButton)abv.findViewById(R.id.pause);
	ib.setOnClickListener(new View.OnClickListener() {
		public void onClick(View v) {
		    if(surfaceView != null)
			surfaceView.doPause();
		}
	    });
	tvTargetSum = (TextView)abv.findViewById(R.id.target_sum_text);
	tvScore = (TextView)abv.findViewById(R.id.score_text);

	DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().
	    getMetrics(metrics);

	surfaceView = new GameSurfaceView(getActivity(), metrics.density,
					  startNew, this);
	FrameLayout fl = (FrameLayout)v.findViewById(R.id.gamelayout);
	fl.addView(surfaceView);

	lastTargetSum = -1;
	lastPressedSum = -1;
	lastScore = -1;

	return v;
    }

    public void setListener(Listener l) {
	listener = l;
    }

    public void setGameType(boolean startnew) {
	Debug.log(TAG, "setGameType startnew=" + startnew);
	startNew = startnew;
    }

    public void resetGame(boolean startnew) {
	lastTargetSum = -1;
	lastPressedSum = -1;
	lastScore = -1;
	surfaceView.resetGame(startnew);
    }

    public void updateActionBar(int targetSum, int level,
				int pressedSum, int score,
				int gameType) {
	if(targetSum != lastTargetSum ||
	   pressedSum != lastPressedSum) {
	    String text = String.valueOf(targetSum);
	    if(pressedSum > 0) {
		String str;

		if(gameType == MainActivity.DIFFICULTY_EASY)
		    str = String.valueOf(pressedSum);
		else if(gameType == MainActivity.DIFFICULTY_HARD)
		    str = level >= 2 ? "-" : String.valueOf(pressedSum);
		else // NORMAL
		    str = level >= 5 ? "-" : String.valueOf(pressedSum);

		text = text + " (" + str + ")";
	    }
	    tvTargetSum.setText(text);

	    lastTargetSum = targetSum;
	    lastPressedSum = pressedSum;
	}

	if(score != lastScore) {
	    tvScore.setText(String.valueOf(score));
	    lastScore = score;
	}
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
    public void onSaveInstanceState(Bundle outState) {
	Debug.log(TAG, "onSaveInstanceState");
    }
}
