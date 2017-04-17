package com.example.falcato.wfdrouting;

import android.app.Application;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class MyApplication extends Application {

    private static final String TAG = "WFDRouting";
    private boolean hasNet;
    public Map<String, Integer> routeTable = new HashMap<>();

    public boolean getHasNet() { return hasNet; }

    public void setHasNet(boolean hasNet) {
        this.hasNet = hasNet;
    }

    public void changeP2Pname (WifiP2pManager mManager, WifiP2pManager.Channel mChannel,
                               String devName) {

        try {
            Class[] paramTypes = new Class[3];
            paramTypes[0] = WifiP2pManager.Channel.class;
            paramTypes[1] = String.class;
            paramTypes[2] = WifiP2pManager.ActionListener.class;
            Method setDeviceName = mManager.getClass().getMethod(
                    "setDeviceName", paramTypes);
            setDeviceName.setAccessible(true);

            Object arglist[] = new Object[3];
            arglist[0] = mChannel;
            arglist[1] = devName;
            arglist[2] = new WifiP2pManager.ActionListener() {

                @Override
                public void onSuccess() {
                    Log.d("setDeviceName succeeded", "true");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d("setDeviceName failed", "true");
                }
            };
            setDeviceName.invoke(mManager, arglist);

        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void updateRouteTable (String msg) {
        String dest = msg.split(";")[1];
        int hops = Integer.parseInt(msg.split(";")[2]);

        // If already contains current node
        if (routeTable.containsKey(dest)){
            // If new advertise is better than previous
            if (hops <= routeTable.get(dest))
                routeTable.put(dest, hops);
                Log.i(TAG, "Updated table: " + routeTable.toString());
        // If table doesn't contain node
        }else{
            // Insert new node and hops
            routeTable.put(dest, hops);
            Log.i(TAG, "Inserted table: " + routeTable.toString());
        }
    }

    public int getMinHop() {

        return -1;
    }
}
