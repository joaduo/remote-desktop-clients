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

	protected boolean handleMouseActions (MotionEvent e) {
		boolean used     = false;
		final int action = e.getActionMasked();
		final int meta   = e.getMetaState();
		final int bstate = e.getButtonState();
		float scale      = canvas.getZoomFactor();
		int x = (int)(canvas.getAbsX() +  e.getX()                          / scale);
		int y = (int)(canvas.getAbsY() + (e.getY() - 1.f * canvas.getTop()) / scale);

		switch (action) {
			// If a mouse button was pressed or mouse was moved.
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_MOVE:
				switch (bstate) {
					case MotionEvent.BUTTON_PRIMARY:
						canvas.movePanToMakePointerVisible();
						pointer.leftButtonDown(x, y, meta);
						used = true;
						break;
					case MotionEvent.BUTTON_SECONDARY:
						canvas.movePanToMakePointerVisible();
						pointer.rightButtonDown(x, y, meta);
						used = true;
						break;
					case MotionEvent.BUTTON_TERTIARY:
						canvas.movePanToMakePointerVisible();
						pointer.middleButtonDown(x, y, meta);
						used = true;
						break;
				}
				break;
			// If a mouse button was released.
			case MotionEvent.ACTION_UP:
				switch (bstate) {
					case 0:
						if (e.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE) {
							break;
						}
					case MotionEvent.BUTTON_PRIMARY:
					case MotionEvent.BUTTON_SECONDARY:
					case MotionEvent.BUTTON_TERTIARY:
						canvas.movePanToMakePointerVisible();
						pointer.releaseButton(x, y, meta);
						used = true;
						break;
				}
				break;
			// If the mouse wheel was scrolled.
			case MotionEvent.ACTION_SCROLL:
				float vscroll = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
				float hscroll = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
				scrollDown  = false;
				scrollUp    = false;
				scrollRight = false;
				scrollLeft  = false;
				// Determine direction and speed of scrolling.
				if (vscroll < 0) {
					swipeSpeed = (int)(-1*vscroll);
					scrollDown = true;
				} else if (vscroll > 0) {
					swipeSpeed = (int)vscroll;
					scrollUp   = true;
				} else if (hscroll < 0) {
					swipeSpeed = (int)(-1*hscroll);
					scrollRight = true;
				} else if (hscroll > 0) {
					swipeSpeed = (int)hscroll;
					scrollLeft  = true;
				} else
					break;

				sendScrollEvents (x, y, meta);
				used = true;
				break;
			// If the mouse was moved OR as reported, some external mice trigger this when a
			// mouse button is pressed as well, so we check bstate here too.
			case MotionEvent.ACTION_HOVER_MOVE:
				canvas.movePanToMakePointerVisible();
				switch (bstate) {
					case MotionEvent.BUTTON_PRIMARY:
						pointer.leftButtonDown(x, y, meta);
						break;
					case MotionEvent.BUTTON_SECONDARY:
						pointer.rightButtonDown(x, y, meta);
						break;
					case MotionEvent.BUTTON_TERTIARY:
						pointer.middleButtonDown(x, y, meta);
						break;
					default:
						pointer.moveMouseButtonUp(x, y, meta);
						break;
				}
				used = true;
		}

		prevMouseOrStylusAction = action;
		return used;
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		final int action     = e.getActionMasked();
		final int index      = e.getActionIndex();
		final int pointerID  = e.getPointerId(index);
		final int meta       = e.getMetaState();

		float f = e.getPressure();
		if (f > 2.f)
			f = f / 50.f;
		if (f > .92f) {
			disregardNextOnFling = true;
		}

		if (android.os.Build.VERSION.SDK_INT >= 14) {
			// Handle and consume actions performed by a (e.g. USB or bluetooth) mouse.
			if (handleMouseActions (e))
				return true;
		}

		if (action == MotionEvent.ACTION_UP) {
			// Turn filtering back on and invalidate to make things pretty.
			canvas.myDrawable.paint.setFilterBitmap(true);
			canvas.invalidate();
		}

		switch (pointerID) {

			case 0:
				switch (action) {
					case MotionEvent.ACTION_DOWN:
						disregardNextOnFling = false;
						singleHandedJustEnded = false;
						// We have put down first pointer on the screen, so we can reset the state of all click-state variables.
						// Permit sending mouse-down event on long-tap again.
						secondPointerWasDown = false;
						// Permit right-clicking again.
						thirdPointerWasDown = false;
						// Cancel any effect of scaling having "just finished" (e.g. ignoring scrolling).
						scalingJustFinished = false;
						// Cancel drag modes and scrolling.
						if (!singleHandedGesture)
							endDragModesAndScrolling();
						canvas.cursorBeingMoved = true;
						// If we are manipulating the desktop, turn off bitmap filtering for faster response.
						canvas.myDrawable.paint.setFilterBitmap(false);
						// Indicate where we start dragging from.
						dragX = e.getX();
						dragY = e.getY();

						// Detect whether this is potentially the start of a gesture to show the nav bar.
						detectImmersiveSwipe(dragY);
						break;
					case MotionEvent.ACTION_UP:
						singleHandedGesture = false;
						singleHandedJustEnded = true;

						// If this is the end of a swipe that showed the nav bar, consume.
						if (immersiveSwipe && Math.abs(dragY - e.getY()) > immersiveSwipeDistance) {
							endDragModesAndScrolling();
							return true;
						}

						// If any drag modes were going on, end them and send a mouse up event.
						if (endDragModesAndScrolling()) {
							pointer.releaseButton(getX(e), getY(e), meta);
							return true;
						}
						break;
					case MotionEvent.ACTION_MOVE:
						// Send scroll up/down events if swiping is happening.
						if (panMode) {
							float scale = canvas.getZoomFactor();
							canvas.relativePan(-(int)((e.getX() - dragX)*scale), -(int)((e.getY() - dragY)*scale));
							dragX = e.getX();
							dragY = e.getY();
							return true;
						} else if (dragMode || rightDragMode || middleDragMode) {
							canvas.movePanToMakePointerVisible();
							pointer.moveMouseButtonDown(getX(e), getY(e), meta);
							return true;
						} else if (inSwiping) {
							// Save the coordinates and restore them afterward.
							float x = e.getX();
							float y = e.getY();
							// Set the coordinates to where the swipe began (i.e. where scaling started).
							setEventCoordinates(e, xInitialFocus, yInitialFocus);
							sendScrollEvents (getX(e), getY(e), meta);
							// Restore the coordinates so that onScale doesn't get all muddled up.
							setEventCoordinates(e, x, y);
						} else if (immersiveSwipe) {
							// If this is part of swipe that shows the nav bar, consume.
							return true;
						}
				}
				break;

			case 1:
				switch (action) {
					case MotionEvent.ACTION_POINTER_DOWN:
						// We re-calculate the initial focal point to be between the 1st and 2nd pointer index.
						xInitialFocus = 0.5f * (dragX + e.getX(pointerID));
						yInitialFocus = 0.5f * (dragY + e.getY(pointerID));
						// Here we only prepare for the second click, which we perform on ACTION_POINTER_UP for pointerID==1.
						endDragModesAndScrolling();
						// Permit sending mouse-down event on long-tap again.
						secondPointerWasDown = true;
						// Permit right-clicking again.
						thirdPointerWasDown  = false;
						break;
					case MotionEvent.ACTION_POINTER_UP:
						if (!inSwiping && !inScaling && !thirdPointerWasDown) {
							// If user taps with a second finger while first finger is down, then we treat this as
							// a right mouse click, but we only effect the click when the second pointer goes up.
							// If the user taps with a second and third finger while the first
							// finger is down, we treat it as a middle mouse click. We ignore the lifting of the
							// second index when the third index has gone down (using the thirdPointerWasDown variable)
							// to prevent inadvertent right-clicks when a middle click has been performed.
							pointer.rightButtonDown(getX(e), getY(e), meta);
							// Enter right-drag mode.
							rightDragMode = true;
							// Now the event must be passed on to the parent class in order to
							// end scaling as it was certainly started when the second pointer went down.
						}
						break;
				}
				break;

			case 2:
				switch (action) {
					case MotionEvent.ACTION_POINTER_DOWN:
						if (!inScaling) {
							// This boolean prevents the right-click from firing simultaneously as a middle button click.
							thirdPointerWasDown = true;
							pointer.middleButtonDown(getX(e), getY(e), meta);
							// Enter middle-drag mode.
							middleDragMode      = true;
						}
				}
				break;
		}

		scalingGestureDetector.onTouchEvent(e);
		return gestureDetector.onTouchEvent(e);
	}

	protected void sendScrollEvents (int x, int y, int meta) {
		int numEvents = 0;
		while (numEvents < swipeSpeed && numEvents < maxSwipeSpeed) {
			if         (scrollDown) {
				pointer.scrollDown(x, y, meta);
				pointer.moveMouseButtonUp(x, y, meta);
			} else if (scrollUp) {
				pointer.scrollUp(x, y, meta);
				pointer.moveMouseButtonUp(x, y, meta);
			} else if (scrollRight) {
				pointer.scrollRight(x, y, meta);
				pointer.moveMouseButtonUp(x, y, meta);
			} else if (scrollLeft) {
				pointer.scrollLeft(x, y, meta);
				pointer.moveMouseButtonUp(x, y, meta);
			}
			numEvents++;
		}
		pointer.releaseButton(x, y, meta);
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
		pointer.leftButtonDown(getX(e), getY(e), metaState);
		SystemClock.sleep(50);
		pointer.releaseButton(getX(e), getY(e), metaState);
		canvas.movePanToMakePointerVisible();
		return true;
	}

	@Override
	public boolean onDoubleTap (MotionEvent e) {
		int metaState   = e.getMetaState();
		pointer.leftButtonDown(getX(e), getY(e), metaState);
		SystemClock.sleep(50);
		pointer.releaseButton(getX(e), getY(e), metaState);
		SystemClock.sleep(50);
		pointer.leftButtonDown(getX(e), getY(e), metaState);
		SystemClock.sleep(50);
		pointer.releaseButton(getX(e), getY(e), metaState);
		canvas.movePanToMakePointerVisible();
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

