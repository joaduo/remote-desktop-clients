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

import java.util.ArrayList;
import java.util.Calendar;


public class InputHandlerDirectDragPan extends InputHandlerGeneric {
	static final String TAG = "InputHandlerDirectDragPan";
	static final String LOGTAG = "IHDirectDragPan";
	public static final String ID = "TOUCH_ZOOM_MODE_DRAG_PAN";

	public InputHandlerDirectDragPan(RemoteCanvasActivity activity, RemoteCanvas canvas,
									 RemotePointer pointer, Vibrator myVibrator) {
		super(activity, canvas, pointer, myVibrator);

		//inRangeStatus = InRangeStatus.OutOfRange;
		gestureStatus = GestureStatus.Nothing;
		eventHistory = new EventHistory();
	}

	@Override
	public String getDescription() {
		return canvas.getResources().getString(R.string.input_method_direct_drag_pan_description);
	}

	@Override
	public String getId() {
		return ID;
	}

	protected int getHX (MotionEvent e, int pos) {
		float scale = canvas.getZoomFactor();
		return (int)(canvas.getAbsX() + e.getHistoricalX(pos) / scale);
	}

	protected int getHY (MotionEvent e, int pos) {
		float scale = canvas.getZoomFactor();
		return (int)(canvas.getAbsY() + (e.getHistoricalY(pos) - 1.f * canvas.getTop()) / scale);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_HOVER_MOVE:
				for (int pos = 0; pos < event.getHistorySize(); pos++){
					pointer.moveMouse(getHX(event, pos), getHY(event, pos), event.getMetaState());
				}
				pointer.moveMouse(getX(event), getY(event), event.getMetaState());
				break;
		}
		return true;
	}

	public class HistoryEvent {
		final int x, y, meta, action;
		public HistoryEvent(int action, int x, int y, int meta) {
			this.action = action;
			this.meta = meta;
			this.x = x;
			this.y = y;
		}
	}
	class EventHistory{
		/**
		 * Gathers event in WAIT_SECOND_FINGER_TIMEOUT millisenconds before deciding whether
		 *   - discard (because in fact the user is doing zoom)
		 *   - flush to the network (so they are drawn in the other side)
		 */
		private ArrayList<HistoryEvent> history;
		EventHistory(){
			history = new ArrayList<HistoryEvent>();
		}
		public void pushEvent(MotionEvent event){
			// process historical data
			for (int pos = 0; pos < event.getHistorySize(); pos++){
				addH(event, pos);
			}
			add(event);
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_MOVE:
					activity.showToolbar();
					break;
				case MotionEvent.ACTION_DOWN:
					gestureStatus = GestureStatus.FirstPointer;
					activity.showToolbar();
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					gestureStatus = GestureStatus.Nothing;
					break;
			}
		}
		public void addH(MotionEvent event, int pos){
			history.add(new HistoryEvent(event.getActionMasked(), getHX(event, pos), getHY(event, pos), event.getMetaState()));
		}
		public void add(MotionEvent event){
			history.add(new HistoryEvent(event.getActionMasked(), getX(event), getY(event), event.getMetaState()));
		}
		public void flush(){
			for(HistoryEvent event : history){
				switch (event.action) {
					case MotionEvent.ACTION_MOVE:
						pointer.moveMouseButtonDown(event.x, event.y, event.meta);
						break;
					case MotionEvent.ACTION_DOWN:
						pointer.leftButtonDown(event.x, event.y, event.meta);
						canvas.movePanToMakePointerVisible();
						break;
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						pointer.releaseButton(event.x, event.y, event.meta);
						break;
				}
			}
			clear();
		}
		public void clear(){
			history.clear();
		}
	}
	EventHistory eventHistory;

	private enum GestureStatus {
		Nothing,
		FirstPointer,
		WaitSecondPointer,
		TapTimedOut,
		Zooming
	}
	GestureStatus gestureStatus;
	long firstPointerTime = 0;
	final long WAIT_SECOND_FINGER_TIMEOUT = 70;

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// Give priority to Scaling events when 2 fingers
		if(event.getPointerCount() == 2){
			if(gestureStatus != GestureStatus.Zooming){
				if(gestureStatus == GestureStatus.WaitSecondPointer){
					// discard 1-finger event history inside WAIT_SECOND_FINGER_TIMEOUT
					// user meant zooming
					eventHistory.clear();
				}
				else{
					// We were drawing with 1 finger and suddenly we switch to zoom/pan
					// we need to raise the pen/finger and flush the event over the network
					eventHistory.pushEvent(event);
					eventHistory.flush();
				}
			}
			// mark we are now zooming
			gestureStatus = GestureStatus.Zooming;
			// so dispatch to 2-finger gesture detectors
			scalingGestureDetector.onTouchEvent(event);
			gestureDetector.onTouchEvent(event);
			return true;
		}
		else if (gestureStatus == GestureStatus.Zooming){
			// we previously detected 2 fingers, raised one
			// trying to guess user's intentions
			gestureStatus = GestureStatus.FirstPointer;
		}
		// queue the 1-finger event (we don't know yet user's intention)
		// check the event type and change gestureStatus accordingly
		eventHistory.pushEvent(event);
		// if gestureStatus changed, then react
		if (gestureStatus == GestureStatus.FirstPointer){
			// 1-finger ACTION_DOWN, first finger is touching the screen
			firstPointerTime = Calendar.getInstance().getTimeInMillis();
			// we start waiting to see user's intentions (zooming or drawing)
			gestureStatus = GestureStatus.WaitSecondPointer;
		}
		else if (gestureStatus == GestureStatus.WaitSecondPointer){
			if(Calendar.getInstance().getTimeInMillis() - firstPointerTime > WAIT_SECOND_FINGER_TIMEOUT){
				gestureStatus = GestureStatus.TapTimedOut;
				eventHistory.flush();
			}
			// else do nothing,since we queued the event above.
		}
		else{
			// gestureStatus is TapTimedOut or Nothing ( not Zooming, not FirstPointer, not WaitSecondPointer)
			// means we flush the event
			eventHistory.flush();
		}
		return true;
	}

	@Override
	protected void sendScrollEvents(int x, int y, int meta) {
	}

	@Override
	public void onLongPress(MotionEvent e) {
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		return true;
	}

	@Override
	public boolean onDoubleTap (MotionEvent e) {
		return true;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		float scale = canvas.getZoomFactor();
		activity.showToolbar();
		canvas.relativePan((int)(distanceX*scale), (int)(distanceY*scale));
		return true;
	}


	final double  minScaleFactor = 0.2;
	@Override
	public boolean onScale(ScaleGestureDetector detector) {
		//android.util.Log.i(TAG, "onScale called");
		boolean eventConsumed = true;

		// Get the current focus.
		xCurrentFocus = detector.getFocusX();
		yCurrentFocus = detector.getFocusY();

		if (!inSwiping) {
			if ( !inScaling && Math.abs(1.0 - detector.getScaleFactor()) < minScaleFactor) {
				//android.util.Log.i(TAG,"Not scaling due to small scale factor.");
				eventConsumed = false;
			}

			if (eventConsumed && canvas != null && canvas.canvasZoomer != null) {
				if (inScaling == false) {
					inScaling = true;
				}
				//android.util.Log.i(LOGTAG, "Changing zoom level: " + detector.getScaleFactor());
				canvas.canvasZoomer.changeZoom(activity, detector.getScaleFactor(), xCurrentFocus, yCurrentFocus);
			}
		}
		return eventConsumed;
	}

}

