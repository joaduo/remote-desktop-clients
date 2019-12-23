/**
 * Copyright (C) 2019- Joaquin Duo
 * Copyright (C) 2013- Iordan Iordanov
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */


package com.iiordanov.bVNC.input;

import android.os.SystemClock;
import android.os.Vibrator;
import android.os.AsyncTask;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Toast;

import com.iiordanov.bVNC.R;
import com.iiordanov.bVNC.RemoteCanvas;
import com.iiordanov.bVNC.RemoteCanvasActivity;
import com.iiordanov.bVNC.gfxtablet.NetEvent;
import com.iiordanov.bVNC.gfxtablet.NetEvent.Type;
import com.iiordanov.bVNC.gfxtablet.NetworkClient;

public class InputHandlerGfxTablet extends InputHandlerGeneric {
	static final String TAG = "InputHandlerGfxTablet";
	public static final String ID = "GFX_TABLET_MODE";

	NetworkClient netClient;

	public InputHandlerGfxTablet(RemoteCanvasActivity activity, RemoteCanvas canvas,
                                 RemotePointer pointer, Vibrator myVibrator) {
		super(activity, canvas, pointer, myVibrator);

		// create network client in a separate thread
		netClient = new NetworkClient(canvas.ipNetworkTablet, canvas.portNetworkTablet);
		new Thread(netClient).start();
		new ConfigureNetworkingTask().execute(); //async resolve hostname
		inRangeStatus = InRangeStatus.OutOfRange;
	}

