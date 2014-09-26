// Copyright (c) 2013 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.crosswalkproject.ardrone;

import android.util.Log;

import java.io.InputStream;
import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import com.twilight.h264.decoder.AVFrame;
import com.twilight.h264.decoder.AVPacket;
import com.twilight.h264.decoder.H264Decoder;
import com.twilight.h264.decoder.MpegEncContext;

public class VideoStreamManager implements Runnable {
    public static final int VIDEO_PORT = 5555;

    private static final int INBUF_SIZE = 65535;
    private static final String TAG = "VideoStreamManager";

    private ARDrone mARDrone;
    private H264Decoder mH264Decoder;
    private InputStream mInputStream;
    private MpegEncContext mMpegEncContext;

    public VideoStreamManager(ARDrone instance, InputStream inputStream) {
        mARDrone = instance;
        mInputStream = inputStream;
    }

    @Override
    public void run() {
        try {
            parseVideoStream();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    private void parseVideoStream() throws IOException {
        AVFrame mAVFrame;
        mH264Decoder = new H264Decoder();
        mMpegEncContext = MpegEncContext.avcodec_alloc_context();
        mAVFrame = AVFrame.avcodec_alloc_frame();

        if ((mH264Decoder.capabilities & H264Decoder.CODEC_CAP_TRUNCATED) != 0) {
            mMpegEncContext.flags |= MpegEncContext.CODEC_FLAG_TRUNCATED;
        }

        if (mMpegEncContext.avcodec_open(mH264Decoder) < 0) {
            Log.e(TAG, "could not open codec");
        }

        // Find 1st NAL.
        int[] cacheRead = new int[3];
        cacheRead[0] = mInputStream.read();
        cacheRead[1] = mInputStream.read();
        cacheRead[2] = mInputStream.read();

        while(!(cacheRead[0] == 0x00 && cacheRead[1] == 0x00 && cacheRead[2] == 0x01)) {
            cacheRead[0] = cacheRead[1];
            cacheRead[1] = cacheRead[2];
            cacheRead[2] = mInputStream.read();
        }

        boolean hasMoreNAL = true;
        int[] bufferedInt = new int[INBUF_SIZE + MpegEncContext.FF_INPUT_BUFFER_PADDING_SIZE];
        bufferedInt[0] = bufferedInt[1] = bufferedInt[2] = 0x00;
        bufferedInt[3] = 0x01;

        // Find next NAL.
        while(hasMoreNAL) {
            int dataPointer = 4;
            cacheRead[0] = mInputStream.read();
            if (cacheRead[0] == -1) {
                hasMoreNAL = false;
            }
            cacheRead[1] = mInputStream.read();
            if (cacheRead[1] == -1) {
                hasMoreNAL = false;
            }
            cacheRead[2] = mInputStream.read();
            if (cacheRead[2] == -1) {
                hasMoreNAL = false;
            }

            while(!(cacheRead[0] == 0x00 && cacheRead[1] == 0x00 && cacheRead[2] == 0x01)
                    && hasMoreNAL) {
                bufferedInt[dataPointer++] = cacheRead[0];
                cacheRead[0] = cacheRead[1];
                cacheRead[1] = cacheRead[2];
                cacheRead[2] = mInputStream.read();
                if (cacheRead[2] == -1) {
                    hasMoreNAL = false;
                }
            }

            AVPacket avPacket = new AVPacket();
            avPacket.av_init_packet();
            avPacket.size = dataPointer;
            avPacket.data_base = bufferedInt;
            avPacket.data_offset = 0;

            while (avPacket.size > 0) {
                int[] gotPicture = new int[1];
                int length = mMpegEncContext.avcodec_decode_video2(mAVFrame, gotPicture, avPacket);
                if (length < 0) {
                    Log.e(TAG, "Error while decoding frame...");
                    break;
                }

                if (gotPicture[0] != 0) {
                    mAVFrame = mMpegEncContext.priv_data.displayPicture;
                    VideoPictureUnit picture = new VideoPictureUnit(mAVFrame);                    
                    JSONObject out = new JSONObject();
                    try {
                        out.put("reply", "videoStreaming");
                        out.put("eventName", "videostreaming");
                        out.put("data", picture.setPicture2JSONObject());
                        mARDrone.broadcastMessage(out.toString());
                    } catch (JSONException e) {
                        Log.e(TAG, e.toString());
                    }
                }

                avPacket.size -= length;
                avPacket.data_offset += length;
            }
        }

        mMpegEncContext.avcodec_close();
    }
}
