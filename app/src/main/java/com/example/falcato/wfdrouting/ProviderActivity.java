package com.example.falcato.wfdrouting;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProviderActivity extends AppCompatActivity{

    // Debugging
    private static final String TAG = "WFDRouting";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Member object for the chat services
    private BluetoothService mService = null;

    IntentFilter mIntentFilter;
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;
    WifiManager wifiManager;
    BluetoothAdapter mBluetoothAdapter;
    boolean groupCreated = false, peerDiscoveryInit = false;

    private List<WifiP2pDevice> peers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        //Check if WiFi is enabled
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if(!wifiManager.isWifiEnabled())
            wifiManager.setWifiEnabled(true);
        //Check if Bluetooth is enabled
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }
        //Initialize Wifi Direct Service
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this, peerListListener);

        //Initialize Bluetooth Service
        mService = new BluetoothService(this, mHandler);
        mOutStringBuffer = new StringBuffer("");

        //Change device's name
        String devName = "WFD;" + getMAC();
        ((MyApplication) ProviderActivity.this.getApplication()).changeP2Pname(mManager, mChannel,
                devName);

        if(((MyApplication) ProviderActivity.this.getApplication()).getHasNet()){
            //Discover Peers
            discoverPeers();
        }else{
            //Create Group
            createGroupAsOwner();
        }
    }

    private WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            if (!groupCreated) {
                Collection<WifiP2pDevice> refreshedPeers = peerList.getDeviceList();
                String peerInfo = "Available peers: \n";

                if (!refreshedPeers.equals(peers)) {
                    peers.clear();
                    peers.addAll(refreshedPeers);

                    for (WifiP2pDevice peer : peers) {
                        peerInfo += peer.deviceName;
                    }
                    TextView peerDisplay = (TextView) findViewById(R.id.peerListText);
                    peerDisplay.setText(peerInfo);
                    //CONNECT
                    advertisePeers(true);
                }
                if (peers.size() == 0) {
                    Toast.makeText(ProviderActivity.this, "No peers found!",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(ProviderActivity.this, "Unable to send, not connected!",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mService.write(send);
            Log.d(TAG, "Sent a message");

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        }
    }

    public void analyzeMsg(String msg) {
        // Routing message
        if (msg.contains("ADV")){
            // Update routing table
            Log.i(TAG, "Received an advertising message");
            ((MyApplication) ProviderActivity.this.getApplication()).updateRouteTable(msg);
        // Request message
        }else if (msg.contains("RQT")){
            // Process the webpage request
            Log.i(TAG, "Received a request message");
        }else if (msg.contains("RSP")){
            // Process the webpage response
            Log.i(TAG, "Received a response message");
        }

    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    TextView connState = (TextView) findViewById(R.id.peerListText);
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            connState.setText("Connected to " + mConnectedDeviceName);
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            connState.setText("Connecting");
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            connState.setText("Not connected");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    Log.d(TAG, "I've sent: " + writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    TextView peerText = (TextView) findViewById(R.id.peerText);
                    peerText.setText(readMessage);
                    // Analyze received message
                    analyzeMsg(readMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private String getMAC () {
        return android.provider.Settings.Secure.getString(this.getContentResolver(),
                "bluetooth_address");
    }

    private void connectDevice(String MAC) {
        // Get the BluetoothDevice object
        TextView peerText = (TextView) findViewById(R.id.peerText);
        peerText.setText(MAC);

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(MAC);
        // Attempt to connect to the device
        mService.connect(device);
    }

    public void advertisePeers(boolean hasNet){
        String adv;
        int hops;

        for (WifiP2pDevice peer : peers) {
            // If peer needs to be advertised
            if (((MyApplication) ProviderActivity.this.getApplication()).
                    checkAdvertise(peer.deviceName)) {

                final WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = peer.deviceName.split(";")[1].toUpperCase();

                // Connect
                connectDevice(config.deviceAddress);
                for (int i=0; mService.getState() != BluetoothService.STATE_CONNECTED; i++){
                    if (i > 999999999){
                        Toast.makeText(getApplicationContext(), "Taking too long to connect!",
                                Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
                // Advertise own MAC adress
                if (hasNet)
                    hops = 1;
                else
                    hops = ((MyApplication) ProviderActivity.this.getApplication()).getHops() + 1;

                adv = "ADV;" + config.deviceAddress + ";" + hops;
                sendMessage(adv);
            }
        }
    }

    public void createGroupAsOwner(){
        if(mManager != null) {
            mManager.createGroup(mChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    groupCreated = true;
                    Toast.makeText(ProviderActivity.this, "P2P group creation successful.",
                            Toast.LENGTH_SHORT).show();
                    //LISTEN
                    TextView peerText = (TextView) findViewById(R.id.peerText);
                    peerText.setText(getMAC());
                }
                @Override
                public void onFailure(int reason) {
                    mManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
                        @Override
                        public void onGroupInfoAvailable(WifiP2pGroup group) {
                        if (group.isGroupOwner()){
                            groupCreated = true;
                            Toast.makeText(ProviderActivity.this, "Device already is a group owner!",
                                    Toast.LENGTH_SHORT).show();
                            //LISTEN
                        }
                        }
                    });

                    Toast.makeText(ProviderActivity.this, "P2P group creation failed. Retry. " + reason,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public void discoverPeers(){
        if(mManager != null) {
            mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    peerDiscoveryInit = true;
                    Toast.makeText(ProviderActivity.this, "Discovery initiated!",
                            Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onFailure(int reasonCode) {
                    Toast.makeText(ProviderActivity.this, "Discovery Failed : " + reasonCode,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public void destroyGroup(){
        if(groupCreated) {
            mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    groupCreated = false;
                    Toast.makeText(ProviderActivity.this, "P2P group destroyed.",
                            Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onFailure(int reason) {
                    Toast.makeText(ProviderActivity.this, "Failed to destroy P2P group.",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public void stopDiscovery(){
        if(peerDiscoveryInit){
            mManager.stopPeerDiscovery(mChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    peerDiscoveryInit = false;
                    Toast.makeText(ProviderActivity.this, "Peer discovery stopped!",
                            Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onFailure(int reasonCode) {
                    Toast.makeText(ProviderActivity.this, "Failed to stop peer discovery!",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this, peerListListener);
        registerReceiver(mReceiver, mIntentFilter);
        if (mService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mService.getState() == BluetoothService.STATE_NONE) {
                mService.start();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (groupCreated) destroyGroup();
        if (peerDiscoveryInit) stopDiscovery();
        if (mService != null) mService.stop();
    }

}
