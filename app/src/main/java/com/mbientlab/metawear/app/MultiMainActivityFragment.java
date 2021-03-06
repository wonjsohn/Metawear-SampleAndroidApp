/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 */

package com.mbientlab.metawear.app;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Debug;
import com.mbientlab.metawear.module.Switch;

import java.io.File;
import java.util.HashMap;

/**
 * A placeholder fragment containing a simple view.
 */
public class MultiMainActivityFragment extends Fragment implements ServiceConnection{
    private final Handler taskScheduler;
    private final HashMap<DeviceState, MetaWearBoard> stateToBoards;
    private MetaWearBleService.LocalBinder binder;

    private ConnectedDevicesAdapter connectedDevices= null;

    public MultiMainActivityFragment() {
        super();
        stateToBoards = new HashMap<>();
        taskScheduler= new Handler();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Activity owner= getActivity();// this Fragment activity.
        owner.getApplicationContext().bindService(new Intent(owner, MetaWearBleService.class), this, Context.BIND_AUTO_CREATE);

//
//        GyroFragment gyro_fragment = new GyroFragment();
//        // update the main content by replacing fragments
//        FragmentManager fragmentManager = getFragmentManager();
//        FragmentTransaction fragmentTransaction= fragmentManager.beginTransaction();
//
//        fragmentTransaction.add(R.id.multimain_activity_content, gyro_fragment);
//        fragmentTransaction.commit();

//        GyroFragment gyro_fragment = new GyroFragment();
//        fragmentTransaction.add(R.id.nav_gyro, gyro_fragment); // check nav_gyro
//        fragmentTransaction.commit();

    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        getActivity().getApplicationContext().unbindService(this);
    }


    public void addNewDevice(BluetoothDevice btDevice) {
        final DeviceState newDeviceState= new DeviceState(btDevice);
        final MetaWearBoard newBoard= binder.getMetaWearBoard(btDevice);

        newDeviceState.connecting= true;
        connectedDevices.add(newDeviceState);

        stateToBoards.put(newDeviceState, newBoard);
        //The device’s connection state is controlled with the connect and disconnect functions.
        //Notifications about the connection state are handled by the ConnectionStateHandler class.
        newBoard.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
            @Override
            public void connected() {
                newDeviceState.connecting = false;
                connectedDevices.notifyDataSetChanged();

                try {
                    //AsyncOperation: This class acts as an observer of the task,
                    // notifying the user asynchronously when the task is complete.
                    newBoard.getModule(Switch.class).routeData().fromSensor().stream("switch_stream").commit()
                            .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                @Override
                                //success: request was successful and passes the result to the caller
                                public void success(RouteManager result) {
                                    result.subscribe("switch_stream", new RouteManager.MessageHandler() {
                                        @Override
                                        public void process(Message msg) {
                                            newDeviceState.pressed = msg.getData(Boolean.class);
                                            connectedDevices.notifyDataSetChanged();
                                        }
                                    });
                                }
                            });
                    final Accelerometer accelModule = newBoard.getModule(Accelerometer.class);
                    accelModule.routeData().fromOrientation().stream("orientation_stream").commit()
                            .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                @Override
                                public void success(RouteManager result) {
                                    result.subscribe("orientation_stream", new RouteManager.MessageHandler() {
                                        @Override
                                        public void process(Message msg) {
                                            newDeviceState.deviceOrientation = msg.getData(Accelerometer.BoardOrientation.class).toString();
                                            connectedDevices.notifyDataSetChanged();
                                        }
                                    });
                                    accelModule.enableOrientationDetection();
                                    accelModule.start();
                                }
                            });
                } catch (UnsupportedModuleException e) {
                    Snackbar.make(getActivity().findViewById(R.id.activity_multimain_layout), e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void disconnected() {
                connectedDevices.remove(newDeviceState);
            }

            @Override
            public void failure(int status, Throwable error) {
                connectedDevices.remove(newDeviceState);
            }
        });
        newBoard.connect();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        connectedDevices= new ConnectedDevicesAdapter(getActivity(), R.id.metawear_status_layout);
        connectedDevices.setNotifyOnChange(true);
        setRetainInstance(true);
        return inflater.inflate(R.layout.fragment_multimain, container, false);
    }




    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        ListView connectedDevicesView= (ListView) view.findViewById(R.id.connected_devices);
        connectedDevicesView.setAdapter(connectedDevices);
        connectedDevicesView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           int position, long id) {
                DeviceState current= connectedDevices.getItem(position);
                final MetaWearBoard selectedBoard= stateToBoards.get(current);

                try {
                    Accelerometer accelModule = selectedBoard.getModule(Accelerometer.class);
                    accelModule.stop();
                    accelModule.disableOrientationDetection();

                    selectedBoard.removeRoutes();
                    selectedBoard.getModule(Debug.class).disconnect();
                } catch (UnsupportedModuleException e) {
                    // Not a big deal if the try catch fails
                    Log.w("multimw", e);

                    taskScheduler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            selectedBoard.disconnect();
                        }
                    }, 100);
                }

                connectedDevices.remove(current);
                return false;
            }
        });
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder= (MetaWearBleService.LocalBinder) service;
        binder.executeOnUiThread();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
}
