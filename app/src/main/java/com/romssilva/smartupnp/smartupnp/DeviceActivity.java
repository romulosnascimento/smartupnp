package com.romssilva.smartupnp.smartupnp;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.controlpoint.ActionCallback;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.message.header.UDNHeader;
import org.fourthline.cling.model.meta.Action;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class DeviceActivity extends AppCompatActivity {

    private DeviceDisplay deviceDisplay;
    private TextView deviceName;
    private UDN udn;
    private Button favoriteButton;
    private TextView bottomBar;

    private FavoritesManagar favoritesManagar;

    private BrowseRegistryListener registryListener = new BrowseRegistryListener();

    private AndroidUpnpService upnpService;

    private DeviceActionAdapter deviceActionAdapter;

    private Timer timer1;
    private Timer timer2;

    private ServiceConnection serviceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i("deviceAct", "onServiceConnected");
            upnpService = (AndroidUpnpService) service;

            // Get ready for future device advertisements
            upnpService.getRegistry().addListener(registryListener);

            upnpService.getRegistry().removeAllLocalDevices();
            upnpService.getRegistry().removeAllRemoteDevices();

            // Search asynchronously for all devices, they will respond soon
            upnpService.getControlPoint().search(new UDNHeader(udn));

            initDeviceActionAdapter();
        }

        public void onServiceDisconnected(ComponentName className) {
            upnpService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("deviceAct", "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        deviceName = findViewById(R.id.device_name);
        deviceName.setText("Obtaining device information...");

        favoriteButton = findViewById(R.id.favorite_btn);
        bottomBar = findViewById(R.id.textViewBar);

        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.device_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(null);

        favoriteButton.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onClick(View view) {
                if (favoritesManagar.isFavorite(deviceDisplay)) {
                    favoritesManagar.removeDevice(deviceDisplay);
                    favoriteButton.setBackground(getDrawable(R.drawable.ic_star_empty));
                } else {
                    favoritesManagar.addDevice(deviceDisplay);
                    favoriteButton.setBackground(getDrawable(R.drawable.ic_star_full));
                }
            }
        });

        favoritesManagar = FavoritesManagar.getInstance(getApplicationContext());

        getIncomingIntent();
    }

    private void initDeviceActionAdapter() {
        RecyclerView deviceActionsList = (RecyclerView) findViewById(R.id.device_actions_list);
        deviceActionAdapter = new DeviceActionAdapter(new ArrayList<Action>(), getApplicationContext(), upnpService, this);

        deviceActionsList.setAdapter(deviceActionAdapter);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());

        deviceActionsList.setLayoutManager(linearLayoutManager);

        timer1 = new Timer();
        timer2 = new Timer();

        timer1.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    public void run() {
                        deviceName.setText("The device is taking too long to respond...");
                    }
                });
                timer2.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(DeviceActivity.this, "Device is currently not available. Please make sure it is connected to the network and try again.", Toast.LENGTH_LONG).show();
                            }
                        });
                        finish();
                    }
                }, 5000);
            }
        }, 5000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (upnpService != null) {
            upnpService.getRegistry().removeListener(registryListener);

            upnpService.getControlPoint().search();
        }
        // This will stop the UPnP service if nobody else is bound to it
        getApplicationContext().unbindService(serviceConnection);
    }

    public void getIncomingIntent() {
        Log.i("deviceAct", "getIcomingIntent");
        if (getIntent().hasExtra("device_udn")) {
            udn = new UDN(getIntent().getStringExtra("device_udn"));

            // This will start the UPnP service if it wasn't already started
            getApplicationContext().bindService(
                    new Intent(this, AndroidUpnpServiceImpl.class),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
            );
        }
    }

    protected class BrowseRegistryListener extends DefaultRegistryListener {

        /* Discovery performance optimization for very slow Android devices! */
        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, final RemoteDevice device, final Exception ex) {
            deviceRemoved(device);
        }
        /* End of optimization, you can remove the whole block if your Android handset is fast (>= 600 Mhz) */

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            deviceRemoved(device);
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
            deviceAdded(device);
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
            deviceRemoved(device);
        }

        public void deviceAdded(final Device device) {
            if (device.isFullyHydrated() && device.getIdentity().getUdn().equals(udn)) {
                timer1.cancel();
                timer2.cancel();
                runOnUiThread(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void run() {
                        deviceDisplay = new DeviceDisplay(device);
                        deviceName.setText(device.getDetails().getFriendlyName());
                        if (favoritesManagar.isFavorite(deviceDisplay)) {
                            favoriteButton.setBackground(getDrawable(R.drawable.ic_star_full));
                        } else {
                            favoriteButton.setBackground(getDrawable(R.drawable.ic_star_empty));
                        }
                        favoriteButton.setVisibility(View.VISIBLE);
                        bottomBar.setVisibility(View.VISIBLE);

                    }
                });
                for (Service service : device.getServices()) {
                    for (Action action : service.getActions()) {
                        deviceActionAdapter.addAction(action);
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        deviceActionAdapter.notifyDataSetChanged();
                    }
                });
            }
        }

        public void deviceRemoved(final Device device) {
            runOnUiThread(new Runnable() {
                public void run() {
                }
            });
        }

        public void executeAction(AndroidUpnpService upnpService, Action action) {

            ActionInvocation toggleActionInvocation = new GenericActionInvocation(action);

            upnpService.getControlPoint().execute(new ActionCallback(toggleActionInvocation) {
                @Override
                public void success(ActionInvocation actionInvocation) {
                    Log.i("Action Callback", "Success!");
                }

                @Override
                public void failure(ActionInvocation actionInvocation, UpnpResponse upnpResponse, String s) {
                    Log.i("Action Callback", "Failed!");
                }
            });
        }

        public void executeActions (AndroidUpnpService upnpService, DeviceDisplay deviceDisplay) {
            Device device = deviceDisplay.getDevice();

            for (Service service : device.getServices()) {
                for (Action action : service.getActions()) {

                    ActionInvocation toggleActionInvocation = new GenericActionInvocation(action);

                    upnpService.getControlPoint().execute(new ActionCallback(toggleActionInvocation) {
                        @Override
                        public void success(ActionInvocation actionInvocation) {
                            Log.i("Action Callback", "Success!");
                        }

                        @Override
                        public void failure(ActionInvocation actionInvocation, UpnpResponse upnpResponse, String s) {
                            Log.i("Action Callback", "Failed!");
                        }
                    });
                }
            }
        }
    }
}
