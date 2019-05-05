package com.linkrosslab.funheartrateapp;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;
import com.github.anastr.speedviewlib.AwesomeSpeedometer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import pl.droidsonroids.gif.GifImageView;



// Main activity
public class HomeActivity extends Activity implements OnItemSelectedListener {


    // Constants
    private final int MAX_SIZE = 60; //graph max size
    private final String HRUUID_SERVICE = "0000180D-0000-1000-8000-00805F9B34FB";              // this is the Heart Rate UUID for Heart Rate "Service"
    private final String HRUUID_CHARACTERISTIC = "00002a37-0000-1000-8000-00805f9b34fb"; // this is the Heart Rate Characteristic UUID for rate measurement
    private final String TAG = "HomeActivity";
    private final int MAX_GAP = 200; // max distance between the bird and the diamond
    private final int MIN_GAP = 20; // max distance between the bird and the diamond

    // layout views
    private XYPlot plot;
    private AwesomeSpeedometer speedometer;
    private ProgressBar progressBar;
    private Spinner spinner;
    private ImageView buttonRefresh;
    private TextView numDiamond;
    private GifImageView bird;

    // variables
    private List<BleDevice> pairedDevices = new ArrayList<>();
    private List<String> deviceList = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private  BleDevice connectedDevice;
    private MediaPlayer mp;

    private int connectedIndex = 0;
    private int reconnectAllowance = 5;
    private int intNumDiamond = 0;
    private int lastBeat = 120;



    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Log.i(TAG, "Starting heart rate monitor at the main activity");

        speedometer = (AwesomeSpeedometer) findViewById(R.id.speedView);
        spinner = (Spinner) findViewById(R.id.spinner);
        buttonRefresh = (ImageView) findViewById(R.id.buttonRefresh);
        numDiamond = (TextView) findViewById(R.id.numDiamond);
        bird = (GifImageView) findViewById(R.id.bird);

        mp = MediaPlayer.create(this, R.raw.sound);
        numDiamond.setText(Integer.toString(intNumDiamond));


        buttonRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanForBTDevices();
            }
        });

        BleManager.getInstance().init(getApplication());
        BleManager.getInstance()
                .enableLog(true)
                .setReConnectCount(5, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(5000);


        progressBar = (ProgressBar) findViewById(R.id.progressBar);


        spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, deviceList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setOnItemSelectedListener(this);
        spinner.setAdapter(spinnerAdapter);


        scanForBTDevices();


        // Create Graph
        plot = (XYPlot) findViewById(R.id.dynamicPlot);

        if (plot.getSeriesSet().size() == 0) {
            Number[] series1Numbers = {};
            DataHandler.getInstance().setSeries1(new SimpleXYSeries(Arrays.asList(series1Numbers), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Heart Rate"));
        }
        plot.setBackgroundColor(getResources().getColor(R.color.colorPrimary));

        //LOAD Graph
        LineAndPointFormatter series1Format = new LineAndPointFormatter(Color.rgb(255, 255, 255), Color.rgb(50, 50, 50), getResources().getColor(R.color.colorAccent), null);
        series1Format.setPointLabelFormatter(new PointLabelFormatter());
        plot.setDrawingCacheBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));

        plot.addSeries(DataHandler.getInstance().getSeries1(), series1Format);
        plot.setTicksPerRangeLabel(3);

        plot.getGraphWidget().setDomainLabelOrientation(-45);
    }


    /**
     * When the option is selected in the dropdown we turn on the bluetooth
     */
    public void onItemSelected(AdapterView<?> arg0, View view, int position, long id) {
        connectedIndex = position;

        if (position > 0 && position <= pairedDevices.size()) {
            Log.v(TAG, ", selected device name = " + pairedDevices.get(position-1).getName());
            connectToDevice(pairedDevices.get(position-1));
        }

    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }



    public void onStart() {
        super.onStart();
    }



    /**
     * Run on startup to list bluetooth paired device
     */
    public void scanForBTDevices() {
        Log.v(TAG, "Listing BT elements");

        // cleanup existing list
        deviceList.clear();
        deviceList.add("Select A Device To Connect");
        spinner.setSelection(0);
        spinnerAdapter.notifyDataSetChanged();

        pairedDevices.clear();



        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "please enable bluetooth on your phone", Toast.LENGTH_LONG).show();
            return;
        }

        UUID[] uuids = new UUID[1];
        uuids[0] = UUID.fromString(HRUUID_SERVICE);

        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
                .setServiceUuids(uuids)
                .setAutoConnect(true)
                .setScanTimeOut(10000) // 10 sec
                .build();

        BleManager.getInstance()
                .setReConnectCount(5, 2000)
