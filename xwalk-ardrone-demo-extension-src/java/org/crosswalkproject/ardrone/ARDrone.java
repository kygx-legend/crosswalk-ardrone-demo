// Copyright (c) 2013 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.crosswalkproject.ardrone;

import org.xwalk.app.runtime.extension.XWalkExtensionClient;
import org.xwalk.app.runtime.extension.XWalkExtensionContextClient;

import android.util.Log;

import java.io.InputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.json.JSONException;
import org.json.JSONObject;

public class ARDrone extends XWalkExtensionClient {
    private static final String TAG = "ARDrone";

    private ATCommandManager mATCommandManager;
    private ATCommandQueue mCommandQueue;
    private InetAddress mInetAddress;
    private Socket mVideoSocket;
    private String mRemoteAddress;
    private RunnableWithLock mKeepAliveRunnable;
    private Thread mCommandThread;
    private Thread mKeepAliveThread;
    private Thread mVideoStreamThread;
    private Thread mParse2RawH264Thread;
    private VideoStreamManager mVideoStreamRunnable;
    private InputStream mInputStream;
    private PipedInputStream mPipedInputStream;
    private PipedOutputStream mPipedOutputStream;

    public ARDrone(String name, String jsApiContent, XWalkExtensionContextClient xwalkContext) {
        super(name, jsApiContent, xwalkContext);
        mRemoteAddress = "192.168.1.1";
        try {
            mInetAddress = InetAddress.getByName(mRemoteAddress);
        } catch (UnknownHostException e) {
            Log.i(TAG, e.toString());
        }
        mCommandQueue = new ATCommandQueue(10);
        mATCommandManager = new ATCommandManager(mCommandQueue, mInetAddress);
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
            if (cmd.equals("addEventListener")) {
                String eventName = jsonInput.getString("eventName");
                handleAddEventListener(eventName);
            } else {
                String asyncCallId = jsonInput.getString("asyncCallId");
                handleControlCommands(instanceID, asyncCallId, cmd);
            }
        } catch (JSONException e) {
            printErrorMessage(e);
        }
    }

    private void handleControlCommands(int instanceID, String asyncCallId, String cmd) {
        try {
            JSONObject jsonOutput = new JSONObject();

            if (cmd.equals("connect")) {
                jsonOutput.put("data", connect());
            } else if (cmd.equals("quit")) {
                jsonOutput.put("data", quit());
            } else if (cmd.equals("ftrim")) {
                jsonOutput.put("data", ftrim());
            } else if (cmd.equals("takeoff")) {
                jsonOutput.put("data", takeoff());
            } else if (cmd.equals("landing")) {
                jsonOutput.put("data", landing());
            } else if (cmd.equals("hover")) {
                jsonOutput.put("data", hover());
            } else if (cmd.equals("pitch_plus")) {
                jsonOutput.put("data", pitch_plus());
            } else if (cmd.equals("pitch_minus")) {
                jsonOutput.put("data", pitch_minus());
            } else if (cmd.equals("roll_plus")) {
                jsonOutput.put("data", roll_plus());
            } else if (cmd.equals("roll_minus")) {
                jsonOutput.put("data", roll_minus());
            } else if (cmd.equals("yaw_plus")) {
                jsonOutput.put("data", yaw_plus());
            } else if (cmd.equals("yaw_minus")) {
                jsonOutput.put("data", yaw_minus());
            }

            jsonOutput.put("asyncCallId", asyncCallId);
            postMessage(instanceID, jsonOutput.toString());
        } catch (JSONException e) {
            printErrorMessage(e);
        }
    }

    private void handleAddEventListener(String eventName) {
        if (!eventName.equals("videostreaming")) {
            return;
        }

        try {
            mVideoSocket = new Socket(mInetAddress, VideoStreamManager.VIDEO_PORT);
            mInputStream = mVideoSocket.getInputStream();
            mPipedInputStream = new PipedInputStream(10 * 1024);
            mPipedOutputStream = new PipedOutputStream(mPipedInputStream);
            mParse2RawH264Thread = new Thread(new RunnableWithLock() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            int length = ParsePaVEHeader.parseHeader(mInputStream);
                            byte[] bytes = ParsePaVEHeader.readPacket(mInputStream, length);
                            mPipedOutputStream.write(bytes);
                        } catch (IOException e) {
                            Log.i(TAG, e.toString());
                        }
                        paused();
                    }
                }
            });
            mParse2RawH264Thread.start();
            mVideoStreamRunnable = new VideoStreamManager(this, mPipedInputStream);
            mVideoStreamThread = new Thread(mVideoStreamRunnable);
            mVideoStreamThread.start();
        } catch (IOException e) {
            Log.i(TAG, e.toString());
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

    private JSONObject ftrim() {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        mCommandQueue.add(new ATCommand(new FtrimCommand()));

        return setOneJSONObject("ftrim", "true");
    }

    private JSONObject takeoff() {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        mCommandQueue.add(new ATCommand(new TakeoffCommand()));

        return setOneJSONObject("takeoff", "true");
    }

    private JSONObject landing() {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        mCommandQueue.add(new ATCommand(new LandingCommand()));
        
        return setOneJSONObject("landing", "true");
    }

    private JSONObject hover() {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        mCommandQueue.add(new ATCommand(new HoverCommand()));

        return setOneJSONObject("hover", "true");
    }

    private JSONObject pitch_plus() {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        mCommandQueue.add(new ATCommand(new MoveCommand(false, 0.5f, 0f, 0f, 0f)));

        return setOneJSONObject("pitch_plus", "true");
    }

    private JSONObject pitch_minus() {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        mCommandQueue.add(new ATCommand(new MoveCommand(false, -0.5f, 0f, 0f, 0f)));

        return setOneJSONObject("pitch_minus", "true");
    }

    private JSONObject roll_plus() {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        mCommandQueue.add(new ATCommand(new MoveCommand(false, 0f, 0.5f, 0f, 0f)));

        return setOneJSONObject("roll_plus", "true");
    }

    private JSONObject roll_minus() {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        mCommandQueue.add(new ATCommand(new MoveCommand(false, 0f, -0.5f, 0f, 0f)));

        return setOneJSONObject("roll_minus", "true");
    }

    private JSONObject yaw_plus() {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        mCommandQueue.add(new ATCommand(new MoveCommand(false, 0f, 0f, 0f, 0.5f)));

        return setOneJSONObject("yaw_plus", "true");
    }

    private JSONObject yaw_minus() {
        if (mCommandThread == null) {
            return setOneJSONObject("status", "not connected");
        }

        mCommandQueue.add(new ATCommand(new MoveCommand(false, 0f, 0f, 0f, -0.5f)));

        return setOneJSONObject("yaw_minus", "true");
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

        if (mParse2RawH264Thread != null) {
            mParse2RawH264Thread.interrupt();
            mParse2RawH264Thread = null;
        }

        if (mVideoStreamThread != null) {
            mVideoStreamThread.interrupt();
            mVideoStreamThread = null;
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

        if (mParse2RawH264Thread != null) {
            mParse2RawH264Thread.interrupt();
            mParse2RawH264Thread = null;
        }

        if (mVideoStreamThread != null) {
            mVideoStreamThread.interrupt();
            mVideoStreamThread = null;
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
