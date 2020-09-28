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
import android.os.AsyncTask;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

//import com.iiordanov.bVNC.R;
import com.undatech.remoteClientUi.R;
import com.iiordanov.bVNC.RemoteCanvas;
import com.iiordanov.bVNC.RemoteCanvasActivity;
import com.iiordanov.bVNC.gfxtablet.NetEvent;
import com.iiordanov.bVNC.gfxtablet.NetEvent.Type;
import com.iiordanov.bVNC.gfxtablet.NetworkClient;

import java.util.ArrayList;
import java.util.Calendar;

public class InputHandlerGfxTablet extends InputHandlerGeneric {
	static final String TAG = "InputHandlerGfxTablet";
	public static final String ID = "GFX_TABLET_MODE";

	NetworkClient netClient;

	public InputHandlerGfxTablet(RemoteCanvasActivity activity, RemoteCanvas canvas,
								 RemotePointer pointer) {
		super(activity, canvas, pointer);

		// create network client in a separate thread
		netClient = new NetworkClient(canvas.ipNetworkTablet, canvas.portNetworkTablet);
		new Thread(netClient).start();
		new ConfigureNetworkingTask().execute(); //async resolve hostname
		inRangeStatus = InRangeStatus.OutOfRange;
		gestureStatus = GestureStatus.Nothing;
		eventHistory = new EventHistory();
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
		//Log.v(TAG, String.format("Generic motion event logged: %f|%f, pressure %f", event.getX(ptr), event.getY(ptr), event.getPressure(ptr)));
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_HOVER_MOVE:
				addNetEvent(buildNetEvent(Type.TYPE_MOTION, event));
				break;
			case MotionEvent.ACTION_HOVER_ENTER:
				inRangeStatus = InRangeStatus.InRange;
				addNetEvent(buildNetEvent(Type.TYPE_BUTTON, event)
							.setButton(NetEvent.RANGE_BUTTON, true));
				break;
			case MotionEvent.ACTION_HOVER_EXIT:
				inRangeStatus = InRangeStatus.OutOfRange;
				addNetEvent(buildNetEvent(Type.TYPE_BUTTON, event)
							.setButton(NetEvent.RANGE_BUTTON, false));
				break;
		}
		return true;
	}

	private byte latestButton, latestButtonDown;
	public void addNetEvent(NetEvent event){
		// This menthod makes sure we don't repeat same button press event twice
		if( event.type == Type.TYPE_BUTTON
			&& event.button == this.latestButton
			&& event.button_down == this.latestButtonDown){
			Log.d(TAG, String.format("Ignoring repeated NetEvent button event %d %d", event.button, event.button_down));
		}
		else{
			if(event.type == Type.TYPE_BUTTON){
				// save for future comparisons
				this.latestButton = event.button;
				this.latestButtonDown = event.button_down;
			}
			netClient.getQueue().add(event);
		}
	}
	public NetEvent buildNetEvent(Type event_type, MotionEvent event){
		// build a NetEvent for several pointers
		return buildNetEventH(event_type, event, 0, -1);
	}
	public NetEvent buildNetEventH(Type event_type, MotionEvent event, int pos){
		return buildNetEventH(event_type, event, 0, pos);
	}
	public NetEvent buildNetEventH(Type event_type, MotionEvent event, int ptr, int pos){
		float scale = canvas.getZoomFactor();
		float fx,fy;
		if (pos == -1) {
			fx = canvas.getAbsX() + event.getX(ptr) / scale;
			fy = canvas.getAbsY() + (event.getY(ptr) - 1.f * canvas.getTop()) / scale;
		} else {
			fx = canvas.getAbsX() + event.getHistoricalX(ptr, pos) / scale;
			fy = canvas.getAbsY() + (event.getHistoricalY(ptr, pos) - 1.f * canvas.getTop()) / scale;
		}
		short nx = normalize(fx, canvas.getImageWidth());
		short ny = normalize(fy, canvas.getImageHeight());
		short np = normalizePressure(event.getPressure(ptr));
		return new NetEvent(event_type, nx, ny, np);
	}

	/**
	 * Gather event in TAP_TIMEOUT millisenconds before deciding whether
	 *   - discard (because in fact the user is doing zoom)
	 *   - flush to the network (so they are drawn in the other side)
	 */
	class EventHistory{
		private ArrayList<NetEvent> history;
		EventHistory(){
			history = new ArrayList<NetEvent>();
		}
		public void pushEvent(MotionEvent event){
			//Log.v(TAG, String.format("Touch event logged: action %d @ %f|%f (pressure %f)", event.getActionMasked(), event.getX(ptr), event.getY(ptr), event.getPressure(ptr)));
			int action = event.getActionMasked();
			for (int pos = 0; pos < event.getHistorySize(); pos++){
				switch (action){
					case MotionEvent.ACTION_MOVE:
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						history.add(buildNetEventH(Type.TYPE_MOTION, event, pos));
						break;
				}
			}
			switch (action) {
				case MotionEvent.ACTION_MOVE:
					history.add(buildNetEvent(Type.TYPE_MOTION, event));
					activity.showToolbar();
					break;
				case MotionEvent.ACTION_DOWN:
					if (inRangeStatus == inRangeStatus.OutOfRange) {
						inRangeStatus = inRangeStatus.FakeInRange;
						history.add(buildNetEvent(Type.TYPE_BUTTON, event)
									.setPressure((short)0)
									.setButton(NetEvent.RANGE_BUTTON, true));
					}
					history.add(buildNetEvent(Type.TYPE_BUTTON, event)
								.setButton(NetEvent.DRAW_BUTTON, true));
					gestureStatus = GestureStatus.FirstPointer;
					activity.showToolbar();
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					pointerUp(event);
					gestureStatus = GestureStatus.Nothing;
					break;
			}
		}
		public void pointerUp(MotionEvent event){
			history.add(buildNetEvent(Type.TYPE_BUTTON, event)
						.setButton(NetEvent.DRAW_BUTTON, false));
			if (inRangeStatus == inRangeStatus.FakeInRange) {
				inRangeStatus = inRangeStatus.OutOfRange;
				history.add(buildNetEvent(Type.TYPE_BUTTON, event)
							.setPressure((short)0)
							.setButton(NetEvent.RANGE_BUTTON, false));
			}
		}
		public void send(){
			for(NetEvent event : history){
				addNetEvent(event);
			}
			history.clear();
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
	long TAP_TIMEOUT = 70;

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// Give priority to Scaling events when 2 fingers
		if(event.getPointerCount() == 2){
			if(gestureStatus != GestureStatus.Zooming){
				if(gestureStatus == GestureStatus.WaitSecondPointer){
					// discard 1-finger event history inside TAP_TIMEOUT
					// user meant zooming
					eventHistory.clear();
				}
				else{
					// We were drawing with 1 finger and suddenly we switch to zoom/pan
					// we need to raise the pen/finger
					eventHistory.pointerUp(event);
					eventHistory.send();
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
		// check the event type and change GestureStatus accordingly
		eventHistory.pushEvent(event);
		// if gestureStatus changed, then react
		if (gestureStatus == GestureStatus.FirstPointer){
			// 1-finger ACTION_DOWN, first finger is touching the screen
			firstPointerTime = Calendar.getInstance().getTimeInMillis();
			// we start waiting to see user's intentions (zooming or drawing)
			gestureStatus = GestureStatus.WaitSecondPointer;
		}
		else if (gestureStatus == GestureStatus.WaitSecondPointer){
			if(Calendar.getInstance().getTimeInMillis() - firstPointerTime > TAP_TIMEOUT){
				gestureStatus = GestureStatus.TapTimedOut;
				eventHistory.send();
			}
			// else do nothing,since we queued the event above.
		}
		else{
			// gestureStatus is TapTimedOut or Nothing ( not Zooming, not FirstPointer, not WaitSecondPointer)
			// means we flush the event over the network
			eventHistory.send();
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

	@Override
	public boolean onScale(ScaleGestureDetector detector) {
		//android.util.Log.i(TAG, "onScale called");
		boolean eventConsumed = true;

		// Get the current focus.
		xCurrentFocus = detector.getFocusX();
		yCurrentFocus = detector.getFocusY();

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