//                .setConnectOverTime(10000)
//                .setOperateTimeout(5000)
                .initScanRule(scanRuleConfig);

        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {
                progressBar.setVisibility(View.GONE);

                if(deviceList.size() == 1) {
                    Toast.makeText(HomeActivity.this, "No device detected. Try pressing the refresh button to scan again!", Toast.LENGTH_LONG).show();
                }
                else {
                    Toast.makeText(HomeActivity.this, "Please select a device from dropdown menu to connect", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onScanStarted(boolean success) {
                progressBar.setVisibility(View.VISIBLE);
                Log.v(TAG, "onScanStarted");
                Toast.makeText(HomeActivity.this, "Please be patient as this may take a few seconds", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                Log.v(TAG, "onScanStarted, index = " + deviceList.size());

                String status = "";
                if(BleManager.getInstance().isConnected(bleDevice)) {
                    Log.v(TAG, "onScanStarted already connected to device " + deviceList.size() + ", set selected index to it");
                    status = "(connected) ";
                    connectedDevice = bleDevice;
                    connectedIndex = deviceList.size();

                    spinner.setSelection(connectedIndex);
                }
                deviceList.add(status + bleDevice.getName() + ": " + bleDevice.getMac());
                spinnerAdapter.notifyDataSetChanged();

                pairedDevices.add(bleDevice);
            }
        });
    }

    private void connectToDevice(final BleDevice device) {
        Log.v(TAG, "connectToDevice");

        if(device != null) {

            boolean isConnected = BleManager.getInstance().isConnected(device);
            Log.i(TAG, "connectToDevice .... connecting, connected already ? " + isConnected );

            if(isConnected) {
                connectedDevice = device;
                startGettingNotificationFromDevice();
            }
            else {

                BleManager.getInstance().connect(device, new BleGattCallback() {
                    @Override
                    public void onStartConnect() {
                        Toast.makeText(HomeActivity.this, "Trying to connect to device. Please wait ...", Toast.LENGTH_LONG).show();
                        Log.v(TAG, "onStartConnect");
                        progressBar.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onConnectFail(BleDevice bleDevice, BleException exception) {
                        Log.v(TAG, "onConnectFail " + exception.getDescription());
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(HomeActivity.this, "Sorry. Connection failed. Try connecting again ... ", Toast.LENGTH_LONG).show();

                        if(reconnectAllowance > 0) {
                            reconnectAllowance--;
                            connectToDevice(bleDevice);
                        }
                        else{
                            spinner.setSelection(0);
                        }
                    }

                    @Override
                    public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                        Log.v(TAG, "onConnectSuccess ---- setting selection to index " + connectedIndex);
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(HomeActivity.this, "Hooray! You're connected : )", Toast.LENGTH_LONG).show();
                        connectedDevice = device;
                        startGettingNotificationFromDevice();

                        if(connectedIndex  > 0 && connectedIndex < deviceList.size()) {
                             String name = deviceList.get(connectedIndex);

                             if(!name.contains("connected")) {
                                 name = "(connected) " + name;
                             }
                             deviceList.set(connectedIndex, name);
                        }
                        reconnectAllowance = 5;
                    }

                    @Override
                    public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
                        Log.v(TAG, "onDisConnected");
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(HomeActivity.this, "Oops. Device disconnected...", Toast.LENGTH_LONG).show();
                        if(reconnectAllowance > 0) {
                            reconnectAllowance--;
                            connectToDevice(bleDevice);
                        }
                        else{
                            spinner.setSelection(0);
                        }
                    }
                });

            }
        }
    }



    
    /**
     * Update UI with new heart beat value
     */
    public void updateUIWithData() {

        runOnUiThread(new Runnable() {
            public void run() {

                int beat = DataHandler.getInstance().getLastIntValue();

                if(speedometer != null) {
                    speedometer.speedTo(beat);
                }
                
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) bird.getLayoutParams();

                // we've reached the diamond from last heart beat
                // reset the bird to original location (bottom)
                if(params.topMargin <= MIN_GAP) {
                    params.topMargin = MAX_GAP;
                    bird.setLayoutParams(params);
                    return;
                }


                // below are the math for the bird game
                int shift = 0;

                if(beat > lastBeat) {
                    shift = (beat - lastBeat) * -20; // quicker to go up
                    // no big jump, make the step shift capped at -80
                    if(shift < -80) {
                        shift = -80;
                    }
                }
                else{
                    shift = (beat - lastBeat) * -15; // slower to go down
                    // no big jump, make the step shift capped at +60
                    if(shift > 60) {
                        shift = 60;
                    }
                }


                int beforeMargin = params.topMargin;
                int afterMargin = params.topMargin + shift;

                // distance between the bird and diamond should be between 20 ~ 220
                if(afterMargin < MIN_GAP) {
                    afterMargin = MIN_GAP;
                }
                else if (afterMargin > MAX_GAP) {
                    afterMargin = MAX_GAP;
                }

                // update the UI
                params.topMargin = afterMargin;
                bird.setLayoutParams(params);

                Log.v(TAG, "BMP = " + beat + ", changing gap from " + beforeMargin + "  to " + params.topMargin);

                // reached the diamond, reward the user
                if(afterMargin <= MIN_GAP) {
                    Toast.makeText(HomeActivity.this, "Congrats! You just won another diamond! Keep going!", Toast.LENGTH_LONG).show();
                    numDiamond.setText(Integer.toString(++intNumDiamond));
                    mp.start();
                }

                lastBeat = beat;


                // update the history chart
                if (DataHandler.getInstance().getLastIntValue() != 0) {
                    DataHandler.getInstance().getSeries1().addLast(0, DataHandler.getInstance().getLastIntValue());
                    if (DataHandler.getInstance().getSeries1().size() > MAX_SIZE)
                        DataHandler.getInstance().getSeries1().removeFirst();//Prevent graph to overload data.
                    plot.redraw();
                }

                // update MIN / MAX / AVG
                TextView min = (TextView) findViewById(R.id.min);
                min.setText(DataHandler.getInstance().getMin());

                TextView avg = (TextView) findViewById(R.id.avg);
                avg.setText(DataHandler.getInstance().getAvg());

                TextView max = (TextView) findViewById(R.id.max);
                max.setText(DataHandler.getInstance().getMax());
            }
        });
    }


    public void onStop() {
        super.onStop();
    }



    private void startGettingNotificationFromDevice(){
        Log.v(TAG, "startGettingNotificationFromDevice ...., is device connected ? " + BleManager.getInstance().isConnected(connectedDevice));

        BleManager.getInstance().notify(
                connectedDevice,
                HRUUID_SERVICE,
                HRUUID_CHARACTERISTIC,
                new BleNotifyCallback() {
                    @Override
                    public void onNotifySuccess() {
                        Log.v(TAG, "onNotifySuccess");
                    }

                    @Override
                    public void onNotifyFailure(BleException exception) {
                        Log.v(TAG, "onNotifyFailure: " + exception.getDescription());
                    }

                    @Override
                    public void onCharacteristicChanged(byte[] data) {
                        int bmp = data[1] & 0xFF; // To un-sign the value
                        DataHandler.getInstance().computeStats(bmp);
                        Log.v(TAG, "Data received from HR "+ bmp);

                        updateUIWithData();
                    }
                });
    }
}
