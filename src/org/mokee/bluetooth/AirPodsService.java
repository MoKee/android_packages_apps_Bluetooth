/*
 * Copyright (C) 2019 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mokee.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class AirPodsService extends Service {

    private static final String TAG = "AirPodsService";

    private static final int MANUFACTURER_ID = 0x004C;
    private static final int MANUFACTURER_MAGIC = 0x07;

    private static final int DATA_LENGTH_PAIRING = 15;
    private static final int DATA_LENGTH_BATTERY = 25;

    private static final long REPORT_DELAY_MS = 500;

    private static final int FLAG_RIGHTLEFT = 1 << 7;

    private static final int MASK_CHARGING_RIGHT = 1 << 4;
    private static final int MASK_CHARGING_LEFT = 1 << 5;
    private static final int MASK_CHARGING_CASE = 1 << 6;

    private BluetoothAdapter mAdapter;
    private BluetoothLeScanner mScanner;

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onBatchScanResults(List<ScanResult> scanResults) {
            for (ScanResult result : scanResults) {
                handleScanResult(result);
            }
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            handleScanResult(result);
        }
    };

    public static Intent getIntent(Context context) {
        return new Intent(context, AirPodsService.class);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        if (mScanner != null) {
            mScanner.stopScan(mScanCallback);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");
        startScan();
        return START_STICKY;
    }

    private void startScan() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mAdapter == null) {
            Log.w(TAG, "BluetoothAdapter is null, ignored");
            return;
        }

        mScanner = mAdapter.getBluetoothLeScanner();
        if (mScanner == null) {
            Log.w(TAG, "BluetoothLeScanner is null, ignored");
            return;
        }

        final List<ScanFilter> filters = new ArrayList<>();

        byte[] filterMaskPairing = new byte[2 + DATA_LENGTH_PAIRING];
        filterMaskPairing[0] = MANUFACTURER_MAGIC;
        filterMaskPairing[1] = DATA_LENGTH_PAIRING;
        filters.add(new ScanFilter.Builder()
                .setManufacturerData(MANUFACTURER_ID, filterMaskPairing, filterMaskPairing)
                .build());

        byte[] filterMaskBattery = new byte[2 + DATA_LENGTH_BATTERY];
        filterMaskBattery[0] = MANUFACTURER_MAGIC;
        filterMaskBattery[1] = DATA_LENGTH_BATTERY;
        filters.add(new ScanFilter.Builder()
                .setManufacturerData(MANUFACTURER_ID, filterMaskBattery, filterMaskBattery)
                .build());

        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(REPORT_DELAY_MS)
                .build();

        mScanner.startScan(filters, settings, mScanCallback);
        Log.v(TAG, "startScan");
    }

    private void handleScanResult(ScanResult result) {
        final ScanRecord record = result.getScanRecord();
        if (record == null) {
            return;
        }

        final byte[] data = record.getManufacturerSpecificData(MANUFACTURER_ID);
        if (data == null || data.length < 2 || data.length != data[1] + 2) {
            return;
        }

        if (data[1] == DATA_LENGTH_BATTERY) {
            handleBattery(result, data);
        }
    }

    private void handleBattery(ScanResult result, byte[] data) {
        final int flags = data[5];
        final int earpiece = data[6];
        final int charger = data[7];

        final boolean rightLeft = ((flags & FLAG_RIGHTLEFT) != 0);
        final int battLeft = (rightLeft ? earpiece : (earpiece >> 4)) & 0x0f;
        final int battRight = (rightLeft ? (earpiece >> 4) : earpiece) & 0x0f;
        final int battCase = charger & 0x0f;

        final boolean chagLeft = (charger & (rightLeft ? MASK_CHARGING_RIGHT : MASK_CHARGING_LEFT)) != 0;
        final boolean chagRight = (charger & (rightLeft ? MASK_CHARGING_LEFT : MASK_CHARGING_RIGHT)) != 0;
        final boolean chagCase = (charger & MASK_CHARGING_CASE) != 0;

        Log.d(TAG, String.format("status: %d(%s), %d(%s), %d(%s)",
                battLeft, chagLeft, battRight, chagRight, battCase, chagCase));
    }

}
