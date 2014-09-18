// Copyright (c) 2013 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.crosswalkproject.ardrone;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class ATCommandManager implements Runnable {
    public static final String TAG = "ATCommandManager";

    public static final int CMD_PORT = 5556;

    private ATCommandQueue mQueue;
    private DatagramSocket mDataSocket;
    private InetAddress mInetAddress;
    private int sequence;

    public ATCommandManager(ATCommandQueue queue, DatagramSocket socket, String remoteAddress) {
        mQueue = queue;
        mDataSocket = socket;
        sequence = 1;
        try {
            mInetAddress = InetAddress.getByName(remoteAddress);
        } catch (UnknownHostException e) {
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                ATCommand atCommand = mQueue.take();
                byte[] packetData = atCommand.buildPacketBytes(sequence);
                sequence += 1;
                DatagramPacket datagramPacket = new DatagramPacket(packetData, packetData.length,
                        mInetAddress, CMD_PORT);
                mDataSocket.send(datagramPacket);

                Log.i(TAG, atCommand.buildATCommandString(sequence));

                if (atCommand.getCommandType().equals("Quit")) {
                    break;
                }

                Thread.sleep(30);
            } catch (InterruptedException e) {
                break;
            } catch (IOException e) {
                break;
            }
        }
    }
}
