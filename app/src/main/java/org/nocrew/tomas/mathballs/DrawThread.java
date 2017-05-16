package org.nocrew.tomas.mathballs;

import java.lang.Thread;
import java.lang.System;
import java.lang.InterruptedException;
import android.util.Log;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.graphics.Canvas;

import org.nocrew.tomas.mathballs.R;

class DrawThread extends Thread {
    private final static String TAG = "MB-DrawThread";

    private SurfaceHolder surfaceHolder;
    private Handler handler;
    private MySurfaceView view;
    private boolean run = false;
    private volatile boolean pause = false;
    private long lastDrawTime = 0;
 
    public DrawThread(SurfaceHolder surfaceHolder,
		      Handler handler,
		      MySurfaceView view) {
        this.surfaceHolder = surfaceHolder;
	this.handler = handler;
        this.view = view;
    }
 
    public void setPaused(boolean pause) {
	this.pause = pause;
    }

    public void setRunning(boolean run) {
        this.run = run;
    }

    public boolean isRunning() {
        return run;
    }

    @Override
    public void run() {
	Canvas c;
	while(run) {
	    //	    Debug.log(TAG, "running pause=" + pause + " " + view);

	    if(pause) {
		yield();
		continue;
	    }

	    c = null;
	    synchronized(surfaceHolder) {
		view.updatePhysics(System.nanoTime() / 1000000);
	    }

	    while(System.nanoTime() - lastDrawTime < 16000000) {
		long waitTime =
		    (16000000 - (System.nanoTime() - lastDrawTime)) / 1000000;
		if(waitTime <= 0)
		    break;
		try {
		    Thread.sleep(waitTime);
		} catch(InterruptedException e) {
		}
	    }

	    try {
		c = surfaceHolder.lockCanvas(null);
		synchronized(surfaceHolder) {
		    view.onDraw(c);
		    lastDrawTime = System.nanoTime();
		}
	    } finally {
		// do this in a finally so that if an exception is thrown
		// during the above, we don't leave the Surface in an
		// inconsistent state
		if(c != null) {
		    surfaceHolder.unlockCanvasAndPost(c);
		}
	    }
	} 
    }
}