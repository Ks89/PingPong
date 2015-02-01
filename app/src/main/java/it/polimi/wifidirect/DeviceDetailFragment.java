/*
 * Copyright (C) 2011 The Android Open Source Project
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

package it.polimi.wifidirect;

import android.app.Activity;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import it.polimi.wifidirect.dialog.PingPongDialog;
import it.polimi.wifidirect.model.LocalP2PDevice;
import it.polimi.wifidirect.model.P2PDevice;
import it.polimi.wifidirect.model.P2PGroup;
import it.polimi.wifidirect.model.P2PGroups;
import it.polimi.wifidirect.model.PeerList;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener, WifiP2pManager.GroupInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;

    private String pingpong_macaddress;
    private P2PDevice device;
    private WifiP2pInfo info;

    private ProgressDialog progressDialog = null;
    private Fragment fragment = this;
    private static final int PINGPONG = 5; //numero costante scelto a caso

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);

        //clicco sul pulsante connect
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.getP2pDevice().deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.getP2pDevice().deviceAddress, true, true
//                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
                );
                ((DeviceListFragment.DeviceActionListener) getActivity()).connect(config);


            }
        });


        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceListFragment.DeviceActionListener) getActivity()).disconnect();
                    }
                });

        mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });


        //clicco sul pulsante connect
        mContentView.findViewById(R.id.btn_start_ping_pong).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                PingPongDialog pingPongDialog = (PingPongDialog) getFragmentManager().findFragmentByTag("pingPongDialog");

                if (pingPongDialog == null) {
                    pingPongDialog = PingPongDialog.newInstance();

                    pingPongDialog.setTargetFragment(fragment, PINGPONG);

                    pingPongDialog.show(getFragmentManager(), "pingPongDialog");
                    getFragmentManager().executePendingTransactions();
                }
            }
        });

        mContentView.findViewById(R.id.btn_start_ping_pong).setVisibility(View.GONE);

        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case PINGPONG:

                if (resultCode == Activity.RESULT_OK) {
                    // After Ok code.
                    Bundle bundle = data.getExtras();
                    this.pingpong_macaddress = bundle.getString("macaddress_text");

                    Log.d("DDF_PingPong_yes", "Ho premuto Yes e il mac address passato e' : " + pingpong_macaddress);

                    this.pingPongConnect();

                } else if (resultCode == Activity.RESULT_CANCELED) {
                    // After Cancel code.
                    Log.d("DDF_PingPong_no", "Ho premuto NO");
                }

                break;
            default:
                //standard dal codice google
                // User has picked an image. Transfer it to group owner i.e peer using
                // FileTransferService.
                Uri uri = data.getData();
                TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
                statusText.setText("Sending: " + uri);
                Log.d(WiFiDirectActivity.TAG, "Intent----------- " + uri);
                Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
                serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                        info.groupOwnerAddress.getHostAddress());
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
                getActivity().startService(serviceIntent);
                break;

        }


    }


    class PingPongAsyncTask extends AsyncTask<Context, Void, Void> {
        @Override
        protected Void doInBackground(Context... context) {

            P2PDevice destinationDevice = PeerList.getInstance().getDeviceByMacAddress(pingpong_macaddress);
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = destinationDevice.getP2pDevice().deviceAddress;
            config.wps.setup = WpsInfo.PBC;

            //per poter diventare client (visto che pingpong e' un client che saltella tra due gruppi)
            //e non iniziare un nuovo gruppo come GO
            config.groupOwnerIntent = 0;

            for (int i = 0; i < 10; i++) {
                ((WiFiDirectActivity) context[0]).disconnectPingPong();

                Log.d("ping-pong", "disconnect");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                ((WiFiDirectActivity) context[0]).discoveryPingPong();

                Log.d("ping-pong", "discovery");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                ((WiFiDirectActivity) context[0]).connect(config);

                Log.d("ping-pong", "connect");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }
    }

    private void pingPongConnect() {
        P2PDevice destinationDevice = PeerList.getInstance().getDeviceByMacAddress(pingpong_macaddress);

        if (destinationDevice != null) {
            Log.d("pingpong_destination", "PingPong con : " + destinationDevice.getP2pDevice().deviceAddress);
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = destinationDevice.getP2pDevice().deviceAddress;
            config.wps.setup = WpsInfo.PBC;
            config.groupOwnerIntent = 0; //per poter diventare client

//            if (progressDialog != null && progressDialog.isShowing()) {
//                progressDialog.dismiss();
//            }
//            progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
//                    "Connecting to :" + device.getP2pDevice().deviceAddress, true, true);

            new PingPongAsyncTask().execute(this.getActivity());


        } else {
            Log.d("pingpong_destination", "Impossibile avviare PingPong");
        }
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {
        Log.d("onGroupInfoAvailable", "Informazioni sul gruppo disponibili");

        //puo' essere il dispositivo corrente ad essere gia' GO, oppure
        //questo potrebbe essere Client e quindi ora ne setto il suo group owner
        P2PDevice owner = new P2PDevice(group.getOwner());
        owner.setGroupOwner(true);

        P2PGroup p2pGroup = new P2PGroup(true);
        p2pGroup.setGroup(group);
        p2pGroup.setGroupOwner(owner);

        if(!P2PGroups.getInstance().getGroupList().contains(p2pGroup)) {
            P2PGroups.getInstance().getGroupList().add(p2pGroup);
        }

        P2PDevice client;
        for (WifiP2pDevice device : group.getClientList()) {
            client = new P2PDevice(device);
            client.setGroupOwner(false);
            p2pGroup.getList().add(client);
        }

        Log.d("Stampo owner", p2pGroup.getGroupOwner().toString());
        for (P2PDevice device1 : p2pGroup.getList()) {
            Log.d("Stampo client", device1.toString());
        }
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);

        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text)
                + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
                : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
//        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {

            //come group owner
            LocalP2PDevice.getInstance().getLocalDevice().setGroupOwner(true);

            new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text))
                    .execute();
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
                    .getString(R.string.client_text));

            //se e' il client e' possibile che voglia diventare pingpong in futuro, quindi prima di tutto vado a

        }

        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);

        //abilita il pulsante ping pong solo se il dispositivo locale corrente e' un client
        if (!LocalP2PDevice.getInstance().getLocalDevice().isGroupOwner()) {
            mContentView.findViewById(R.id.btn_start_ping_pong).setVisibility(View.VISIBLE);
        }
    }

    /**
     * Updates the UI with device data
     *
     * @param device the device to be displayed
     */
    public void showDetails(P2PDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(device.getP2pDevice().deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(device.getP2pDevice().toString());

    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;

        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                ServerSocket serverSocket = new ServerSocket(8988);
                Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();
                Log.d(WiFiDirectActivity.TAG, "Server: connection done");
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();

                Log.d(WiFiDirectActivity.TAG, "server: copying files " + f.toString());
                InputStream inputstream = client.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                statusText.setText("File copied - " + result);
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + result), "image/*");
                context.startActivity(intent);
            }

        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }

    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);

            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(WiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }
}