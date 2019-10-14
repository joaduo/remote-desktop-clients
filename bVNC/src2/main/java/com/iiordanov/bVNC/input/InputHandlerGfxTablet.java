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

import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.iiordanov.bVNC.R;
import com.iiordanov.bVNC.RemoteCanvas;
import com.iiordanov.bVNC.RemoteCanvasActivity;

public class InputHandlerGfxTablet extends InputHandlerGeneric {
	static final String TAG = "InputHandlerGfxTablet";
	public static final String ID = "GFX_TABLET_MODE";

	public InputHandlerGfxTablet(RemoteCanvasActivity activity, RemoteCanvas canvas,
                                 RemotePointer pointer, Vibrator myVibrator) {
		super(activity, canvas, pointer, myVibrator);
	}

	@Override
	public String getDescription() {
		return canvas.getResources().getString(R.string.input_method_gfx_tablet_description);
	}

	@Override
	public String getId() {
		return ID;
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
				p.leftButtonDown(getX(e1), getY(e1), e1.getMetaState());
			} else {
				p.moveMouseButtonDown(getX(e2), getY(e2), e2.getMetaState());
			}
		}
		canvas.movePanToMakePointerVisible();
		return true;
	}

	@Override
	public boolean onScale(ScaleGestureDetector detector) {
		//android.util.Log.i(TAG, "onScale called");
		boolean eventConsumed = true;

		// Get the current focus.
		xCurrentFocus = detector.getFocusX();
		yCurrentFocus = detector.getFocusY();

		// If we haven't started scaling yet, we check whether a swipe is being performed.
		// The arbitrary fudge factor may not be the best way to set a tolerance...
		if (!inScaling) {
			// Start swiping mode only after we've moved away from the initial focal point some distance.
			if (!inSwiping) {
				if ( (yCurrentFocus < (yInitialFocus - startSwipeDist)) ||
						(yCurrentFocus > (yInitialFocus + startSwipeDist)) ||
						(xCurrentFocus < (xInitialFocus - startSwipeDist)) ||
						(xCurrentFocus > (xInitialFocus + startSwipeDist)) ) {
					inSwiping      = true;
					xPreviousFocus = xCurrentFocus;
					yPreviousFocus = yCurrentFocus;
				}
			}

			// If in swiping mode, indicate a swipe at regular intervals.
			if (inSwiping) {
				scrollDown  = false;
				scrollUp    = false;
				scrollRight = false;
				scrollLeft  = false;
				if        (yCurrentFocus < (yPreviousFocus - baseSwipeDist)) {
					scrollDown     = true;
					xPreviousFocus = xCurrentFocus;
					yPreviousFocus = yCurrentFocus;
				} else if (yCurrentFocus > (yPreviousFocus + baseSwipeDist)) {
					scrollUp       = true;
					xPreviousFocus = xCurrentFocus;
					yPreviousFocus = yCurrentFocus;
				} else if (xCurrentFocus < (xPreviousFocus - baseSwipeDist)) {
					scrollRight    = true;
					xPreviousFocus = xCurrentFocus;
					yPreviousFocus = yCurrentFocus;
				} else if (xCurrentFocus > (xPreviousFocus + baseSwipeDist)) {
					scrollLeft     = true;
					xPreviousFocus = xCurrentFocus;
					yPreviousFocus = yCurrentFocus;
				} else {
					eventConsumed  = false;
				}
				// The faster we swipe, the faster we traverse the screen, and hence, the
				// smaller the time-delta between consumed events. We take the reciprocal
				// obtain swipeSpeed. If it goes to zero, we set it to at least one.
				long elapsedTime = detector.getTimeDelta();
				if (elapsedTime < 10) elapsedTime = 10;

				swipeSpeed = baseSwipeTime/elapsedTime;
				if (swipeSpeed == 0)  swipeSpeed = 1;
				//if (consumed)        Log.d(TAG,"Current swipe speed: " + swipeSpeed);
			}
		}

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

