package com.arturmaslov.iosixtest;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.iosix.eldblelib.EldBleConnectionStateChangeCallback;
import com.iosix.eldblelib.EldBleDataCallback;
import com.iosix.eldblelib.EldBleError;
import com.iosix.eldblelib.EldBleScanCallback;
import com.iosix.eldblelib.EldBroadcast;
import com.iosix.eldblelib.EldBroadcastTypes;
import com.iosix.eldblelib.EldBufferRecord;
import com.iosix.eldblelib.EldCachedNewTimeRecord;
import com.iosix.eldblelib.EldCachedNewVinRecord;
import com.iosix.eldblelib.EldCachedPeriodicRecord;
import com.iosix.eldblelib.EldDriverBehaviorRecord;
import com.iosix.eldblelib.EldDtcCallback;
import com.iosix.eldblelib.EldEmissionsRecord;
import com.iosix.eldblelib.EldEngineRecord;
import com.iosix.eldblelib.EldFirmwareUpdateCallback;
import com.iosix.eldblelib.EldFuelRecord;
import com.iosix.eldblelib.EldManager;
import com.iosix.eldblelib.EldParameterTypes;
import com.iosix.eldblelib.EldScanObject;
import com.iosix.eldblelib.EldTransmissionRecord;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

/**
 * Review the following URL https://devzone.nordicsemi.com/blogs/1046/what-to-keep-in-mind-when-developing-your-ble-andr/
 * <p>
 * The App presents two sections...both are scrollable lists
 * <p>
 * Top half shows the current execution steps and bottom half simply shows the data received from the ELD
 * once the BLE connection has been established and we are receiving the notification messages
 * <p>
 * This is simply a quick sample to show how one would get data from the ELD. A number of error conditions have not been considered
 * please make sure you consider all the timeout conditions and exceptions in your actual production application
 * <p>
 * Android 6+ requires both coarse and fine location permissions to be able to scan for bluetooth - this is why the app asks for
 * location permission when running for the first time
 */
public class BleConnectActivity extends AppCompatActivity {

    String MAC;

    final Context context = this;
    private int updateSelection;

    private static final int REQUEST_BASE = 100;
    private static final int REQUEST_BT_ENABLE = REQUEST_BASE + 1;
    public TextView mStatusView;
    public TextView mDataView;
    public ScrollView mScrollView;

    private EldManager mEldManager;
    private Set<EldBroadcastTypes> subscribedRecords = EnumSet.of(EldBroadcastTypes.ELD_BUFFER_RECORD, EldBroadcastTypes.ELD_CACHED_RECORD, EldBroadcastTypes.ELD_FUEL_RECORD, EldBroadcastTypes.ELD_DATA_RECORD, EldBroadcastTypes.ELD_DRIVER_BEHAVIOR_RECORD, EldBroadcastTypes.ELD_EMISSIONS_PARAMETERS_RECORD, EldBroadcastTypes.ELD_ENGINE_PARAMETERS_RECORD, EldBroadcastTypes.ELD_TRANSMISSION_PARAMETERS_RECORD);
    private boolean diagnosticEnabled = false, fuelEnabled = false, engineEnabled = false, transmissionEnabled = false, emissionsEnabled = false, driverEnabled = false;

    private boolean exit = false;

    boolean reqdelinprogress = false;
    int startseq, endseq;
    int reccount = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_connect);
        mStatusView = (TextView) findViewById(R.id.statusLog);
        mDataView = (TextView) findViewById(R.id.eldData);
        mScrollView = (ScrollView) findViewById(R.id.scrollView3);

        //Required to allow bluetooth scanning
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        mEldManager = EldManager.GetEldManager(this, "123456789A");

        runOnUiThread(() -> mStatusView.append("Using api version: " + mEldManager.GetApiVersion() + "\n"));

