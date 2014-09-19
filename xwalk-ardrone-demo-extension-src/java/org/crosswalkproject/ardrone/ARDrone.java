// Copyright (c) 2013 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.crosswalkproject.ardrone;

import org.xwalk.app.runtime.extension.XWalkExtensionClient;
import org.xwalk.app.runtime.extension.XWalkExtensionContextClient;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;

import org.json.JSONException;
import org.json.JSONObject;

public class ARDrone extends XWalkExtensionClient {
    private static final String TAG = "ARDrone";

    private ATCommandManager mATCommandManager;
    private ATCommandQueue mCommandQueue;
    private String mRemoteAddress;
    private RunnableWithLock mKeepAliveRunnable;
    private Thread mCommandThread;
    private Thread mKeepAliveThread;

    public ARDrone(String name, String jsApiContent, XWalkExtensionContextClient xwalkContext) {
        super(name, jsApiContent, xwalkContext);
        mRemoteAddress = "192.168.1.1";
        mCommandQueue = new ATCommandQueue(10);
        mATCommandManager = new ATCommandManager(mCommandQueue, mRemoteAddress);
        mKeepAliveRunnable = new RunnableWithLock() {
            @Override
            public void run() {
                while (true) {
                    if (mCommandThread != null) {
                        mCommandQueue.add(new ATCommand(new ComwdgCommand()));
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.i(TAG, "KeepAliveThread interruptted!!!");
                        break;
                    }
                    paused();
                }
            }
        };
        mCommandThread = null;
        mKeepAliveThread = null;
    }

    private void handleMessage(int instanceID, String message) {
        try {
            JSONObject jsonInput = new JSONObject(message);
            String cmd = jsonInput.getString("cmd");
            String asyncCallId = jsonInput.getString("asyncCallId");
            handle(instanceID, asyncCallId, cmd);
        } catch (JSONException e) {
            printErrorMessage(e);
        }
    }

    private void handle(int instanceID, String asyncCallId, String cmd) {
        try {
            JSONObject jsonOutput = new JSONObject();

            if (cmd.equals("connect")) {
                jsonOutput.put("data", connect());
            } else if (cmd.equals("quit")) {
                jsonOutput.put("data", quit());
            } else {
                jsonOutput.put("data", commandDelegate(cmd));
            }

            jsonOutput.put("asyncCallId", asyncCallId);
            postMessage(instanceID, jsonOutput.toString());
        } catch (JSONException e) {
            printErrorMessage(e);
        }
    }

    private JSONObject connect() {
        if (mCommandThread != null) {
            return setOneJSONObject("connect", "true");
        }

        if (mKeepAliveThread == null) {
            mKeepAliveThread = new Thread(mKeepAliveRunnable);
        }

        mCommandThread = new Thread(mATCommandManager);
        mCommandThread.start();
        mKeepAliveThread.start();

        return setOneJSONObject("connect", "true");
    }

    private JSONObject quit() {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        mCommandQueue.add(new ATCommand(new QuitCommand()));
        mCommandThread.interrupt();
        mCommandThread = null;
        
        if (mKeepAliveThread != null) {
            mKeepAliveThread.interrupt();
            mKeepAliveThread = null;
        }

        return setOneJSONObject("quit", "true");
    }

    private JSONObject commandDelegate(String command) {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        if (command.equals("ftrim")) {
            mCommandQueue.add(new ATCommand(new FtrimCommand()));
            return setOneJSONObject("ftrim", "true");
        } else if (command.equals("takeoff")) {
            mCommandQueue.add(new ATCommand(new TakeoffCommand()));
            return setOneJSONObject("takeoff", "true");
        } else if (command.equals("landing")) {
            mCommandQueue.add(new ATCommand(new LandingCommand()));
            return setOneJSONObject("landing", "true");
        } else if (command.equals("hover")) {
            mCommandQueue.add(new ATCommand(new HoverCommand()));
            return setOneJSONObject("hover", "true");
        } else if (command.equals("pitch_plus")) {
            mCommandQueue.add(new ATCommand(new MoveCommand(false, 0.2f, 0f, 0f, 0f)));
            return setOneJSONObject("pitch_plus", "true");
        } else if (command.equals("pitch_minus")) {
            mCommandQueue.add(new ATCommand(new MoveCommand(false, -0.2f, 0f, 0f, 0f)));
            return setOneJSONObject("pitch_minus", "true");
        } else if (command.equals("roll_plus")) {
            mCommandQueue.add(new ATCommand(new MoveCommand(false, 0f, 0.2f, 0f, 0f)));
            return setOneJSONObject("roll_plus", "true");
        } else if (command.equals("roll_minus")) {
            mCommandQueue.add(new ATCommand(new MoveCommand(false, 0f, -0.2f, 0f, 0f)));
            return setOneJSONObject("roll_minus", "true");
        } else if (command.equals("yaw_plus")) {
            mCommandQueue.add(new ATCommand(new MoveCommand(false, 0f, 0f, 0f, 0.2f)));
            return setOneJSONObject("yaw_plus", "true");
        } else if (command.equals("yaw_minus")) {
            mCommandQueue.add(new ATCommand(new MoveCommand(false, 0f, 0f, 0f, -0.2f)));
            return setOneJSONObject("yaw_minus", "true");
        } else if (command.equals("altitude_plus")) {
            mCommandQueue.add(new ATCommand(new MoveCommand(false, 0f, 0f, 0.2f, 0f)));
            return setOneJSONObject("altitude_plus", "true");
        } else if (command.equals("altitude_minus")) {
            mCommandQueue.add(new ATCommand(new MoveCommand(false, 0f, 0f, -0.2f, 0f)));
            return setOneJSONObject("altitude_minus", "true");
        }

        return setOneJSONObject(command, "not supported!!!");
    }

    protected JSONObject setOneJSONObject(String key, String value) {
        JSONObject out = new JSONObject();
        try {
            out.put(key, value);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
        return out;
    }

    protected void printErrorMessage(JSONException e) {
        Log.e(TAG, e.toString());
    }

    protected JSONObject setErrorMessage(String error) {
        JSONObject out = new JSONObject();
        JSONObject errorMessage = new JSONObject();
        try {
            errorMessage.put("message", error);
            out.put("error", errorMessage);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
        return out;
    }

    @Override
    public void onResume() {
        if (mKeepAliveThread != null) {
            mKeepAliveRunnable.onResume();
        }

        if (mCommandThread != null) {
            mATCommandManager.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mKeepAliveThread != null) {
            mKeepAliveRunnable.onPause();
        }

        if (mCommandThread != null) {
            mATCommandManager.onPause();
        }
    }

    @Override
    public void onStop() {
        if (mKeepAliveThread != null) {
            mKeepAliveRunnable.onPause();
        }

        if (mCommandThread != null) {
            mATCommandManager.onPause();
        }
    }

    @Override
    public void onDestroy() {
        quit();
    }

    @Override
    public void onMessage(int instanceID, String message) {
        if (!message.isEmpty()) {
            handleMessage(instanceID, message);
        }
    }

    @Override
    public String onSyncMessage(int instanceID, String message) {
        return null;
    }
}
