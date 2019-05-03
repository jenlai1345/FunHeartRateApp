package com.linkrosslab.funheartrateapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
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


// Main activity
public class HomeActivity extends Activity implements OnItemSelectedListener {


    private final int MAX_SIZE = 60; //graph max size
    private final String HRUUID_SERVICE = "0000180D-0000-1000-8000-00805F9B34FB";              // this is the Heart Rate UUID for Heart Rate "Service"
    private final String HRUUID_CHARACTERISTIC = "00002a37-0000-1000-8000-00805f9b34fb"; // this is the Heart Rate UUID for rate measurement

    // layout views
    private XYPlot plot;
    private AwesomeSpeedometer speedometer;
    private View gap;
    private ProgressBar progressBar;
    private Spinner spinner;
    private ImageView buttonRefresh;
    private TextView numDiamond;


    // variables
    List<BleDevice> pairedDevices = new ArrayList<>();
    List<String> deviceList = new ArrayList<>();
    ArrayAdapter<String> spinnerAdapter;
    BleDevice connectedDevice;

    int connectedIndex = 0;
    int reconnectAllowance = 5;
    int intNumDiamond = 0;
    private int lastBeat = 80; // normal heart beats



    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Log.i("Mainnn", "Starting heart rate monitor at the main activity");


        speedometer = (AwesomeSpeedometer) findViewById(R.id.speedView);
        gap = (View) findViewById(R.id.flyingGap);
        spinner = (Spinner) findViewById(R.id.spinner);
        buttonRefresh = (ImageView) findViewById(R.id.buttonRefresh);
        numDiamond = (TextView) findViewById(R.id.numDiamond);

        numDiamond.setText(Integer.toString(intNumDiamond));

        buttonRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listBT();
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


        listBT();


        // Create Graph
        plot = (XYPlot) findViewById(R.id.dynamicPlot);
        if (plot.getSeriesSet().size() == 0) {
            Number[] series1Numbers = {};
            DataHandler.getInstance().setSeries1(new SimpleXYSeries(Arrays.asList(series1Numbers), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Heart Rate"));
        }

        //LOAD Graph
        LineAndPointFormatter series1Format = new LineAndPointFormatter(Color.rgb(0, 0, 255), Color.rgb(200, 200, 200), null, null);
        series1Format.setPointLabelFormatter(new PointLabelFormatter());
        plot.addSeries(DataHandler.getInstance().getSeries1(), series1Format);
        plot.setTicksPerRangeLabel(3);
        plot.getGraphWidget().setDomainLabelOrientation(-45);
    }


    /**
     * When the option is selected in the dropdown we turn on the bluetooth
     */
    public void onItemSelected(AdapterView<?> arg0, View view, int position, long id) {
        Log.i("Mainnn", "onItemSelected() called -> " + position);
        connectedIndex = position;

        if (position > 0 && position <= pairedDevices.size()) {
            Log.i("Mainnn", ", selected device name = " + pairedDevices.get(position-1).getName());
            connectToDevice(pairedDevices.get(position-1));
        }

    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        Log.i("Mainnn", "onNothingSelected");
    }



    public void onStart() {
        super.onStart();
    }



    /**
     * Run on startup to list bluetooth paired device
     */
    public void listBT() {
        Log.i("Mainnn", "Listing BT elements");

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
                Log.i("Mainnn", "onScanFinished");
                Toast.makeText(HomeActivity.this, "Please select a device from dropdown menu to connect", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onScanStarted(boolean success) {
                progressBar.setVisibility(View.VISIBLE);
                Log.i("Mainnn", "onScanStarted");
                Toast.makeText(HomeActivity.this, "Please be patient as this may take a few seconds", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                Log.i("Mainnn", "onScanStarted, index = " + deviceList.size());

                String status = "";
                if(BleManager.getInstance().isConnected(bleDevice)) {
                    Log.i("Mainnn", "onScanStarted already connected to device " + deviceList.size() + ", set selected index to it");
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
        Log.i("Mainnn", "connectToDevice");

        if(device != null) {

            boolean isConnected = BleManager.getInstance().isConnected(device);
            Log.i("Mainnn", "connectToDevice .... connecting, connected already ? " + isConnected );

            if(isConnected) {
                connectedDevice = device;
                startGettingNotificationFromDevice();
            }
            else {

                BleManager.getInstance().connect(device, new BleGattCallback() {
                    @Override
                    public void onStartConnect() {
                        Toast.makeText(HomeActivity.this, "Trying to connect to device. Please wait ...", Toast.LENGTH_LONG).show();
                        Log.i("Mainnn", "onStartConnect");
                        progressBar.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onConnectFail(BleDevice bleDevice, BleException exception) {
                        Log.i("Mainnn", "onConnectFail " + exception.getDescription());
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
                        Log.i("Mainnn", "onConnectSuccess ---- setting selection to index " + connectedIndex);
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
                        Log.i("Mainnn", "onDisConnected");
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
    public void receiveData() {

        runOnUiThread(new Runnable() {
            public void run() {

                int beat = DataHandler.getInstance().getLastIntValue();

                if(speedometer != null) {
                    speedometer.speedTo(beat);
                }

                int shift = (beat - lastBeat) * -11;
                ViewGroup.LayoutParams params = gap.getLayoutParams();

                Log.i("Mainnn", "shifting gap " + shift);

                int beforeHeight = params.height;
                int afterHeight = params.height + shift;

                // always make their gap between 0 ~ 100
                if(afterHeight < 0) {
                    afterHeight = 0;
                }
                else if (afterHeight > 170) {
                    afterHeight = 170;
                }

                params.height = afterHeight;

                Log.i("Mainnn", "changing gap height from " + beforeHeight + "  to " + afterHeight);

                // reward a diamond when bird reaches it
                if(afterHeight == 0) {
                    Toast.makeText(HomeActivity.this, "Congrats! You just won another diamond! Keep going!", Toast.LENGTH_LONG).show();
                    intNumDiamond++;
                    numDiamond.setText(Integer.toString(intNumDiamond));
                    params.height = 100; // reset
                }

                gap.setLayoutParams(params);

                lastBeat = beat;

                if (DataHandler.getInstance().getLastIntValue() != 0) {
                    DataHandler.getInstance().getSeries1().addLast(0, DataHandler.getInstance().getLastIntValue());
                    if (DataHandler.getInstance().getSeries1().size() > MAX_SIZE)
                        DataHandler.getInstance().getSeries1().removeFirst();//Prevent graph to overload data.
                    plot.redraw();
                }

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
        Log.v("Mainnn", "startGettingNotificationFromDevice ...., is device connected ? " + BleManager.getInstance().isConnected(connectedDevice));

        BleManager.getInstance().notify(
                connectedDevice,
                HRUUID_SERVICE,
                HRUUID_CHARACTERISTIC,
                new BleNotifyCallback() {
                    @Override
                    public void onNotifySuccess() {
                        Log.v("Mainnn", "onNotifySuccess");
                    }

                    @Override
                    public void onNotifyFailure(BleException exception) {
                        Log.v("Mainnn", "onNotifyFailure: " + exception.getDescription());
                    }

                    @Override
                    public void onCharacteristicChanged(byte[] data) {
                        int bmp = data[1] & 0xFF; // To unsign the value
                        DataHandler.getInstance().computeStats(bmp);
                        Log.v("Mainnn", "Data received from HR "+ bmp);

                        receiveData();
                    }
                });
    }
}
