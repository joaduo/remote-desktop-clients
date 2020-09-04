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

import com.iiordanov.bVNC.R;
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
								 RemotePointer pointer, Vibrator myVibrator) {
		super(activity, canvas, pointer, myVibrator);

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
		float scale = canvas.getZoomFactor();

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
			float scale = canvas.getZoomFactor();
			float fx,fy;
			short nx,ny, np;
			// GfxTablet original code visits all pointers (not sure why)
			for (int ptr = 0; ptr < event.getPointerCount(); ptr++){
				fx = canvas.getAbsX() + event.getX(ptr) / scale;
				fy = canvas.getAbsY() + (event.getY(ptr) - 1.f * canvas.getTop()) / scale;
				nx = normalize(fx, canvas.getImageWidth());
				ny = normalize(fy, canvas.getImageHeight());
				np = normalizePressure(event.getPressure(ptr));
				Log.v(TAG, String.format("Touch event logged: action %d @ %f|%f (pressure %f)", event.getActionMasked(), event.getX(ptr), event.getY(ptr), event.getPressure(ptr)));
				switch (event.getActionMasked()) {
					case MotionEvent.ACTION_MOVE:
						history.add(new NetEvent(Type.TYPE_MOTION, nx, ny, np));
						activity.showToolbar();
						break;
					case MotionEvent.ACTION_DOWN:
						if (inRangeStatus == inRangeStatus.OutOfRange) {
							inRangeStatus = inRangeStatus.FakeInRange;
							history.add(new NetEvent(Type.TYPE_BUTTON, nx, ny, (short)0, -1, true));
						}
						history.add(new NetEvent(Type.TYPE_BUTTON, nx, ny, np, 0, true));
						gestureStatus = GestureStatus.FirstPointer;
						activity.showToolbar();
						break;
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						pointerUp(nx,ny,np);
                        gestureStatus = GestureStatus.Nothing;
						break;
				}

			}
		}
		public void pointerUp(short nx, short ny, short npressure){
			history.add(new NetEvent(Type.TYPE_BUTTON, nx, ny, npressure, 0, false));
			if (inRangeStatus == inRangeStatus.FakeInRange) {
				inRangeStatus = inRangeStatus.OutOfRange;
				history.add(new NetEvent(Type.TYPE_BUTTON, nx, ny, (short)0, -1, false));
			}
		}
		public void send(NetworkClient netClient){
			for(NetEvent event : history){
				netClient.getQueue().add(event);
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
					// we need to raise the pen/finger and flush the event over the network
					float scale = canvas.getZoomFactor();
					float fx = canvas.getAbsX() + event.getX() / scale;
					float fy = canvas.getAbsY() + (event.getY() - 1.f * canvas.getTop()) / scale;
					short nx = normalize(fx, canvas.getImageWidth());
					short ny = normalize(fy, canvas.getImageHeight());
					short np = normalizePressure(event.getPressure());
					eventHistory.pointerUp(nx, ny, np);
					eventHistory.send(netClient);
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

		// we could probably ignore if more tha 1 finger  (at least for sending to the network)

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
			if(Calendar.getInstance().getTimeInMillis() - firstPointerTime > TAP_TIMEOUT){
				gestureStatus = GestureStatus.TapTimedOut;
				eventHistory.send(netClient);
			}
			// else do nothing,since we queued the event above.
		}
		else{
			// gestureStatus is TapTimedOut or Nothing ( not Zooming, not FirstPointer, not WaitSecondPointer)
			// means we flush the event over the network
			eventHistory.send(netClient);
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
}

