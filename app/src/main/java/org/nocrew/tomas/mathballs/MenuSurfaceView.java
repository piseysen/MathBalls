package org.nocrew.tomas.mathballs;

import java.util.Random;
import android.os.Handler;
import android.content.Context;
import android.content.Intent;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.Toast;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.content.DialogInterface;

import org.nocrew.tomas.mathballs.R;

public class MenuSurfaceView extends MySurfaceView
    implements SurfaceHolder.Callback {
    private final static String TAG = "MB-MenuSV";

    private MenuFragment.Listener listener;
    private MenuFragment fragment;
    private Context context;
    private DrawThread thread;
    private Handler handler = new Handler();

    private int surfaceWidth;
    private int surfaceHeight;
    private float density;

    private long prevUpdateTime;

    private float circlePosX[] = new float[6];
    private float circlePosY[] = new float[6];
    private float circleSize;
    private float changeSize;
    private int changeAlpha;
    private float textPosX[] = new float[6];
    private float textPosY[] = new float[6];
    private float textSize;
    private Paint.Align textAlign;
    private Typeface typeface;
    private Paint menuPaint;
    private Paint resumePaint;
    private Paint levelPaint;
    private Path menuPath;

    private final static float SMALL_FACTOR = 0.5f;

    private Random rnd;
    private int resumeLevel = -1;
    private int resumeType = MainActivity.DIFFICULTY_NORMAL;
    private int resumeCircleType[] = new int[5];
    private float resumeCircleX[] = new float[5];
    private float resumeCircleY[] = new float[5];

    private final static int MENU_NONE           = -1;
    private final static int MENU_RESUME         = 0;
    private final static int MENU_STARTNEW       = 1;
    private final static int MENU_ACHIEVEMENTS   = 2;
    private final static int MENU_LEADERBOARDS   = 3;
    private final static int MENU_SETTINGS       = 4;
    private final static int MENU_RATE           = 5;
    private int selectCircle = MENU_NONE;
    private int selected = MENU_NONE;

    public MenuSurfaceView(Context context, float density,
			   MenuFragment fragment) {
	super(context);

	listener = (MenuFragment.Listener)context;
	this.fragment = fragment;
	this.context = context;
	this.density = density;
	getHolder().addCallback(this);
	typeface = Typeface.createFromAsset(context.getAssets(),
					    "fonts/Roboto-Regular.ttf");
	resumeLevel = GameSurfaceView.getStateLevel(context);
	resumeType = GameSurfaceView.getStateType(context);
	rnd = new Random();

	menuPaint = new Paint();
	menuPaint.setAntiAlias(true);
	menuPaint.setStyle(Paint.Style.FILL_AND_STROKE);
	menuPaint.setTypeface(typeface);

	resumePaint = new Paint();
	resumePaint.setAntiAlias(true);
	resumePaint.setStyle(Paint.Style.STROKE);
	resumePaint.setStrokeWidth(4.0f * density);

	levelPaint = new Paint();
	levelPaint.setAntiAlias(true);
	levelPaint.setTypeface(typeface);
	levelPaint.setStyle(Paint.Style.FILL_AND_STROKE);
	levelPaint.setStrokeWidth(1.0f * density);
	levelPaint.setTextAlign(Paint.Align.CENTER);
	levelPaint.setARGB(255, 192, 192, 192);

	randomizeResumeCircles();

	menuPath = new Path();
    }

    private void randomizeResumeCircles() {
	for(int i = 0 ; i < 5 ; i++) {
	    resumeCircleType[i] = rnd.nextInt(3);
	    resumeCircleX[i] = (rnd.nextFloat() - 0.5f) * 1.4f;
	    resumeCircleY[i] = (rnd.nextFloat() - 0.5f) * 1.4f;
	}
    }

    // Called from DrawThread, inside synchronized(holder)
    public void updatePhysics(long time) {
	long diff = 0;

	if(prevUpdateTime > 0)
	    diff = time - prevUpdateTime;
	prevUpdateTime = time;

	if(selected != MENU_NONE) {
	    float factor = 0.05f * diff;
	    if(factor <= 1.0f)
		factor = 1.05f;
	    changeSize *= factor;
	    changeAlpha -= 256 * diff / 200.0f;
	    if(changeAlpha < 0) {
		changeAlpha = 0;
		selected = MENU_NONE;
	    }
	}
    }

    private int checkCirclePos(float x, float y) {
	for(int i = MENU_RESUME ; i <= MENU_RATE ; i++) {
	    if(i == MENU_RESUME && resumeLevel == -1)
		continue;
	    float factor = (i >= MENU_SETTINGS ? SMALL_FACTOR : 1.0f);
	    if(x > circlePosX[i] - circleSize * factor * 1.1f &&
	       x < circlePosX[i] + circleSize * factor * 1.1f &&
	       y > circlePosY[i] - circleSize * factor * 1.1f &&
	       y < circlePosY[i] + circleSize * factor * 2.0f)
		return i;
	}

	return MENU_NONE;
    }

    // Must use synchronized(holder) when using the same
    // variables as in updatePhysics and onDraw.
    @Override
    public boolean onTouchEvent(MotionEvent event) {
	int action = event.getActionMasked();
	float x = event.getX();
	float y = event.getY();
	int sel = checkCirclePos(x, y);

	synchronized(getHolder()) {
	    if(action == MotionEvent.ACTION_DOWN) {
		selectCircle = sel;
	    } else if(action == MotionEvent.ACTION_MOVE) {
		if(selectCircle != MENU_NONE &&
		   selectCircle != sel) {
		    selectCircle = MENU_NONE;
		    changeSize = 0.0f;
		}
	    } else if(action == MotionEvent.ACTION_UP) {
		if(selectCircle != MENU_NONE) {
		    selected = selectCircle;
		    changeSize = circleSize * 1.1f;
		    changeAlpha = 255;
		    switch(selectCircle) {
		    case MENU_RESUME:
			String gts =
			    listener.getGameTypeString(GameSurfaceView.getStateType(context));
			listener.doStartGame(false);
			break;
		    case MENU_STARTNEW:
			if(resumeLevel >= 0) {
			    showConfirmNew();
			} else {
			    String gtsa =
				listener.getGameTypeString(listener.getGameType());
			    listener.doStartGame(true);
			}
			break;
		    case MENU_ACHIEVEMENTS:
			listener.doShowAchievements();
			break;
		    case MENU_LEADERBOARDS:
			listener.doShowAllLeaderboards();
			break;
		    case MENU_SETTINGS:
			listener.doOpenSettings();
			break;
		    case MENU_RATE:
			listener.doOpenRate();
			break;
		    default:
		    }
		    selectCircle = MENU_NONE;
		}
	    }
	}

	return true;
    }

    private void showConfirmNew() {
	AlertDialog.Builder builder =
	    new AlertDialog.Builder(context);
	builder.setTitle(R.string.confirmnew_title);
	builder.setMessage(R.string.confirmnew_message);
	builder.setIcon(R.drawable.ic_home);
	builder.setCancelable(true);
	builder.setNegativeButton(R.string.confirmnew_cancel,
				  new DialogInterface.OnClickListener() {
				      public void onClick(DialogInterface dialog, int id) {
					  dialog.cancel();
				      }
				  });
	builder.setPositiveButton(R.string.confirmnew_ok,
				  new DialogInterface.OnClickListener() {
				      public void onClick(DialogInterface dialog, int id) {
					  listener.doStartGame(true);
					  dialog.cancel();
				      }
				  });
	builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
		public void onCancel(DialogInterface dialog) {
		    dialog.cancel();
		}
	    });
	AlertDialog alert = builder.create();
	alert.show();
    }

    @Override
    public void onDraw(Canvas canvas) {
	if(canvas == null)
	    return;

	canvas.drawColor(Color.WHITE);

	float size;
	int alpha;

	menuPaint.setTextSize(textSize);
	menuPaint.setTextAlign(textAlign);

	if(resumeLevel >= 0) {
	    levelPaint.setTextSize(circleSize * 0.47f * density);
	    canvas.drawText(String.valueOf(resumeLevel),
			    circlePosX[MENU_RESUME],
			    circlePosY[MENU_RESUME] -
			    ((levelPaint.descent() +
			      levelPaint.ascent()) / 2.0f),
			    levelPaint);

	    menuPaint.setARGB(255, 192, 192, 192);
	    String[] gameTypeArray =
		fragment.getResources().getStringArray(R.array.gametype_array);
	    canvas.drawText(String.valueOf(gameTypeArray[resumeType]),
			    circlePosX[MENU_RESUME],
			    circlePosY[MENU_RESUME] + circleSize * 0.45f -
			    (menuPaint.descent() + menuPaint.ascent()),
			    menuPaint);

	    size = circleSize * 0.118f;

	    for(int i = 0 ; i < 5 ; i++) {
		int c = resumeCircleType[i];
		float ox = resumeCircleX[i] * circleSize;
		float oy = resumeCircleY[i] * circleSize;
		menuPaint.setARGB(255,
				  c == 0 ? 255 : 100,
				  c == 1 ? 255 : 100,
				  c == 2 ? 255 : 100);

		canvas.drawCircle(circlePosX[MENU_RESUME] - ox,
				  circlePosY[MENU_RESUME] - oy,
				  size,
				  menuPaint);
	    }

	    if(selected == MENU_RESUME && changeSize != 0.0f) {
		size = changeSize;
		alpha = changeAlpha;
	    } else {
		size = (selectCircle == MENU_RESUME ?
			circleSize * 1.1f : circleSize);
		alpha = 255;
	    }
	    resumePaint.setARGB(alpha, 0, 0, 0);
	    canvas.drawCircle(circlePosX[MENU_RESUME],
			      circlePosY[MENU_RESUME],
			      size,
			      resumePaint);

	    menuPaint.setARGB(255, 0, 0, 0);
	    canvas.drawText(context.getString(R.string.menu_resume),
			    textPosX[MENU_RESUME],
			    textPosY[MENU_RESUME],
			    menuPaint);
	}

	if(selected == MENU_STARTNEW && changeSize != 0.0f) {
	    size = changeSize;
	    alpha = changeAlpha;
	} else {
	    size = (selectCircle == MENU_STARTNEW ?
		    circleSize * 1.1f : circleSize);
	    alpha = 255;
	}
	menuPaint.setARGB(alpha, 255, 100, 100);
	canvas.drawCircle(circlePosX[MENU_STARTNEW],
			  circlePosY[MENU_STARTNEW],
			  size,
			  menuPaint);

	size = circleSize / 2.0f;
	menuPath.rewind();
	menuPath.moveTo(circlePosX[MENU_STARTNEW] - size / 3.0f,
			circlePosY[MENU_STARTNEW] - 1.2f * size / 2.0f);
	menuPath.lineTo(circlePosX[MENU_STARTNEW] + 2.0f * size / 3.0f,
			circlePosY[MENU_STARTNEW]);
	menuPath.lineTo(circlePosX[MENU_STARTNEW] - size / 3.0f,
			circlePosY[MENU_STARTNEW] + 1.2f * size / 2.0f);
	menuPath.close();
	menuPaint.setARGB(255, 255, 255, 255);
	canvas.drawPath(menuPath, menuPaint);

	menuPaint.setARGB(255, 0, 0, 0);
	canvas.drawText(context.getString(R.string.menu_start_new),
			textPosX[MENU_STARTNEW],
			textPosY[MENU_STARTNEW],
			menuPaint);



	if(selected == MENU_ACHIEVEMENTS && changeSize != 0.0f) {
	    size = changeSize;
	    alpha = changeAlpha;
	} else {
	    size = (selectCircle == MENU_ACHIEVEMENTS ?
		    circleSize * 1.1f : circleSize);
	    alpha = 255;
	}
	menuPaint.setARGB(alpha, 100, 255, 100);
	canvas.drawCircle(circlePosX[MENU_ACHIEVEMENTS],
			  circlePosY[MENU_ACHIEVEMENTS],
			  size,
			  menuPaint);

	float cx = 254.0f;
	float cy = 193.0f;
	float cr = 74.0f;
	float ox = 0.0f;
	float oy = -63.0f;
	float factor = circleSize * 0.0025f;

	final float coords1[] = {
	    0, 220, 66,
	    0, 255, 81,
	    0, 290, 66,
	    0, 312, 96,
	    0, 349, 100,
	    0, 352, 137,
	    0, 384, 159,
	    0, 368, 195,
	    0, 384, 229,
	    0, 353, 250,
	    0, 349, 288,
	    0, 311, 292,
	    0, 289, 323,
	    0, 255, 306,
	    0, 221, 323,
	    0, 198, 292,
	    0, 161, 288,
	    0, 158, 251,
	    0, 126, 228,
	    0, 142, 194,
	    0, 126, 159,
	    0, 157, 137,
	    0, 161, 100,
	    0, 198, 96,
	};

	final float coords2[] = {
	    0, 198, 307,
	    0, 217, 334,
	    0, 255, 318,
	    0, 211, 447,
	    0, 154, 391,
	};

	final float coords3[] = {
	    0, 255, 318,
	    0, 292, 335,
	    0, 311, 308,
	    0, 356, 391,
	    0, 299, 446,
	};

	menuPaint.setARGB(255, 255, 255, 255);

	drawCoordsPath(canvas,
		       circlePosX[MENU_ACHIEVEMENTS],
		       circlePosY[MENU_ACHIEVEMENTS],
		       factor, cx, cy, ox, oy, coords1);

	drawCoordsPath(canvas,
		       circlePosX[MENU_ACHIEVEMENTS],
		       circlePosY[MENU_ACHIEVEMENTS],
		       factor, cx, cy, ox, oy, coords2);

	drawCoordsPath(canvas,
		       circlePosX[MENU_ACHIEVEMENTS],
		       circlePosY[MENU_ACHIEVEMENTS],
		       factor, cx, cy, ox, oy, coords3);

	menuPaint.setARGB(alpha, 100, 255, 100);
	canvas.drawCircle(circlePosX[MENU_ACHIEVEMENTS] + factor * ox,
			  circlePosY[MENU_ACHIEVEMENTS] + factor * oy,
			  factor * cr,
			  menuPaint);


	menuPaint.setARGB(255, 0, 0, 0);
	canvas.drawText(context.getString(R.string.menu_achievements),
			textPosX[MENU_ACHIEVEMENTS],
			textPosY[MENU_ACHIEVEMENTS],
			menuPaint);


	if(selected == MENU_LEADERBOARDS && changeSize != 0.0f) {
	    size = changeSize;
	    alpha = changeAlpha;
	} else {
	    size = (selectCircle == MENU_LEADERBOARDS ?
		    circleSize * 1.1f : circleSize);
	    alpha = 255;
	}
	menuPaint.setARGB(alpha, 100, 100, 255);
	canvas.drawCircle(circlePosX[MENU_LEADERBOARDS],
			  circlePosY[MENU_LEADERBOARDS],
			  size,
			  menuPaint);


	cx = 255.0f;
	cy = 255.0f;
	cr = 19.0f;
	ox = 0.0f;
	oy = 0.0f;
	factor = circleSize * 0.0025f;

	final float coords4[] = {
	    0, 155, 343,
	    0, 121, 223,
	    0, 190, 272,
	    0, 204, 155,
	    0, 256, 260,
	    0, 307, 155,
	    0, 321, 272,
	    0, 390, 223,
	    0, 356, 343,
	};

	final float coords5[] = {
	    0, 155, 354,
	    0, 356, 354,
	    0, 356, 375,
	    0, 155, 375,
	};

	final float coords6[] = {
	    121, 223,
	    204, 155,
	    307, 155,
	    390, 223,
	};

	menuPaint.setARGB(255, 255, 255, 255);

	drawCoordsPath(canvas,
		       circlePosX[MENU_LEADERBOARDS],
		       circlePosY[MENU_LEADERBOARDS],
		       factor, cx, cy, ox, oy, coords4);

	drawCoordsPath(canvas,
		       circlePosX[MENU_LEADERBOARDS],
		       circlePosY[MENU_LEADERBOARDS],
		       factor, cx, cy, ox, oy, coords5);

	for(int i = 0 ; i < coords6.length / 2 ; i++) {
	    canvas.drawCircle(circlePosX[MENU_LEADERBOARDS] +
			      factor * (ox + coords6[i * 2 + 0] - cx),
			      circlePosY[MENU_LEADERBOARDS] +
			      factor * (oy + coords6[i * 2 + 1] - cy),
			      factor * cr, menuPaint);
	}


	menuPaint.setARGB(255, 0, 0, 0);
	canvas.drawText(context.getString(R.string.menu_leaderboards),
			textPosX[MENU_LEADERBOARDS],
			textPosY[MENU_LEADERBOARDS],
			menuPaint);



	if(selected == MENU_SETTINGS && changeSize != 0.0f) {
	    size = changeSize;
	    alpha = changeAlpha;
	} else {
	    size = (selectCircle == MENU_SETTINGS ?
		    circleSize * 1.1f : circleSize);
	    alpha = 255;
	}
	menuPaint.setARGB(alpha, 240, 200, 100);
	canvas.drawCircle(circlePosX[MENU_SETTINGS],
			  circlePosY[MENU_SETTINGS],
			  size * SMALL_FACTOR,
			  menuPaint);

	cx = 60.0f;
	cy = 60.0f;
	cr = 23.0f;
	ox = 0.0f;
	oy = 0.0f;
	factor = circleSize * SMALL_FACTOR * 0.0075f;

	final float coords7[] = {
	    0, 58.67f, 103.86f,
	    1, 51.85f, 103.68f, 47.86f, 110.79f, 43.39f, 117.04f,
	    0, 26.94f, 109.66f,
	    1, 27.99f, 102.81f, 31.46f, 95.02f, 26.76f, 90.07f,
	    1, 22.06f, 85.11f, 14.22f, 87.32f, 6.64f, 88.58f,
	    0, 0.5f, 72.15f,
	    1, 6.34f, 68.47f, 14.31f, 65.41f, 14.49f, 58.59f,
	    1, 14.67f, 51.76f, 7.57f, 47.78f, 1.31f, 43.31f,
	    0, 8.69f, 26.86f,
	    1, 15.54f, 27.90f, 23.33f, 31.38f, 28.29f, 26.68f,
	    1, 33.24f, 21.98f, 31.03f, 14.14f, 29.77f, 6.55f,
	    0, 46.65f, 0.5f,
	    1, 50.78f, 6.43f, 53.83f, 14.40f, 60.66f, 14.58f,
	    1, 67.48f, 14.76f, 71.46f, 7.65f, 75.94f, 1.39f,
	    0, 92.39f, 8.78f,
	    1, 91.34f, 15.62f, 87.87f, 23.42f, 92.57f, 28.37f,
	    1, 97.27f, 33.33f, 105.11f, 31.12f, 112.69f, 29.86f,
	    0, 118.83f, 46.29f,
	    1, 112.99f, 49.97f, 105.02f, 53.02f, 104.84f, 59.85f,
	    1, 104.66f, 66.68f, 111.76f, 70.66f, 118.02f, 75.13f,
	    0, 110.64f, 91.58f,
	    1, 103.79f, 90.54f, 96.00f, 87.06f, 91.04f, 91.76f,
	    1, 86.09f, 96.46f, 88.30f, 104.30f, 89.56f, 111.89f,
	    0, 72.68f, 117.94f,
	    1, 68.55f, 112.01f, 65.50f, 104.04f, 58.67f, 103.86f,
	};

	menuPaint.setARGB(255, 255, 255, 255);

	drawCoordsPath(canvas,
		       circlePosX[MENU_SETTINGS],
		       circlePosY[MENU_SETTINGS],
		       factor, cx, cy, ox, oy, coords7);

	menuPaint.setARGB(alpha, 240, 200, 100);
	canvas.drawCircle(circlePosX[MENU_SETTINGS] + factor * ox,
			  circlePosY[MENU_SETTINGS] + factor * oy,
			  factor * cr,
			  menuPaint);

	menuPaint.setARGB(255, 0, 0, 0);
	canvas.drawText(context.getString(R.string.menu_settings),
			textPosX[MENU_SETTINGS],
			textPosY[MENU_SETTINGS],
			menuPaint);



	if(selected == MENU_RATE && changeSize != 0.0f) {
	    size = changeSize;
	    alpha = changeAlpha;
	} else {
	    size = (selectCircle == MENU_RATE ?
		    circleSize * 1.1f : circleSize);
	    alpha = 255;
	}
	menuPaint.setARGB(alpha, 240, 200, 100);
	canvas.drawCircle(circlePosX[MENU_RATE],
			  circlePosY[MENU_RATE],
			  size * SMALL_FACTOR,
			  menuPaint);

	cx = 352.0f;
	cy = 471.0f;
	ox = 0.0f;
	oy = 0.0f;
	factor = circleSize * SMALL_FACTOR * 0.0025f;

	final float coords8[] = {
	    0, 470.71f, 634.50f,
	    0, 352.22f, 552.22f,
	    0, 233.89f, 634.74f,
	    0, 275.53f, 496.62f,
	    0, 160.49f, 409.58f,
	    0, 304.71f, 406.50f,
	    0, 351.94f, 270.19f,
	    0, 399.44f, 406.41f,
	    0, 543.66f, 409.20f,
	    0, 428.80f, 496.47f,
	};

	menuPaint.setARGB(255, 255, 255, 255);

	drawCoordsPath(canvas,
		       circlePosX[MENU_RATE],
		       circlePosY[MENU_RATE],
		       factor, cx, cy, ox, oy, coords8);

	menuPaint.setARGB(255, 0, 0, 0);
	canvas.drawText(context.getString(R.string.menu_rate),
			textPosX[MENU_RATE],
			textPosY[MENU_RATE],
			menuPaint);
    }

    private void drawCoordsPath(Canvas canvas,
				float centerX, float centerY,
				float factor, float cx, float cy,
				float ox, float oy, float[] coords) {
	int i = 0;

	menuPath.rewind();
	menuPath.moveTo(centerX + factor * (ox + coords[i + 1] - cx),
			centerY + factor * (oy + coords[i + 2] - cy));
	i += 3;
	while(i < coords.length) {
	    if(coords[i] == 0.0f) {
		menuPath.lineTo(centerX + factor * (ox + coords[i + 1] - cx),
				centerY + factor * (oy + coords[i + 2] - cy));
		i += 3;
	    } else {
		menuPath.cubicTo(centerX + factor * (ox + coords[i + 1] - cx),
				 centerY + factor * (oy + coords[i + 2] - cy),
				 centerX + factor * (ox + coords[i + 3] - cx),
				 centerY + factor * (oy + coords[i + 4] - cy),
				 centerX + factor * (ox + coords[i + 5] - cx),
				 centerY + factor * (oy + coords[i + 6] - cy));
		i += 7;
	    }
	}
	menuPath.close();
	canvas.drawPath(menuPath, menuPaint);
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format,
			       int width, int height) {
	if(width != -1) {
	    surfaceWidth = width;
	    surfaceHeight = height;
	}

	circleSize = surfaceWidth / 10.0f;
	float center = surfaceWidth / 2.0f;
	if(resumeLevel >= 0) {
	    float distance = surfaceWidth / 4.3f;
	    circlePosX[MENU_RESUME] = center - 1.5f * distance;
	    circlePosX[MENU_STARTNEW] = center - 0.5f * distance;
	    circlePosX[MENU_ACHIEVEMENTS] = center + 0.5f * distance;
	    circlePosX[MENU_LEADERBOARDS] = center + 1.5f * distance;
	    circlePosX[MENU_SETTINGS] = center - 1.0f * distance;
	    circlePosX[MENU_RATE] = center + 1.0f * distance;
	} else {
	    float distance = surfaceWidth / 3.3f;
	    circlePosX[MENU_STARTNEW] = center - distance;
	    circlePosX[MENU_ACHIEVEMENTS] = center;
	    circlePosX[MENU_LEADERBOARDS] = center + distance;
	    circlePosX[MENU_SETTINGS] = center - 0.5f * distance;
	    circlePosX[MENU_RATE] = center + 0.5f * distance;
	}

        float pos1 = 1.5f;
        float pos2 = 3.8f;

	if(height / width < 0.48f) {
	    pos1 -= 0.1f;
	    pos2 -= 0.3f;
	}

	circlePosY[MENU_RESUME] = circleSize * pos1;
	circlePosY[MENU_STARTNEW] = circleSize * pos1;
	circlePosY[MENU_ACHIEVEMENTS] = circleSize * pos1;
	circlePosY[MENU_LEADERBOARDS] = circleSize * pos1;
	circlePosY[MENU_SETTINGS] = circleSize * pos2;
	circlePosY[MENU_RATE] = circleSize * pos2;

	textSize = circleSize / 3.5f;
	textAlign = Paint.Align.CENTER;
	for(int i = MENU_RESUME ; i <= MENU_RATE ; i++) {
	    if(i == MENU_RESUME && resumeLevel == -1)
		continue;
	    float factor = (i >= MENU_SETTINGS ? SMALL_FACTOR + 0.4f : 1.5f);
	    textPosX[i] = circlePosX[i];
	    textPosY[i] = circlePosY[i] + circleSize * factor;
	}
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
	if(thread == null || !thread.isRunning()) {
	    thread = new DrawThread(holder, handler, this);
	    thread.setRunning(true);
	    thread.start();
	}
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
	boolean retry = true;
	thread.setRunning(false);
	while(retry) {
	    try {
		thread.join();
		retry = false;
	    } catch(InterruptedException e) {
	    }
	}
    }

    public void onPause() {
    }

    public void onResume() {
	resumeLevel = GameSurfaceView.getStateLevel(context);
	resumeType = GameSurfaceView.getStateType(context);
	selected = MENU_NONE;
	selectCircle = MENU_NONE;
	changeSize = 0.0f;
	changeAlpha = 0;

	randomizeResumeCircles();
    }
}
