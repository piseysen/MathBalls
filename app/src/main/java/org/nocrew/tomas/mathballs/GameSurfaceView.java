package org.nocrew.tomas.mathballs;

import java.util.Random;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.crypto.*;
import javax.crypto.spec.DESKeySpec;
import android.os.Build;
import android.os.Handler;
import android.os.Vibrator;
import android.os.Environment;
import android.content.Intent;
import android.content.Context;
import android.view.View;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.Button;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.util.Base64;
import android.net.Uri;

import org.nocrew.tomas.mathballs.R;

public class GameSurfaceView extends MySurfaceView
    implements SurfaceHolder.Callback {
    private final static String TAG = "MB-GameSV";

    private GameFragment.Listener listener;
    private GameFragment fragment;
    private Context context;
    private DrawThread thread = null;
    private SurfaceHolder holder;
    private Handler handler = new Handler();
    private Vibrator vibrator;
    private SharedPreferences prefs;

    private boolean makeSound;
    private boolean makeVibration;
    private boolean makeFullscreen;

    private boolean gotSurfaceChanged = false;
    private int surfaceWidth;
    private int surfaceHeight;
    private float density;

    private Random rnd;

    private Typeface typeface;
    private Paint ballPaint;
    private Paint headerTextPaint;
    private Paint headerMonoPaint;
    private Paint removeLinePaint;
    private Paint timeLinePaint;
    private Paint levelPaint;
    private Paint pausePaint;
    private float ballSize;
    private float removeLinePosY;
    private float timeLinePosY;

    private final static int GAME_MAX_BALLS = 12;
    private final static int GAME_MAX_REMOVE_DELAY = 100; // ms

    private class Ball {
	public float x, y;
	public float size;
	public float changeSize;
	public int changeAlpha;
	public int number;
	public int type;
	public int r, g, b;
	public boolean pressed;
	public String numText;
	public int state;
	public long delay;

	// For storage
	public final static int VERSION = 1;

	public final static int STATE_HIDDEN    = 0;
	public final static int STATE_SHOWING   = 1;
	public final static int STATE_SHOWN     = 2;
	public final static int STATE_REMOVING  = 3;

	public final static int GREEN = 1;
	public final static int RED = 2;
	public final static int BLUE = 3;

	public Ball(float x, float y, float size, int number, int type) {
	    init(x, y, size, number, type);
	}

	public Ball(String serialized) {
	    String[] strs = serialized.split("\\s+");
	    // FIXME: Look at version, when we have more
	    float sx = Float.parseFloat(strs[1]);
	    float sy = Float.parseFloat(strs[2]);
	    float ssize = Float.parseFloat(strs[3]);
	    int snumber = Integer.parseInt(strs[4]);
	    int stype = Integer.parseInt(strs[5]);
	    int sstate = Integer.parseInt(strs[6]);
	    float schangesize = Float.parseFloat(strs[7]);
	    int schangealpha = Integer.parseInt(strs[8]);

	    init(sx, sy, ssize, snumber, stype);
	    this.state = sstate;
	    this.changeSize = schangesize;
	    this.changeAlpha = schangealpha;
	}

	private void init(float x, float y, float size, int number, int type) {
	    this.x = x;
	    this.y = y;
	    this.changeSize = 0.0f;
	    this.changeAlpha = 255;
	    this.size = size;
	    this.number = number;
	    this.type = type;
	    this.numText = String.valueOf(number);
	    if(type == RED) {
		this.r = 255;
		this.g = 100;
		this.b = 100;
	    } else if(type == GREEN) {
		this.r = 100;
		this.g = 255;
		this.b = 100;
	    } else if(type == BLUE) {
		this.r = 100;
		this.g = 100;
		this.b = 255;
	    }
	    this.pressed = false;
	    this.state = STATE_HIDDEN;
	    this.delay = 0;
	}

	public String serialize() {
	    return VERSION + " " + x + " " + y + " " + size + " " +
		number + " " + type + " " + state + " " + changeSize + " " +
		changeAlpha;
	}
    }

    private class BallScore {
	int score;
	int removed;

	public BallScore() {
	    this.score = 0;
	    this.removed = 0;
	}
	public void inc(int incScore) {
	    this.score += incScore;
	    this.removed++;
	}
    }

    private ArrayList<Ball> ballList = new ArrayList<Ball>();
    private ArrayList<Ball> removedBallList = new ArrayList<Ball>();
    private int targetSum;
    private int targetBalls;
    private int pressedSum;
    private int score;
    private int removeTotal;
    private int removeLevel;
    private int level;
    private int removePerLevel;
    private int maxBallShowing;
    private int gameType;

    private boolean paused;
    private boolean pauseShowing;

    private boolean gameOver = false;

    private long prevUpdateTime;
    private long timeoutInterval;
    private long timeoutTimer;
    private long addBallInterval;
    private long addBallTimer;
    private long addBallFactor;

    public GameSurfaceView(Context context, float density, boolean startnew,
			   GameFragment fragment) {
	super(context);

	Debug.log(TAG, "GameSurfaceView density=" + density);

	listener = (GameFragment.Listener)context;
	this.fragment = fragment;
	this.context = context;
	this.density = density;
	holder = getHolder();
	holder.addCallback(this);
	rnd = new Random();
	vibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);

	prefs = PreferenceManager.getDefaultSharedPreferences(context);
	makeSound = prefs.getBoolean("pref_sound_enable", true);
	makeVibration = prefs.getBoolean("pref_vibrate_enable", true);
	makeFullscreen = prefs.getBoolean("pref_fullscreen_enable", true);

	typeface = Typeface.createFromAsset(context.getAssets(),
					    "fonts/Roboto-Regular.ttf");
	ballPaint = new Paint();
	ballPaint.setAntiAlias(true);
	ballPaint.setStyle(Paint.Style.FILL_AND_STROKE);
	ballPaint.setTextAlign(Paint.Align.CENTER);
	ballPaint.setTypeface(typeface);

	headerTextPaint = new Paint();
	headerTextPaint.setAntiAlias(true);
	headerTextPaint.setTypeface(typeface);
	headerTextPaint.setTextSize(24.0f * density);
	headerTextPaint.setARGB(255, 80, 80, 80);

	headerMonoPaint = new Paint();
	headerMonoPaint.setAntiAlias(true);
	headerMonoPaint.setTypeface(Typeface.MONOSPACE);
	headerMonoPaint.setTextSize(24.0f * density);
	headerMonoPaint.setStyle(Paint.Style.FILL_AND_STROKE);
	headerMonoPaint.setStrokeWidth(1.0f * density);
	headerMonoPaint.setARGB(255, 0, 0, 0);

	timeLinePaint = new Paint();
	timeLinePaint.setAntiAlias(true);
	timeLinePaint.setStyle(Paint.Style.STROKE);
	timeLinePaint.setStrokeWidth(3.0f * density);
	timeLinePaint.setARGB(255, 200, 50, 50);

	removeLinePaint = new Paint();
	removeLinePaint.setAntiAlias(true);
	removeLinePaint.setStyle(Paint.Style.STROKE);
	removeLinePaint.setStrokeWidth(3.0f * density);
	removeLinePaint.setARGB(255, 50, 50, 200);

	levelPaint = new Paint();
	levelPaint.setAntiAlias(true);
	levelPaint.setTypeface(typeface);
	levelPaint.setStyle(Paint.Style.FILL_AND_STROKE);
	levelPaint.setTextSize(240.0f * density);
	levelPaint.setStrokeWidth(1.0f * density);
	levelPaint.setTextAlign(Paint.Align.CENTER);
	levelPaint.setARGB(255, 192, 192, 192);

	pausePaint = new Paint();
	pausePaint.setAntiAlias(true);
	pausePaint.setStyle(Paint.Style.FILL_AND_STROKE);
	pausePaint.setStrokeWidth(1.0f * density);
	pausePaint.setARGB(255, 0, 0, 0);

	ballSize = 32.0f * density;
	removeLinePosY = 1.0f * density;
	timeLinePosY = 4.0f * density;

	resetGame(startnew);
    }

    public void resetGame(boolean startnew) {
	targetSum = 0;
	pressedSum = 0;
	score = 0;
	level = 0;
	timeoutTimer = 0;
	addBallTimer = 0;
	removeTotal = 0;
	removeLevel = 0;
	gameType = listener.getGameType();

	paused = false;
	pauseShowing = false;
	gameOver = false;

	if(gotSurfaceChanged) {
	    synchronized(holder) {
		ballList.clear();
		removedBallList.clear();
	    }
	}

	if(!startnew)
	    restoreState();

	setLevelValues();

	// Fill up ball list
	if(gotSurfaceChanged) {
	    synchronized(holder) {
		while(addBall())
		    ;
	    }
	}

	fragment.updateActionBar(targetSum, level, pressedSum, score, gameType);
    }

    private void setLevelValues() {
	switch(gameType) {
	case MainActivity.DIFFICULTY_EASY:
	    removePerLevel = 10 + level * 10;
	    targetBalls = 1 + level;
	    if(targetBalls > 4)
		targetBalls = 4;
	    timeoutInterval = 15000 - level * 1000;
	    if(timeoutInterval < 5000)
		timeoutInterval = 5000;
	    addBallFactor = 250 - level * 20;
	    if(addBallFactor < 80)
		addBallFactor = 80;
	    if(level < 4)
		maxBallShowing = 10;
	    else if(level < 6)
		maxBallShowing = 11;
	    else
		maxBallShowing = 12;
	    break;

	case MainActivity.DIFFICULTY_NORMAL:
	    removePerLevel = 20 + level * 10;
	    targetBalls = 2 + level;
	    if(targetBalls > 5)
		targetBalls = 5;
	    timeoutInterval = 14000 - level * 1000;
	    if(timeoutInterval < 5000)
		timeoutInterval = 5000;
	    addBallFactor = 250 - level * 20;
	    if(addBallFactor < 80)
		addBallFactor = 80;
	    if(level < 5)
		maxBallShowing = 10;
	    else if(level < 7)
		maxBallShowing = 11;
	    else
		maxBallShowing = 12;
	    break;

	case MainActivity.DIFFICULTY_HARD:
	    removePerLevel = 30 + level * 10;
	    targetBalls = 3 + level;
	    if(targetBalls > 6)
		targetBalls = 6;
	    timeoutInterval = 12000 - level * 1000;
	    if(timeoutInterval < 4000)
		timeoutInterval = 4000;
	    addBallFactor = 250 - level * 20;
	    if(addBallFactor < 80)
		addBallFactor = 80;
	    if(level < 5)
		maxBallShowing = 10;
	    else if(level < 6)
		maxBallShowing = 11;
	    else
		maxBallShowing = 12;
	    break;
	}
    }

    private void playSoundDrop() {
	if(!makeSound)
	    return;

	int snd = rnd.nextInt(MainActivity.SOUND_DROP_NUM) +
	    MainActivity.SOUND_DROP_FIRST;
	listener.doPlaySound(snd);
    }

    private void playSoundPop() {
	if(!makeSound)
	    return;

	int snd = rnd.nextInt(MainActivity.SOUND_POP_NUM) +
	    MainActivity.SOUND_POP_FIRST;
	listener.doPlaySound(snd);
    }

    private boolean addBall() {
	if(!gotSurfaceChanged)
	    return true;

	if(ballList.size() >= GAME_MAX_BALLS)
	    return false;

	float minX, maxX, minY, maxY;
	float origX, origY, x, y;

	minX = ballSize * 2.0f;
	maxX = surfaceWidth - ballSize * 2.0f;
	minY = ballSize * 2.0f + timeLinePosY;
	maxY = surfaceHeight - ballSize * 2.0f;

	origX = x = rnd.nextFloat() * (maxX - minX) + minX;
	origY = y = rnd.nextFloat() * (maxY - minY) + minY;

	// Check if we're too close to another ball.
	boolean tooClose;
	boolean wrapY = false;
	float closeFactor = 3.1f;
	do {
	    tooClose = false;
	    for(Ball ball : ballList) {
		if(checkTouchBall(ball, x, y, closeFactor, true)) {
		    tooClose = true;
		    break;
		}
	    }

	    if(tooClose) {
		x += ballSize;
		if(x > maxX) {
		    x = minX;
		    y += ballSize;
		    if(!wrapY && y > maxY) {
			y = minY;
			wrapY = true;
		    } else if(wrapY && y > origY) {
			closeFactor -= 0.5f;
			if(closeFactor < 2.0f)
			    return false;
			x = origX;
			y = origY;
			wrapY = false;
		    }
		}
	    }
	} while(tooClose);

	int num = rnd.nextInt(9) + 1;
	int type;
	if(num >= 8)
	    type = Ball.BLUE;
	else if(num >= 5)
	    type = Ball.RED;
	else
	    type = Ball.GREEN;

	Debug.log(TAG, "New ball at x=" + x + ", y=" + y +
	      ", closeFactor=" + closeFactor);

	ballList.add(new Ball(x, y, ballSize, num, type));

	return true;
    }

    // Called on update thread.
    private void showBall() {
	int cnt = 0;

	for(Ball ball : ballList) {
	    if(ball.state == Ball.STATE_HIDDEN) {
		ball.state = Ball.STATE_SHOWING;
		handler.post(new Runnable() {
			public void run() {
			    playSoundDrop();
			}});
		return;
	    } else {
		cnt++;
		if(cnt > maxBallShowing)
		    return;
	    }
	}
    }

    // Called on update thread.
    private int ballListCountShowing() {
	int cnt = 0;

	for(Ball ball : ballList) {
	    if(ball.state == Ball.STATE_SHOWN ||
	       ball.state == Ball.STATE_SHOWING)
		cnt++;
	}

	return cnt;
    }

    // Called on update thread.
    private BallScore removePressedBalls() {
	BallScore ballScore = new BallScore();
	long delay = (long)rnd.nextInt(GAME_MAX_REMOVE_DELAY);

	Iterator it = ballList.iterator();
	while(it.hasNext()) {
	    Ball ball = (Ball)it.next();
	    if(!ball.pressed)
		continue;

	    it.remove();
	    ball.delay = delay;
	    ball.state = Ball.STATE_REMOVING;
	    ball.changeSize = ball.size * 1.5f;
	    ballScore.inc(ball.type);
	    handler.postDelayed(new Runnable() {
		    public void run() {
			playSoundPop();
		    }}, delay);
	    removedBallList.add(ball);
	}

	return ballScore;
    }

    private int calcPressedSum() {
	int sum = 0;

	for(Ball ball : ballList) {
	    if(ball.pressed)
		sum += ball.number;
	}

	return sum;
    }

    // Called from DrawThread, inside synchronized(holder)
    public void updatePhysics(long time) {
	long diff = 0;

	if(prevUpdateTime > 0)
	    diff = time - prevUpdateTime;
	prevUpdateTime = time;

	if(paused || gameOver)
	    return;

	timeoutTimer += diff;
	if(timeoutTimer > timeoutInterval) {
	    handler.post(new Runnable() {
		    public void run() {
			String gts = listener.getGameTypeString(gameType);
			showGameOver();
		    }});
	    gameOver = true;
	    return;
	}

	addBallInterval = ballListCountShowing() * addBallFactor;

	addBallTimer += diff;
	if(addBallTimer > addBallInterval) {
	    showBall();
	    addBallTimer = 0;
	}

	// Handle balls about to show up.
	for(Ball ball : ballList) {
	    if(ball.state != Ball.STATE_SHOWING)
		continue;

	    ball.changeSize += ball.size * (float)diff / 150.0f;
	    if(ball.changeSize >= ball.size) {
		ball.state = Ball.STATE_SHOWN;
	    }
	}

	// Handle balls that are being removed.
	Iterator it = removedBallList.iterator();
	while(it.hasNext()) {
	    Ball ball = (Ball)it.next();
	    if(ball.state != Ball.STATE_REMOVING)
		continue;

	    ball.delay -= diff;
	    if(ball.delay <= 0) {
		float factor = 0.05f * diff;
		if(factor <= 1.0f)
		    factor = 1.05f;
		ball.changeSize *= factor;
		ball.changeAlpha -= 256 * diff / 100.0f;
		if(ball.changeAlpha <= 0) {
		    it.remove();
		}
	    }
	}

	pressedSum = calcPressedSum();
	if(targetSum > 0 && pressedSum == targetSum) {
	    // Scoring:
	    // ballscore (1-3) * removed
	    // Double if removing all shown balls.

	    BallScore ballScore = removePressedBalls();
	    if(ballListCountShowing() == 0)
		ballScore.score *= 2;

	    score += ballScore.score * ballScore.removed;
	    targetSum = 0;
	    timeoutTimer = 0;

	    removeLevel += ballScore.removed;
	    removeTotal += ballScore.removed;
	    if(removeLevel > removePerLevel) {
		level++;
		removeLevel -= removePerLevel;
		setLevelValues();
	    }

	    if(ballScore.removed == 4)
		unlockAchievement(R.string.achievement_fantastic_four);
	    if(ballScore.removed == 5)
		unlockAchievement(R.string.achievement_high_five);
	    if(ballScore.removed == 6)
		unlockAchievement(R.string.achievement_sixth_sense);
	    if(ballScore.removed == 7)
		unlockAchievement(R.string.achievement_lucky_seven);
	    if(ballScore.removed == 8)
		unlockAchievement(R.string.achievement_magic_eight);
	    if(ballScore.removed == 9)
		unlockAchievement(R.string.achievement_nine_lives);
	    if(ballScore.removed >= 10)
		unlockAchievement(R.string.achievement_hang_ten);

	    if(removeTotal >= 100)
		unlockAchievement(R.string.achievement_100_balls_in_one_game);
	    if(removeTotal >= 200)
		unlockAchievement(R.string.achievement_200_balls_in_one_game);
	    if(removeTotal >= 500)
		unlockAchievement(R.string.achievement_500_balls_in_one_game);

	    if(makeVibration)
		vibrator.vibrate(50);
	}

	// Fill up ball list
	while(addBall())
	    ;

	// Set new targetSum, based on shown or next balls.
	if(targetSum == 0) {
	    int numShown = 0;

	    for(Ball ball : ballList) {
		if(ball.state == Ball.STATE_SHOWN ||
		   ball.state == Ball.STATE_SHOWING)
		    numShown++;
	    }

	    int off = rnd.nextInt(numShown + 2);
	    if(targetBalls - 1 + off >= ballList.size())
		off = 0;
	    boolean mod = false;

	    if(numShown >= targetBalls + 1) {
		mod = true;
	    }
	    for(int i = 0 ; i < targetBalls ; i++) {
		Ball ball;

		Debug.log(TAG, "test i=" + i + ", targetBalls=" + targetBalls +
		      ", mod=" + mod + ", off=" + off +
		      ", size=" + ballList.size());

		if(mod)
		    ball = ballList.get((i + off) % numShown);
		else
		    ball = ballList.get(i + off);

		targetSum += ball.number;
		Debug.log(TAG, "selected i=" + i + ", targetBalls=" + targetBalls +
		      ", mod=" + mod + ", off=" + off +
		      ", size=" + ballList.size() + ", ballnum=" +
		      ball.number + ", targetSum=" + targetSum +
		      ", ballstate=" + ball.state);
	    }
	}

	handler.post(new Runnable() {
		public void run() {
		    fragment.updateActionBar(targetSum, level,
					     pressedSum, score,
					     gameType);
		}});
    }

    private void unlockAchievement(final int id) {
	handler.post(new Runnable() {
		public void run() {
		    listener.doUnlockAchievement(id);
		}});
    }

    private boolean checkTouchBall(Ball ball, float x, float y,
				   float marginFactor, boolean checkall) {
	if(!checkall && ball.state != Ball.STATE_SHOWN)
	    return false;

	float dx = x - ball.x;
	float dy = y - ball.y;
	float dist = dx * dx + dy * dy;
	float checkdist = ball.size * marginFactor;
	checkdist *= checkdist;

	/*
	Debug.log(TAG, "checkTouchBall touch=(" + x + "," + y +
	      "), ball=(" + ball.x + "," + ball.y + "), " +
	      "dist=" + dist + ", checkdist=" + checkdist);
	*/

	if(dist < checkdist)
	    return true;

	return false;
    }

    // Must use synchronized(holder) when using the same
    // variables as in updatePhysics and onDraw.
    @Override
    public boolean onTouchEvent(MotionEvent event) {
	int action = event.getActionMasked();
	int pointerCount = event.getPointerCount();
	int actionIndex = -1;

	if(action == MotionEvent.ACTION_POINTER_DOWN) {
	    actionIndex = event.getActionIndex();
	    action = MotionEvent.ACTION_DOWN;
	} else if(action == MotionEvent.ACTION_POINTER_UP) {
	    actionIndex = event.getActionIndex();
	    action = MotionEvent.ACTION_UP;
	}

	if(actionIndex == -1) {
	    synchronized(holder) {
		for(Ball ball : ballList) {
		    ball.pressed = false;
		}
	    }
	}

	for(int i = 0 ; i < pointerCount ; i++) {
	    int pointerId = event.getPointerId(i);
	    float x = event.getX(i);
	    float y = event.getY(i);

	    if(actionIndex >= 0 && actionIndex != i)
		continue;

	    synchronized(holder) {
		for(Ball ball : ballList) {
		    if(checkTouchBall(ball, x, y, 1.2f, false)) {
			if(action == MotionEvent.ACTION_DOWN ||
			   action == MotionEvent.ACTION_MOVE)
			    ball.pressed = true;
			else if(action == MotionEvent.ACTION_UP ||
				action == MotionEvent.ACTION_CANCEL)
			    ball.pressed = false;
			break;
		    }
		}
	    }
	}

	return true;
    }

    private void drawLevel(Canvas canvas) {
	canvas.drawText(String.valueOf(level),
			surfaceWidth / 2.0f,
			surfaceHeight / 2.0f -
			((levelPaint.descent() +
			  levelPaint.ascent()) / 2.0f) +
			timeLinePosY,
			levelPaint);
    }

    private void drawLevelLine(Canvas canvas) {
	float length =
	    surfaceWidth *
	    ((float)removeLevel / (float)removePerLevel);

	canvas.drawLine(0, removeLinePosY, length, removeLinePosY,
			removeLinePaint);
    }

    private void drawTimeLine(Canvas canvas) {
	float length =
	    surfaceWidth *
	    (1.0f - ((float)timeoutTimer / (float)timeoutInterval));

	canvas.drawLine(0, timeLinePosY, length, timeLinePosY,
			timeLinePaint);
    }

    private void drawBall(Canvas canvas, Ball ball) {
	if(ball.state == Ball.STATE_HIDDEN)
	    return;

	float size = ball.size;
	int alpha = 255;
	if(ball.state == Ball.STATE_SHOWING) {
	    size = ball.changeSize;
	} else if(ball.state == Ball.STATE_REMOVING) {
	    size = ball.changeSize;
	    alpha = ball.changeAlpha;
	} else if(ball.pressed) {
	    size = ball.size * 1.5f;
	}

	if(!paused)
	    ballPaint.setARGB(alpha, ball.r, ball.g, ball.b);
	else
	    ballPaint.setARGB(alpha, 160, 160, 160);

	canvas.drawCircle(ball.x, ball.y, size, ballPaint);

	if(!paused) {
	    ballPaint.setTextSize(size * 0.75f);
	    ballPaint.setARGB(alpha, 0, 0, 0);
	    canvas.drawText(ball.numText,
			    ball.x,
			    ball.y - ((ballPaint.descent() +
				       ballPaint.ascent()) / 2.0f),
			    ballPaint);
	}
    }

    @Override
    public void onDraw(Canvas canvas) {
	if(canvas == null)
	    return;

	canvas.drawColor(Color.WHITE);

	drawLevel(canvas);
	drawLevelLine(canvas);
	drawTimeLine(canvas);

	for(Ball ball : ballList) {
	    drawBall(canvas, ball);
	}
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format,
			       int width, int height) {
	Debug.log(TAG, "surfaceChanged width=" + width + ", height=" + height);

	gotSurfaceChanged = true;

	surfaceWidth = width;
	surfaceHeight = height;

	prevUpdateTime = 0;

	// Fill up ball list
	synchronized(holder) {
	    while(addBall())
		;
	}
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
	Debug.log(TAG, "surfaceCreated");

	if(thread == null || !thread.isRunning()) {
	    thread = new DrawThread(holder, handler, this);
	    thread.setRunning(true);
	    thread.start();
	}
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
	Debug.log(TAG, "surfaceDestroyed");

	boolean retry = true;
	thread.setRunning(false);
	while(retry) {
	    try {
		thread.join();
		retry = false;
	    } catch(InterruptedException e) {
	    }
	}
	thread = null;
    }

    public void onPause() {
	Debug.log(TAG, "onPause");
	listener.doStopSoundAll();
	if(thread != null)
	    thread.setPaused(true);
	if(gameOver) {
	    removeState();
	} else {
	    paused = true;
	    saveState();
	}
    }

    public void onResume() {
	Debug.log(TAG, "onResume");

	if(thread != null)
	    thread.setPaused(false);

	if(makeFullscreen) {
	    if(Build.VERSION.SDK_INT >= 19) {
		setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
				      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		Activity act = (Activity)context;
		act.getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
			@Override
			public void onSystemUiVisibilityChange(int visibility) {
			    if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
				setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
						      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
			    }
			}
		    });
	    }
	}

	if(paused && !pauseShowing)
	    showPause();
    }


    public void doPause() {
	paused = true;
	showPause();
    }

    private void showPause() {
	AlertDialog.Builder builder =
	    new AlertDialog.Builder(context);
	builder.setTitle(R.string.pause_title);
	builder.setIcon(R.drawable.ic_home);
	builder.setCancelable(false);
	builder.setNegativeButton(R.string.pause_exit,
				  new DialogInterface.OnClickListener() {
				      public void onClick(DialogInterface dialog, int id) {
					  listener.doBackFragment();
				      }
				  });
	builder.setPositiveButton(R.string.pause_resume,
				  new DialogInterface.OnClickListener() {
				      public void onClick(DialogInterface dialog, int id) {
					  paused = false;
					  onResume();
					  dialog.cancel();
					  pauseShowing = false;
				      }
				  });
	builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
		public boolean onKey(DialogInterface dialog,
				     int keyCode, KeyEvent event) {
		    if(event.getAction() == KeyEvent.ACTION_DOWN &&
		       keyCode == KeyEvent.KEYCODE_BACK) {
			dialog.cancel();
			listener.doBackFragment();
			return true;
		    }
		    return false;
		}
	    });
	AlertDialog alert = builder.create();
	alert.show();
	pauseShowing = true;
    }

    private void showGameOver() {
	saveHighScore();

	Debug.log(TAG, "showGameOver");

	LayoutInflater inflater =
	    (LayoutInflater)context
	    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	AlertDialog.Builder builder =
	    new AlertDialog.Builder(context);
	builder.setTitle(R.string.gameover_title);
	View view = inflater.inflate(R.layout.gameover, null);
	TextView tv;
	tv = (TextView)view.findViewById(R.id.gameover_score);
	tv.setText(String.valueOf(score));
	tv = (TextView)view.findViewById(R.id.gameover_level);
	tv.setText(String.valueOf(level));
	tv = (TextView)view.findViewById(R.id.gameover_removed);
	tv.setText(String.valueOf(removeTotal));
	tv = (TextView)view.findViewById(R.id.gameover_highscore);
	int highscore = getHighScore();
	tv.setText(String.valueOf(highscore));

	builder.setView(view);
	builder.setIcon(R.drawable.ic_home);
	builder.setCancelable(false);
	builder.setNegativeButton(R.string.gameover_button_share,
				  new DialogInterface.OnClickListener() {
				      public void onClick(DialogInterface dialog, int id) {
				      }
				  });
	builder.setNeutralButton(R.string.gameover_button_highscores,
				 new DialogInterface.OnClickListener() {
				     public void onClick(DialogInterface dialog, int id) {
				     }
				 });
	builder.setPositiveButton(R.string.gameover_button_newgame,
				  new DialogInterface.OnClickListener() {
				      public void onClick(DialogInterface dialog, int id) {
					  dialog.cancel();
					  listener.doStartGame(true);
				      }
				  });
	builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
		public boolean onKey(DialogInterface dialog,
				     int keyCode, KeyEvent event) {
		    if(event.getAction() == KeyEvent.ACTION_DOWN &&
		       keyCode == KeyEvent.KEYCODE_BACK) {
			dialog.cancel();
			listener.doBackFragment();
			return true;
		    }
		    return false;
		}
	    });
	AlertDialog alert = builder.create();
	alert.show();
	final View vAlert = alert.getWindow().getDecorView();
	Button button;
	button = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
	button.setOnClickListener(new View.OnClickListener() {
		public void onClick(View v) {
		    handler.postDelayed(new Runnable() {
			    public void run() {
				Uri screenshot = createScreenshot(vAlert);
				Intent sendIntent = new Intent();
				sendIntent.setAction(Intent.ACTION_SEND);
				sendIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject));
				String[] gameTypeArray =
				    fragment.getResources().
				    getStringArray(R.array.gametype_array);
				String text = String.format(context.getString(R.string.share_text), score, gameTypeArray[gameType]);
				sendIntent.putExtra(Intent.EXTRA_TEXT, text);
				if(screenshot != null)
				    sendIntent.putExtra(Intent.EXTRA_STREAM, screenshot);
				sendIntent.setType("*/*");
				context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_title)));
			    }}, 500);
		}
	    });
	button = alert.getButton(AlertDialog.BUTTON_NEUTRAL);
	button.setOnClickListener(new View.OnClickListener() {
		public void onClick(View v) {
		    listener.doShowLeaderboard(gameType);
		}
	    });
    }

    private static String prefEncrypt(String seed, String valueString) {
	String cryptoPass = "prefValue" + seed;

	try {
	    DESKeySpec keySpec = new DESKeySpec(cryptoPass.getBytes("UTF8"));
	    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
	    SecretKey key = keyFactory.generateSecret(keySpec);

	    byte[] clearText = valueString.getBytes("UTF8");
	    Cipher cipher = Cipher.getInstance("DES");
	    cipher.init(Cipher.ENCRYPT_MODE, key);

	    String encryptedValue =
		Base64.encodeToString(cipher.doFinal(clearText),
				      Base64.DEFAULT);

	    return encryptedValue;
	} catch (Exception e) {
	}

	return null;
    }

    private static String prefDecrypt(String seed, String valueString) {
	String cryptoPass = "prefValue" + seed;

	try {
	    DESKeySpec keySpec = new DESKeySpec(cryptoPass.getBytes("UTF8"));
	    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
	    SecretKey key = keyFactory.generateSecret(keySpec);

	    byte[] encryptedPwdBytes =
		Base64.decode(valueString, Base64.DEFAULT);
	    Cipher cipher = Cipher.getInstance("DES");
	    cipher.init(Cipher.DECRYPT_MODE, key);
	    byte[] decryptedValueBytes = (cipher.doFinal(encryptedPwdBytes));

	    return new String(decryptedValueBytes);
	} catch (Exception e) {
	}

	return null;
    }

    private static String prefIntEncrypt(String seed, int value) {
	return prefEncrypt(seed, String.valueOf(value));
    }

    private static String prefLongEncrypt(String seed, long value) {
	return prefEncrypt(seed, String.valueOf(value));
    }

    private static int prefIntDecrypt(String seed, String valueString) {
	String str = prefDecrypt(seed, valueString);
	if(str == null)
	    return 0;
	else
	    return Integer.parseInt(str);
    }

    private static long prefLongDecrypt(String seed, String valueString) {
	String str = prefDecrypt(seed, valueString);
	if(str == null)
	    return 0;
	else
	    return Long.parseLong(str);
    }

    public static int getStateLevel(Context context) {
	SharedPreferences prefs =
	    PreferenceManager.getDefaultSharedPreferences(context);

	String str = prefs.getString("game_level", null);
	if(str != null)
	    return prefIntDecrypt("gl", str);

	return -1;
    }

    public static int getStateType(Context context) {
	SharedPreferences prefs =
	    PreferenceManager.getDefaultSharedPreferences(context);

	String str = prefs.getString("game_type", null);
	if(str != null)
	    return prefIntDecrypt("gt", str);

	return MainActivity.DIFFICULTY_NORMAL;
    }

    private void saveState() {
	SharedPreferences.Editor editor = prefs.edit();

	editor.putString("game_target_sum",
			 prefIntEncrypt("gts", targetSum));
	editor.putString("game_score",
			 prefIntEncrypt("gs", score));
	editor.putString("game_remove_total",
			 prefIntEncrypt("grt", removeTotal));
	editor.putString("game_remove_level",
			 prefIntEncrypt("grl", removeLevel));
	editor.putString("game_level",
			 prefIntEncrypt("gl", level));
	editor.putString("game_timeout_timer",
			 prefLongEncrypt("gtt", timeoutTimer));
	editor.putString("game_type",
			 prefIntEncrypt("gt", gameType));
	int i = 0;
	for(Ball ball : ballList) {
	    editor.putString("game_ball_" + i,
			     prefEncrypt("gb", ball.serialize()));
	    i++;
	}
	editor.commit();
    }

    private void restoreState() {
	String str;

	str = prefs.getString("game_target_sum", null);
	if(str != null)
	    targetSum = prefIntDecrypt("gts", str);
	str = prefs.getString("game_score", null);
	if(str != null)
	    score = prefIntDecrypt("gs", str);
	str = prefs.getString("game_remove_total", null);
	if(str != null)
	    removeTotal = prefIntDecrypt("grt", str);
	str = prefs.getString("game_remove_level", null);
	if(str != null)
	    removeLevel = prefIntDecrypt("grl", str);
	str = prefs.getString("game_level", null);
	if(str != null)
	    level = prefIntDecrypt("gl", str);
	str = prefs.getString("game_timeout_timer", null);
	if(str != null)
	    timeoutTimer = prefLongDecrypt("gtt", str);
	str = prefs.getString("game_type", null);
	if(str != null)
	    gameType = prefIntDecrypt("gt", str);

	for(int i = 0 ; i < GAME_MAX_BALLS ; i++) {
	    str = prefs.getString("game_ball_" + i, null);
	    if(str == null)
		continue;
	    String ballStr = prefDecrypt("gb", str);
	    if(ballStr == null)
		continue;
	    ballList.add(new Ball(ballStr));
	}
    }

    private void removeState() {
	SharedPreferences.Editor editor = prefs.edit();

	editor.remove("game_target_sum");
	editor.remove("game_score");
	editor.remove("game_remove_total");
	editor.remove("game_remove_level");
	editor.remove("game_level");
	editor.remove("game_timeout_timer");
	editor.remove("game_type");
	for(int i = 0 ; i < GAME_MAX_BALLS ; i++) {
	    editor.remove("game_ball_" + i);
	}
	editor.commit();
    }

    private void saveHighScore() {
        listener.doSubmitHighscore(score, gameType);

	int highscore = 0;

	String str = prefs.getString("game_highscore", null);
	if(str != null)
	    highscore = prefIntDecrypt("ghs", str);

	if(score > highscore) {
	    SharedPreferences.Editor editor = prefs.edit();
	    editor.putString("game_highscore",
			     prefIntEncrypt("ghs", score));
	    editor.commit();
	}
    }

    private int getHighScore() {
	int highscore = 0;

	String str = prefs.getString("game_highscore", null);
	if(str != null)
	    highscore = prefIntDecrypt("ghs", str);

	return highscore;
    }

    private Uri createScreenshot(View v) {
	// Make screenshot
	v.setDrawingCacheEnabled(true);
	Bitmap bitmap = Bitmap.createBitmap(v.getDrawingCache());
	v.setDrawingCacheEnabled(false);

	// Store as file
	ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	bitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes);
	File f = new File(Environment.getExternalStorageDirectory()
			  + File.separator + "mathballs_sharescore.png");
	try {
	    f.createNewFile();
	    FileOutputStream fo = new FileOutputStream(f);
	    fo.write(bytes.toByteArray());
	    fo.close();
	} catch(IOException e) {
	    return null;
	}

	return Uri.fromFile(f);
    }
}