//        ScanForEld();
    }

    private EldDtcCallback dtcCallback = new EldDtcCallback() {
        @Override
        public void onDtcDetected(final String status, final String jsonString) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDataView.append(status);
                    mDataView.append(jsonString);
                    mScrollView.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    };

    private EldFirmwareUpdateCallback fwUpdateCallback = new EldFirmwareUpdateCallback() {
        @Override
        public void onUpdateNotification(final String status) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final String data = status;
                    mDataView.append(status + "\n");
                    mScrollView.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    };


    public void onCheckUpdateClicked(View v) {
        if (v.getId() == R.id.FW_CHECK) {
            final String Status = mEldManager.CheckFirmwareUpdate();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStatusView.append("Current firmware: " + mEldManager.GetFirmwareVersion() + " Available firmware: " + mEldManager.CheckFirmwareUpdate() + "\r\n");
                }
            });
        }
    }

    public void onReqDebugClicked(View v) {
        if (v.getId() == R.id.REQ_DEBUG) {
            EldBleError status = mEldManager.RequestDebugData();
            if (status != EldBleError.SUCCESS) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusView.append("Request Debug Failed\n");
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusView.append("Request Debug Succeeded\n");
                    }
                });
            }
        }
    }

    public void onUpdateFwClicked(View v) {
        if (v.getId() == R.id.updateFw) {

            updateSelection = 0;

            // get prompts.xml view
            LayoutInflater li = LayoutInflater.from(context);
            View promptsView = li.inflate(R.layout.update_popup, null);
            final EditText downloadInput = (EditText) promptsView.findViewById(R.id.downloadInput);
            final EditText localInput = (EditText) promptsView.findViewById(R.id.localInput);
            final RadioGroup radioGroup = (RadioGroup) promptsView.findViewById(R.id.radiogroup);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
            alertDialogBuilder.setView(promptsView);

            alertDialogBuilder
                    .setCancelable(true)
                    .setPositiveButton("START",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    if (updateSelection == 0) {
                                        if (downloadInput.getText().toString().equals("")) {
                                            mEldManager.StartUpdate(fwUpdateCallback, null);
                                        } else if (downloadInput.getText().toString().toUpperCase().equals("FLASHERROR")) {
                                            mEldManager.SendSpecialString("FLASHERROR");
                                        } else {
                                            mEldManager.StartUpdate(fwUpdateCallback, downloadInput.getText().toString());
                                        }
                                    } else {
                                        mEldManager.StartUpdateLocal(fwUpdateCallback, localInput.getText().toString());
                                    }
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });

            radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                                                      public void onCheckedChanged(RadioGroup group, int checkedId) {
                                                          switch (checkedId) {
                                                              case R.id.downloadbutton:
                                                                  updateSelection = 0;
                                                                  break;
                                                              case R.id.localbutton:
                                                                  updateSelection = 1;
                                                                  break;
                                                          }
                                                      }
                                                  }
            );

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();
        }
    }

    public void onEnableParametersClicked(View v) {
        if (v.getId() == R.id.ENABLE_PARAM) {
            LayoutInflater li = LayoutInflater.from(context);
            View promptsView = li.inflate(R.layout.param_popup, null);
            final Switch dtcSwitch = (Switch) promptsView.findViewById(R.id.diagnostics);
            final Switch fuelSwitch = (Switch) promptsView.findViewById(R.id.fuel);
            final Switch engineSwitch = (Switch) promptsView.findViewById(R.id.engine);
            final Switch transmissionSwitch = (Switch) promptsView.findViewById(R.id.transmission);
            final Switch emissionsSwitch = (Switch) promptsView.findViewById(R.id.emissions);
            final Switch driverSwitch = (Switch) promptsView.findViewById(R.id.driver);

            dtcSwitch.setChecked(diagnosticEnabled);
            fuelSwitch.setChecked(fuelEnabled);
            engineSwitch.setChecked(engineEnabled);
            transmissionSwitch.setChecked(transmissionEnabled);
            emissionsSwitch.setChecked(emissionsEnabled);
            driverSwitch.setChecked(driverEnabled);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
            alertDialogBuilder.setView(promptsView);

            alertDialogBuilder
                    .setCancelable(true)
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    if (dtcSwitch.isChecked() && !diagnosticEnabled) {
                                        final EldBleError status = mEldManager.EnableAdditionalParameters(EldParameterTypes.DIAGNOSTIC_PARAMETERS);
                                        subscribedRecords.add(EldBroadcastTypes.ELD_DIAGNOSTIC_RECORD);
                                        diagnosticEnabled = true;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("EnableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    } else if (!dtcSwitch.isChecked() && diagnosticEnabled) {
                                        final EldBleError status = mEldManager.DisableAdditionalParameters(EldParameterTypes.DIAGNOSTIC_PARAMETERS);
                                        subscribedRecords.remove(EldBroadcastTypes.ELD_DIAGNOSTIC_RECORD);
                                        diagnosticEnabled = false;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("DisableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    }

                                    if (fuelSwitch.isChecked() && !fuelEnabled) {
                                        final EldBleError status = mEldManager.EnableAdditionalParameters(EldParameterTypes.FUEL_PARAMETERS);
                                        subscribedRecords.add(EldBroadcastTypes.ELD_FUEL_RECORD);
                                        fuelEnabled = true;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("EnableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    } else if (!fuelSwitch.isChecked() && fuelEnabled) {
                                        final EldBleError status = mEldManager.DisableAdditionalParameters(EldParameterTypes.FUEL_PARAMETERS);
                                        subscribedRecords.remove(EldBroadcastTypes.ELD_FUEL_RECORD);
                                        fuelEnabled = false;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("DisableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    }

                                    if (engineSwitch.isChecked() && !engineEnabled) {
                                        final EldBleError status = mEldManager.EnableAdditionalParameters(EldParameterTypes.ENGINE_PARAMETERS);
                                        subscribedRecords.add(EldBroadcastTypes.ELD_ENGINE_PARAMETERS_RECORD);
                                        engineEnabled = true;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("EnableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    } else if (!engineSwitch.isChecked() && engineEnabled) {
                                        final EldBleError status = mEldManager.DisableAdditionalParameters(EldParameterTypes.ENGINE_PARAMETERS);
                                        subscribedRecords.remove(EldBroadcastTypes.ELD_ENGINE_PARAMETERS_RECORD);
                                        engineEnabled = false;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("DisableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    }

                                    if (transmissionSwitch.isChecked() && !transmissionEnabled) {
                                        final EldBleError status = mEldManager.EnableAdditionalParameters(EldParameterTypes.TRANSMISSION_PARAMETERS);
                                        subscribedRecords.add(EldBroadcastTypes.ELD_TRANSMISSION_PARAMETERS_RECORD);
                                        transmissionEnabled = true;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("EnableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    } else if (!transmissionSwitch.isChecked() && transmissionEnabled) {
                                        final EldBleError status = mEldManager.DisableAdditionalParameters(EldParameterTypes.TRANSMISSION_PARAMETERS);
                                        subscribedRecords.remove(EldBroadcastTypes.ELD_TRANSMISSION_PARAMETERS_RECORD);
                                        transmissionEnabled = false;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("DisableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    }

                                    if (emissionsSwitch.isChecked() && !emissionsEnabled) {
                                        final EldBleError status = mEldManager.EnableAdditionalParameters(EldParameterTypes.EMISSIONS_PARAMETERS);
                                        subscribedRecords.add(EldBroadcastTypes.ELD_EMISSIONS_PARAMETERS_RECORD);
                                        emissionsEnabled = true;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("EnableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    } else if (!emissionsSwitch.isChecked() && emissionsEnabled) {
                                        final EldBleError status = mEldManager.DisableAdditionalParameters(EldParameterTypes.EMISSIONS_PARAMETERS);
                                        subscribedRecords.remove(EldBroadcastTypes.ELD_EMISSIONS_PARAMETERS_RECORD);
                                        emissionsEnabled = false;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("DisableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    }

                                    if (driverSwitch.isChecked() && !driverEnabled) {
                                        final EldBleError status = mEldManager.EnableAdditionalParameters(EldParameterTypes.DRIVER_BEHAVIOR);
                                        subscribedRecords.add(EldBroadcastTypes.ELD_DRIVER_BEHAVIOR_RECORD);
                                        driverEnabled = true;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("EnableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    } else if (!driverSwitch.isChecked() && driverEnabled) {
                                        final EldBleError status = mEldManager.DisableAdditionalParameters(EldParameterTypes.DRIVER_BEHAVIOR);
                                        subscribedRecords.remove(EldBroadcastTypes.ELD_DRIVER_BEHAVIOR_RECORD);
                                        driverEnabled = false;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mStatusView.append("DisableAdditionalParameters (" + status + ")\n");
                                            }
                                        });
                                    }

                                    mEldManager.UpdateSubscribedRecordTypes(subscribedRecords);
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });

            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        }
    }

    public void onSetPeriodClicked(View v) {
        if (v.getId() == R.id.SET_PERIOD) {
            LayoutInflater li = LayoutInflater.from(context);
            View promptsView = li.inflate(R.layout.period_popup, null);
            final EditText periodInput = (EditText) promptsView.findViewById(R.id.periodinput);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
            alertDialogBuilder.setView(promptsView);

            alertDialogBuilder
                    .setCancelable(true)
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    int period = Integer.parseInt(periodInput.getText().toString()) * 1000;
                                    final EldBleError status = mEldManager.SetRecordingInterval(period);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mStatusView.append("Set Period Status (" + status + ")\n");
                                        }
                                    });
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });

            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        }
    }


    public void onReqRecordClicked(View v) {
        if (v.getId() == R.id.reqButton) {
            final EldBleError status = mEldManager.RequestRecord();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStatusView.append("ReqRecordStatus (" + status + ")\n");
                }
            });
        }
    }

    public void onReqDelClicked(View v) {
        if (v.getId() == R.id.reqDelButton) {
//            1. request 10 records, for example from 1 to 10
//            2. Wait until 10th record is received
//            3. call deleteRecord method with parameters 1, 10
//            4. request record 11
//               In that case library does not return 11th record, need to request it few times or wait some time

            reqdelinprogress = true;
            reccount = 0;

            EldBleError err = mEldManager.RequestRecord(startseq, startseq + 9);
            if (err == EldBleError.RECORD_OUT_OF_RANGE) {
                Log.d("TESTING", "Not enough records on device");
            }
        }
    }

    public void onDisconnectClicked(View v) {
        if (v.getId() == R.id.Disconnect) {
            mEldManager.DisconnectEld();
        }
    }

    public void onRescanClicked(View v) {
        if (v.getId() == R.id.Rescan) {

            //request android 12 runtime bluetooth permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 2);
                }
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 2);
                }
            }

            ScanForEld();
        }
    }

    public void onDelRecordClicked(View v) {
        if (v.getId() == R.id.delButton) {
            final EldBleError status = mEldManager.DeleteRecord(startseq, startseq);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStatusView.append("DelRecordStatus (" + status + ")\n");
                }
            });
        }
    }

    public void onSetOdoClicked(View v) {
        if (v.getId() == R.id.SET_ODO) {
            LayoutInflater li = LayoutInflater.from(context);
            View promptsView = li.inflate(R.layout.odo_popup, null);
            final EditText odoInput = (EditText) promptsView.findViewById(R.id.odoinput);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
            alertDialogBuilder.setView(promptsView);

            alertDialogBuilder
                    .setCancelable(true)
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    int odo = Integer.parseInt(odoInput.getText().toString());
                                    if (odo >= 0) {
                                        mEldManager.SetOdometer(odo);
                                    }
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();
        }
    }

    public void onSetTimeClicked(View v) {
        if (v.getId() == R.id.SET_TIME) {
            // get prompts.xml view
            LayoutInflater li = LayoutInflater.from(context);
            View promptsView = li.inflate(R.layout.time_popup, null);
            final EditText timeInput = (EditText) promptsView.findViewById(R.id.timeinput);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
            alertDialogBuilder.setView(promptsView);

            alertDialogBuilder
                    .setCancelable(true)
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    int time = Integer.parseInt(timeInput.getText().toString());
                                    if (time >= 0) {
                                        mEldManager.SetTime(time);
                                    }
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();
        }
    }

    public void onSetHoursClicked(View v) {
        if (v.getId() == R.id.SET_HOURS) {
            // get prompts.xml view
            LayoutInflater li = LayoutInflater.from(context);
            View promptsView = li.inflate(R.layout.hours_popup, null);
            final EditText hoursInput = (EditText) promptsView.findViewById(R.id.hoursinput);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
            alertDialogBuilder.setView(promptsView);

            alertDialogBuilder
                    .setCancelable(true)
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    int hours = Integer.parseInt(hoursInput.getText().toString());
                                    if (hours >= 0) {
                                        mEldManager.SetEngineHours(hours);
                                    }
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();
        }
    }


    public void onBackPressed() {
        if (exit) {
            mEldManager.DisconnectEld();
            finish(); // finish activity
        } else {
            Toast.makeText(this, "Press Back again to Exit.", Toast.LENGTH_SHORT).show();
            exit = true;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    exit = false;
                }
            }, 3 * 1000);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.navigation, menu);
        return true;
    }

    private EldBleConnectionStateChangeCallback bleConnectionStateChangeCallback = new EldBleConnectionStateChangeCallback() {
        @Override
        public void onConnectionStateChange(final int newState) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDataView.append("New State of connection" + Integer.toString(newState, 10) + "\n");
                }
            });
        }
    };

    private EldBleDataCallback bleDataCallback = new EldBleDataCallback() {
        @Override
        public void OnDataRecord(final EldBroadcast dataRec, final EldBroadcastTypes RecordType) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    mDataView.append(dataRec.getBroadcastString().trim() + "\r\n");
                    Log.d("TESTING", dataRec.getBroadcastString().trim());

                    if (dataRec instanceof EldBufferRecord) {
                        startseq = ((EldBufferRecord) dataRec).getStartSeqNo();
                        endseq = ((EldBufferRecord) dataRec).getEndSeqNo();
                    } else if (RecordType == EldBroadcastTypes.ELD_DATA_RECORD) {
//                        mDataView.append("RPM: " + Double.toString(((EldDataRecord) (dataRec)).getRpm()));
//                        mDataView.append(" Satellites: " + Double.toString(((EldDataRecord) (dataRec)).getNumSats()));
//                        mDataView.append(" Latitude: " + Double.toString(((EldDataRecord) (dataRec)).getLatitude()));
//                        mDataView.append(" Longitude: " + Double.toString(((EldDataRecord) (dataRec)).getLongitude()));
//                        mDataView.append(" Firmware: " + ((EldDataRecord) (dataRec)).getFirmwareVersion() + "\n");

                    } else if (RecordType == EldBroadcastTypes.ELD_CACHED_RECORD) {
                        //Shows how to get to the specific record types created based on the broadcast info

                        mStatusView.append(dataRec.getBroadcastString());

                        if (reqdelinprogress) {
                            reccount++;
                            Log.d("TESTING", "received " + reccount + " records");
                            if (reccount == 10) {
                                Log.d("TESTING", "delete " + startseq + "-" + (startseq + 9));
                                mEldManager.DeleteRecord(startseq, startseq + 9);
                                Log.d("TESTING", "request " + (startseq + 10));
                                mEldManager.RequestRecord(startseq + 10, startseq + 10);
                            } else if (reccount == 11) {
                                Log.d("TESTING", "success!");

                                reqdelinprogress = false;
                                reccount = 0;
                            }
                        }

                        if (dataRec instanceof EldCachedPeriodicRecord) {

//                            Log.d("TESTING", "Odometer " + ((EldCachedPeriodicRecord) (dataRec)).getOdometer());
//                            Log.d("TESTING", "Engine Hours " + ((EldCachedPeriodicRecord) (dataRec)).getEngineHours());
//                            Log.d("TESTING", "RPM " + ((EldCachedPeriodicRecord) (dataRec)).getRpm());
//                            Log.d("TESTING", "Satellites " + ((EldCachedPeriodicRecord) (dataRec)).getNumSats());
//                            Log.d("TESTING", "Lat " + ((EldCachedPeriodicRecord) (dataRec)).getLatitude());
//                            Log.d("TESTING", "Lon " + ((EldCachedPeriodicRecord) (dataRec)).getLongitude());
//                            Log.d("TESTING", "Unix Time " + ((EldCachedPeriodicRecord) (dataRec)).getUnixTime());
//                            Log.d("TESTING", "Sequence Number " + ((EldCachedPeriodicRecord) (dataRec)).getSeqNum());

                            // mDataView.append("CACHED REC"+((EldCachedPeriodicRecord)(dataRec)).getBroadcastString());

                        } else if (dataRec instanceof EldCachedNewTimeRecord) {
                            ((EldCachedNewTimeRecord) (dataRec)).getNewUnixTime();
                        } else if (dataRec instanceof EldCachedNewVinRecord) {
//                            Log.d("TESTING", "Vin " + ((EldCachedNewVinRecord) (dataRec)).getVin());
//                            Log.d("TESTING", "Odometer " + ((EldCachedNewVinRecord) (dataRec)).getOdometer());
//                            Log.d("TESTING", "Engine Hours " + ((EldCachedNewVinRecord) (dataRec)).getEngineHours());
//                            Log.d("TESTING", "Unix Time " + ((EldCachedNewVinRecord) (dataRec)).getUnixTime());
//                            Log.d("TESTING", "Sequence Number " + ((EldCachedNewVinRecord) (dataRec)).getSeqNum());
                        }

                    } else if (RecordType == EldBroadcastTypes.ELD_DRIVER_BEHAVIOR_RECORD) {
                        EldDriverBehaviorRecord rec = (EldDriverBehaviorRecord) dataRec;
                        driverEnabled = true;
                        if (rec instanceof EldDriverBehaviorRecord) {
                            ((EldDriverBehaviorRecord) (rec)).getAbsStatus();
                            //((EldCachedPeriodicRecord)(rec)).getUnixTime();

//                            mStatusView.append("" + rec.getCruiseSetSpeed_kph() + " ");
//                            mStatusView.append("" + rec.getCruiseStatus() + " ");
//                            mStatusView.append("" + rec.getThrottlePosition_pct() + " ");
//                            mStatusView.append("" + rec.getAcceleratorPosition_pct() + " ");
//                            mStatusView.append("" + rec.getBrakePosition_pct() + " ");
//                            mStatusView.append("" + rec.getSeatBeltStatus() + " ");
//                            mStatusView.append("" + rec.getSteeringWheelAngle_deg() + " ");
//                            mStatusView.append("" + rec.getAbsStatus() + " ");
//                            mStatusView.append("" + rec.getTractionStatus() + " ");
//                            mStatusView.append("" + rec.getStabilityStatus() + " ");
//                            mStatusView.append("" + rec.getBrakeSystemPressure_kpa() + " ");
//                            mStatusView.append("\n");

                            // mDataView.append("CACHED REC"+((EldCachedPeriodicRecord)(rec)).getBroadcastString());

                        }
                    } else if (RecordType == EldBroadcastTypes.ELD_ENGINE_PARAMETERS_RECORD) {
                        EldEngineRecord rec = (EldEngineRecord) dataRec;
                        engineEnabled = true;

//                        mStatusView.append("" + rec.getOilPressure_kpa() + " ");
//                        mStatusView.append("" + rec.getTurboBoost_kpa() + " ");
//                        mStatusView.append("" + rec.getIntakePressure_kpa() + " ");
//                        mStatusView.append("" + rec.getFuelPressure_kpa() + " ");
//                        mStatusView.append("" + rec.getCrankCasePressure_kpa() + " ");
//                        mStatusView.append("" + rec.getLoad_pct() + " ");
//                        mStatusView.append("" + rec.getMassAirFlow_galPerSec() + " ");
//                        mStatusView.append("" + rec.getTurboRpm() + " ");
//                        mStatusView.append("" + rec.getIntakeTemp_c() + " ");
//                        mStatusView.append("" + rec.getEngineCoolantTemp_c() + " ");
//                        mStatusView.append("" + rec.getEngineOilTemp_c() + " ");
//                        mStatusView.append("" + rec.getFuelTemp_c() + " ");
//                        mStatusView.append("" + rec.getChargeCoolerTemp_c() + " ");
//                        mStatusView.append("" + rec.getTorgue_Nm() + " ");
//                        mStatusView.append("" + rec.getEngineOilLevel_pct() + " ");
//                        mStatusView.append("" + rec.getEngineCoolandLevel_pct() + " ");
//                        mStatusView.append("" + rec.getTripFuel_L() + " ");
//                        mStatusView.append("" + rec.getDrivingFuelEconomy_LPerKm() + " ");
//                        mStatusView.append("\n");

                        //mDataView.append("Engine Rec was sent" + ((EldEngineRecord) (rec)).getBroadcastString());


                    } else if (RecordType == EldBroadcastTypes.ELD_EMISSIONS_PARAMETERS_RECORD) {
                        EldEmissionsRecord rec = (EldEmissionsRecord) dataRec;
                        emissionsEnabled = true;

//                        mStatusView.append("" + rec.getNOxInlet() + " ");
//                        mStatusView.append("" + rec.getNOxOutlet() + " ");
//                        mStatusView.append("" + rec.getAshLoad() + " ");
//                        mStatusView.append("" + rec.getDpfSootLoad() + " ");
//                        mStatusView.append("" + rec.getDpfRegenStatus() + " ");
//                        mStatusView.append("" + rec.getDpfDifferentialPressure() + " ");
//                        mStatusView.append("" + rec.getEgrValvePosition() + " ");
//                        mStatusView.append("" + rec.getAfterTreatmentFuelPressure() + " ");
//                        mStatusView.append("" + rec.getEngineExhaustTemperature() + " ");
//                        mStatusView.append("" + rec.getExhaustTemperature1() + " ");
//                        mStatusView.append("" + rec.getExhaustTemperature2() + " ");
//                        mStatusView.append("" + rec.getExhaustTemperature3() + " ");
//                        mStatusView.append("" + rec.getDefFluidLevel() + " ");
//                        mStatusView.append("" + rec.getDefTankTemperature() + " ");
//                        mStatusView.append("" + rec.getScrInducementFaultStatus() + " ");
//                        mStatusView.append("\n");


                    } else if (RecordType == EldBroadcastTypes.ELD_TRANSMISSION_PARAMETERS_RECORD) {
                        EldTransmissionRecord rec = (EldTransmissionRecord) dataRec;
                        transmissionEnabled = true;

//                        mStatusView.append("" + rec.getOutputShaftRpm() + " ");
//                        mStatusView.append("" + rec.getGearStatus() + " ");
//                        mStatusView.append("" + rec.getRequestGearStatus() + " ");
//                        mStatusView.append("" + rec.getTransmissionOilTemp_c() + " ");
//                        mStatusView.append("" + rec.getTorqueConverterLockupStatus() + " ");
//                        mStatusView.append("" + rec.getTorqueConverterOilOutletTemp_c() + " ");
//                        mStatusView.append("\n");

                    } else if (RecordType == EldBroadcastTypes.ELD_FUEL_RECORD) {
                        EldFuelRecord rec = (EldFuelRecord) dataRec;
                        fuelEnabled = true;

//                        mStatusView.append("" + rec.getFuelLevelPercent() + " ");
//                        mStatusView.append("" + rec.getFuelIntegratedLiters() + " ");
//                        mStatusView.append("" + rec.getTotalFuelConsumedLiters() + " ");
//                        mStatusView.append("" + rec.getFuelRateLitersPerHours() + " ");
//                        mStatusView.append("" + rec.getIdleFuelConsumedLiters() + " ");
//                        mStatusView.append("" + rec.getIdleTimeHours() + " ");
//                        mStatusView.append("" + rec.getStateHighRPM() + " ");
//                        mStatusView.append("" + rec.getStateUnsteady() + " ");
//                        mStatusView.append("" + rec.getStateEnginePower() + " ");
//                        mStatusView.append("" + rec.getStateAccel() + " ");
//                        mStatusView.append("" + rec.getStateEco() + " ");
//                        mStatusView.append("" + rec.getStateAnticipate() + " ");
//                        mStatusView.append("\n");

                    } else if (RecordType == EldBroadcastTypes.ELD_DIAGNOSTIC_RECORD) {
                        diagnosticEnabled = true;
                    }

                    mScrollView.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    };

    private EldBleScanCallback bleScanCallback = new EldBleScanCallback() {

        @Override
        public void onScanResult(EldScanObject device) {

            Log.d("BLETEST", "BleScanCallback single");

            final String strDevice;
            if (device != null) {
                strDevice = device.getDeviceId();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusView.append("ELD " + strDevice + " found, now connecting...\n");
                    }
                });

                EldBleError res = mEldManager.ConnectToEld(bleDataCallback, subscribedRecords, bleConnectionStateChangeCallback);

                if (res != EldBleError.SUCCESS) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mStatusView.append("Connection Failed\n");
                        }
                    });
                }

            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusView.append("No ELD found\n");
                    }
                });
            }
        }

        @Override
        public void onScanResult(ArrayList deviceList) {

            Log.d("BLETEST", "BleScanCallback multiple");

            final String strDevice;
            EldScanObject so;

            if (deviceList != null) {
                so = (EldScanObject) deviceList.get(0);
                strDevice = so.getDeviceId();
                MAC = strDevice;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusView.append("ELD " + strDevice + " found, now connecting...\n");
                    }
                });

                EldBleError res = mEldManager.ConnectToEld(bleDataCallback, subscribedRecords, bleConnectionStateChangeCallback, strDevice);

                if (res != EldBleError.SUCCESS) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mStatusView.append("Connection Failed\n");
                        }
                    });
                }

            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusView.append("No ELD found\n");
                    }
                });
            }
        }
    };

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BT_ENABLE) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                mStatusView.append("Bluetooth enabled - now scanning for ELD\n");
                mEldManager.ScanForElds(bleScanCallback);
            } else {
                mStatusView.append("Unable to enable bluetooth\n");
            }
        }
    }

    /*
    Scan for the ELD using EldBleLib  - if Bluetooth is not enabled it will return a NOT_ENABLED Error
    in which case invoke EnableBluetooth to enable bluetooth and in the custom intent on success
    invoke the scan functions
     */
    private void ScanForEld() {
        if (mEldManager.ScanForElds(bleScanCallback) == EldBleError.BLUETOOTH_NOT_ENABLED)
            mEldManager.EnableBluetooth(REQUEST_BT_ENABLE);
    }

}
