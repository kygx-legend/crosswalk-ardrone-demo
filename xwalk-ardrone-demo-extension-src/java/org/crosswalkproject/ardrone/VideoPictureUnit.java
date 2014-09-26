// Copyright (c) 2013 Intel Corporation. All rights reserved.
// Use omAVFrame this source code is governed by a BSD-style license that can be
// mAVFrameound in the LICENSE mAVFrameile.

package org.crosswalkproject.ardrone;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.twilight.h264.decoder.AVFrame;

public class VideoPictureUnit {
    private static final String TAG = "VideoPictureUnit";

    private int mHeight;
    private int mWidth;

    private AVFrame mAVFrame;

    public VideoPictureUnit(AVFrame avFrame) {
        mAVFrame = avFrame;
        mHeight = avFrame.imageHeight;
        mWidth = avFrame.imageWidth;
    }

    public JSONObject setPicture2JSONObject() {
        JSONObject out = new JSONObject();
        if (mAVFrame == null) {
            return out;
        }

        try {
            JSONArray dataArray = setRGBDataArray();
            Log.i(TAG, "length: " + dataArray.length());
            out.put("height", mHeight);
            out.put("width", mWidth);
            out.put("data", dataArray);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }

        return out;
    }

    private JSONArray setRGBDataArray() throws JSONException{
        JSONArray dataArray = new JSONArray();
        if (mAVFrame == null) {
            return dataArray;
        }

        int[] luma = mAVFrame.data_base[0];
        int[] cb = mAVFrame.data_base[1];
        int[] cr = mAVFrame.data_base[2];
        int stride = mAVFrame.linesize[0];
        int strideChroma = mAVFrame.linesize[1];

        for (int y = 0; y < mHeight; y++) {
            int lineOffLuma = y * stride;
            int lineOffChroma = (y >> 1) * strideChroma;

            for (int x = 0; x < mWidth; x++) {
                int c = luma[lineOffLuma + x] - 16;
                int d = cb[lineOffChroma + (x >> 1)] - 128;
                int e = cr[lineOffChroma + (x >> 1)] - 128;

                int red = (298 * c + 409 * e + 128) >> 8;
                red = red < 0 ? 0 : (red > 255 ? 255 : red);
                int green = (298 * c - 100 * d - 208 * e + 128) >> 8;
                green = green < 0 ? 0 : (green > 255 ? 255 : green);
                int blue = (298 * c + 516 * d + 128) >> 8;
                blue = blue < 0 ? 0 : (blue > 255 ? 255 : blue);
                int alpha = 255;

                dataArray.put(red);
                dataArray.put(green);
                dataArray.put(blue);
                dataArray.put(alpha);
            }
        }

        return dataArray;
    }
}