	private class ConfigureNetworkingTask extends AsyncTask<Void, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Void... params) {
			return netClient.reconfigureNetworking();
		}

		protected void onPostExecute(Boolean success) {

		}
	}

	@Override
	public String getDescription() {
		return canvas.getResources().getString(R.string.input_method_gfx_tablet_description);
	}

	@Override
	public String getId() {
		return ID;
	}

	private enum InRangeStatus {
		OutOfRange,
		InRange,
		FakeInRange
	}
	InRangeStatus inRangeStatus;

	short normalize(float x, int max) {
		return (short)(Math.min(Math.max(0, x), max) * 2 * Short.MAX_VALUE/max);
	}

	short normalizePressure(float x) {
		return (short)(Math.min(Math.max(0, x), 2.0) * Short.MAX_VALUE);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		float scale      = canvas.getZoomFactor();

		for (int ptr = 0; ptr < event.getPointerCount(); ptr++) {
			float fx = canvas.getAbsX() + event.getX(ptr) / scale;
			float fy = canvas.getAbsY() + (event.getY(ptr) - 1.f * canvas.getTop()) / scale;
			short nx = normalize(fx, canvas.getImageWidth());
			short ny = normalize(fy, canvas.getImageHeight());
			//short npressure = normalizePressure(event.getPressure(ptr) + event.getSize(ptr));
			short npressure = normalizePressure(event.getPressure(ptr));
			Log.v(TAG, String.format("Generic motion event logged: %f|%f, pressure %f", event.getX(ptr), event.getY(ptr), event.getPressure(ptr)));
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_HOVER_MOVE:
					netClient.getQueue().add(new NetEvent(Type.TYPE_MOTION, nx, ny, npressure));
					break;
				case MotionEvent.ACTION_HOVER_ENTER:
					inRangeStatus = InRangeStatus.InRange;
					netClient.getQueue().add(new NetEvent(Type.TYPE_BUTTON, nx, ny, npressure, -1, true));
					break;
				case MotionEvent.ACTION_HOVER_EXIT:
					inRangeStatus = InRangeStatus.OutOfRange;
					netClient.getQueue().add(new NetEvent(Type.TYPE_BUTTON, nx, ny, npressure, -1, false));
					break;
			}
		}
		return true;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// Give priority to Scaling events
		if(event.getPointerCount() == 2){
			scalingGestureDetector.onTouchEvent(event);
			return true;
		}

		float scale      = canvas.getZoomFactor();
		for (int ptr = 0; ptr < event.getPointerCount(); ptr++){
			float fx = canvas.getAbsX() + event.getX(ptr) / scale;
			float fy = canvas.getAbsY() + (event.getY(ptr) - 1.f * canvas.getTop()) / scale;
			short nx = normalize(fx, canvas.getImageWidth());
			short ny = normalize(fy, canvas.getImageHeight());
			//short npressure = normalizePressure(event.getPressure(ptr) + event.getSize(ptr));
			short npressure = normalizePressure(event.getPressure(ptr));
			Log.v(TAG, String.format("Touch event logged: action %d @ %f|%f (pressure %f)", event.getActionMasked(), event.getX(ptr), event.getY(ptr), event.getPressure(ptr)));
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_MOVE:
					netClient.getQueue().add(new NetEvent(Type.TYPE_MOTION, nx, ny, npressure));
					activity.showToolbar();
					break;
				case MotionEvent.ACTION_DOWN:
					if (inRangeStatus == inRangeStatus.OutOfRange) {
						inRangeStatus = inRangeStatus.FakeInRange;
						netClient.getQueue().add(new NetEvent(Type.TYPE_BUTTON, nx, ny, (short)0, -1, true));
					}
					netClient.getQueue().add(new NetEvent(Type.TYPE_BUTTON, nx, ny, npressure, 0, true));
					activity.showToolbar();
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					netClient.getQueue().add(new NetEvent(Type.TYPE_BUTTON, nx, ny, npressure, 0, false));
					if (inRangeStatus == inRangeStatus.FakeInRange) {
						inRangeStatus = inRangeStatus.OutOfRange;
						netClient.getQueue().add(new NetEvent(Type.TYPE_BUTTON, nx, ny, (short)0, -1, false));
					}
					break;
			}

		}
		return true;
	}

	@Override
	protected void sendScrollEvents(int x, int y, int meta) {
	}

	@Override
	public void onLongPress(MotionEvent e) {

		// If we've performed a right/middle-click and the gesture is not over yet, do not start drag mode.
		if (secondPointerWasDown || thirdPointerWasDown)
			return;

		//myVibrator.vibrate(Constants.SHORT_VIBRATION);

		canvas.displayShortToastMessage("Panning");
		endDragModesAndScrolling();
		panMode = true;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		int metaState   = e.getMetaState();
		activity.showToolbar();
		//pointer.leftButtonDown(getX(e), getY(e), metaState);
		//SystemClock.sleep(50);
		//pointer.releaseButton(getX(e), getY(e), metaState);
		//canvas.movePanToMakePointerVisible();
		return true;
	}

	@Override
	public boolean onDoubleTap (MotionEvent e) {
		int metaState   = e.getMetaState();
//		pointer.leftButtonDown(getX(e), getY(e), metaState);
//		SystemClock.sleep(50);
//		pointer.releaseButton(getX(e), getY(e), metaState);
//		SystemClock.sleep(50);
//		pointer.leftButtonDown(getX(e), getY(e), metaState);
//		SystemClock.sleep(50);
//		pointer.releaseButton(getX(e), getY(e), metaState);
		//canvas.movePanToMakePointerVisible();
		return true;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		RemotePointer p = canvas.getPointer();

		// If we are scaling, allow panning around by moving two fingers around the screen
		if (inScaling) {
			float scale = canvas.getZoomFactor();
			activity.showToolbar();
			canvas.relativePan((int)(distanceX*scale), (int)(distanceY*scale));
		} else {
			// onScroll called while scaling/swiping gesture is in effect. We ignore the event and pretend it was
			// consumed. This prevents the mouse pointer from flailing around while we are scaling.
			// Also, if one releases one finger slightly earlier than the other when scaling, it causes Android
			// to stick a spiteful onScroll with a MASSIVE delta here.
			// This would cause the mouse pointer to jump to another place suddenly.
			// Hence, we ignore onScroll after scaling until we lift all pointers up.
			boolean twoFingers = false;
			if (e1 != null)
				twoFingers = (e1.getPointerCount() > 1);
			if (e2 != null)
				twoFingers = twoFingers || (e2.getPointerCount() > 1);

			if (twoFingers||inSwiping)
				return true;

			activity.showToolbar();

			if (!dragMode) {
				dragMode = true;
				//GFX
				//p.leftButtonDown(getX(e1), getY(e1), e1.getMetaState());
			} else {
				//GFX
				//p.moveMouseButtonDown(getX(e2), getY(e2), e2.getMetaState());
			}
		}
		//canvas.movePanToMakePointerVisible();
		return true;
	}

	@Override
	public boolean onScale(ScaleGestureDetector detector) {
		//android.util.Log.i(TAG, "onScale called");
		boolean eventConsumed = true;

		// Get the current focus.
		xCurrentFocus = detector.getFocusX();
		yCurrentFocus = detector.getFocusY();

//		// If we haven't started scaling yet, we check whether a swipe is being performed.
//		// The arbitrary fudge factor may not be the best way to set a tolerance...
//		if (!inScaling) {
//			// Start swiping mode only after we've moved away from the initial focal point some distance.
//			if (!inSwiping) {
//				if ( (yCurrentFocus < (yInitialFocus - startSwipeDist)) ||
//						(yCurrentFocus > (yInitialFocus + startSwipeDist)) ||
//						(xCurrentFocus < (xInitialFocus - startSwipeDist)) ||
//						(xCurrentFocus > (xInitialFocus + startSwipeDist)) ) {
//					inSwiping      = true;
//					xPreviousFocus = xCurrentFocus;
//					yPreviousFocus = yCurrentFocus;
//				}
//			}
//
//			// If in swiping mode, indicate a swipe at regular intervals.
//			if (inSwiping) {
//				scrollDown  = false;
//				scrollUp    = false;
//				scrollRight = false;
//				scrollLeft  = false;
//				if        (yCurrentFocus < (yPreviousFocus - baseSwipeDist)) {
//					scrollDown     = true;
//					xPreviousFocus = xCurrentFocus;
//					yPreviousFocus = yCurrentFocus;
//				} else if (yCurrentFocus > (yPreviousFocus + baseSwipeDist)) {
//					scrollUp       = true;
//					xPreviousFocus = xCurrentFocus;
//					yPreviousFocus = yCurrentFocus;
//				} else if (xCurrentFocus < (xPreviousFocus - baseSwipeDist)) {
//					scrollRight    = true;
//					xPreviousFocus = xCurrentFocus;
//					yPreviousFocus = yCurrentFocus;
//				} else if (xCurrentFocus > (xPreviousFocus + baseSwipeDist)) {
//					scrollLeft     = true;
//					xPreviousFocus = xCurrentFocus;
//					yPreviousFocus = yCurrentFocus;
//				} else {
//					eventConsumed  = false;
//				}
//				// The faster we swipe, the faster we traverse the screen, and hence, the
//				// smaller the time-delta between consumed events. We take the reciprocal
//				// obtain swipeSpeed. If it goes to zero, we set it to at least one.
//				long elapsedTime = detector.getTimeDelta();
//				if (elapsedTime < 10) elapsedTime = 10;
//
//				swipeSpeed = baseSwipeTime/elapsedTime;
//				if (swipeSpeed == 0)  swipeSpeed = 1;
//				//if (consumed)        Log.d(TAG,"Current swipe speed: " + swipeSpeed);
//			}
//		}

		if (!inSwiping) {
			if ( !inScaling && Math.abs(1.0 - detector.getScaleFactor()) < minScaleFactor ) {
				//android.util.Log.i(TAG,"Not scaling due to small scale factor.");
				eventConsumed = false;
			}

			if (eventConsumed && canvas != null && canvas.canvasZoomer != null) {
				if (inScaling == false) {
					inScaling = true;
				}
				//android.util.Log.i(TAG, "Changing zoom level: " + detector.getScaleFactor());
				canvas.canvasZoomer.changeZoom(activity, detector.getScaleFactor(), xCurrentFocus, yCurrentFocus);
			}
		}
		return eventConsumed;
	}

	@Override
	public boolean onScaleBegin(ScaleGestureDetector detector) {
		//android.util.Log.i(TAG, "onScaleBegin ("+xInitialFocus+","+yInitialFocus+")");
		inScaling           = false;
		scalingJustFinished = false;
		// Cancel any swipes that may have been registered last time.
		inSwiping   = false;
		scrollDown  = false;
		scrollUp    = false;
		scrollRight = false;
		scrollLeft  = false;
		return true;
	}

	@Override
	public void onScaleEnd(ScaleGestureDetector detector) {
		//android.util.Log.d(TAG, "onScaleEnd");
		inScaling = false;
		inSwiping = false;
		scalingJustFinished = true;
	}
}

