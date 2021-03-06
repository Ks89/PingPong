/*
Copyright 2015 Stefano Cappa, Politecnico di Milano

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package it.polimi.wifidirect.dialog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;

import java.util.ArrayList;

import it.polimi.wifidirect.R;
import it.polimi.wifidirect.model.P2PDevice;
import it.polimi.wifidirect.model.P2PGroups;
import it.polimi.wifidirect.model.PingPongList;
import it.polimi.wifidirect.spinner.CustomSpinnerAdapter;
import it.polimi.wifidirect.spinner.SpinnerRow;

/**
 * Class that represents a DialogFragment used to choose the GO's mac address of the device which you want to do "pingpong".
 * Be careful, because, before that you can proceed to use this device as a "pingpong device", it must be connected to a GO.
 * In this Dialog there is the GO's mac address of the other group, where this device is not a peer/client, but he want to do "pingpong".
 * <p></p>
 * Created by Stefano Cappa on 30/01/15.
 */
public class PingPongDialog extends DialogFragment {

    static private Button ok, no;

    //spinner_ping is the device where this client is already connected, spinner_pong is the other GO.
    static private Spinner spinner_ping, spinner_pong;
    static private CheckBox testmode_checkbox;

    private static ArrayList<SpinnerRow> list_ping, list_pong; //list_ping: list of starting device, list_pong: list of destinations

    /**
     * {@link it.polimi.wifidirect.DeviceDetailFragment} implements this interface.
     * But the method to change the device name in
     */
    public interface DialogPingPongListener {
        public void initAndStartPingPong(String ping_address, String pong_address, boolean isChecked);
    }

    /**
     * Method to obtain a new Fragment's instance.
     * @return This Fragment instance.
     */
    static public PingPongDialog newInstance() {
        return new PingPongDialog();
    }

    /**
     * Default Fragment constructor.
     */
    public PingPongDialog() {}


    @Override
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance())
            getDialog().setOnDismissListener(null);
        super.onDestroyView();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.pingpong_dialog, container, false);

        this.getDialog().setTitle("Ping Pong");
        ok = (Button) v.findViewById(R.id.dialog_yes);
        no = (Button) v.findViewById(R.id.dialog_no);
        spinner_ping = (Spinner) v.findViewById(R.id.spinner_ping);
        spinner_pong = (Spinner) v.findViewById(R.id.spinner_pong);
        testmode_checkbox = (CheckBox) v.findViewById(R.id.testmode_checkbox);

        list_ping = new ArrayList<>();
        list_ping.add(new SpinnerRow(P2PGroups.getInstance().getGroupList().get(0).getGroupOwner().getP2pDevice().deviceAddress));

        list_pong = new ArrayList<>();
        for(P2PDevice pingpongDevice : PingPongList.getInstance().getPingponglist()) {
            list_pong.add(new SpinnerRow(pingpongDevice.getP2pDevice().deviceAddress));
        }


        this.setSpinnerAdapter(spinner_ping,list_ping);
        this.setSpinnerAdapter(spinner_pong,list_pong);

        //the ping device is forced to the actual GO of this client.
        spinner_ping.setEnabled(false);

        this.setListener();

        return v;
    }

    /**
     * Method to set the Spinner adapter.
     * @param spinner The Spinner to set
     * @param list ArrayList<SpinnerRow> of the element to fill the Spinner.
     */
    private void setSpinnerAdapter(Spinner spinner, ArrayList<SpinnerRow> list) {
        spinner.setAdapter(new CustomSpinnerAdapter(this.getActivity(),
                android.R.layout.simple_spinner_item,
                list));
    }



    /**
     * Method to set listeners on buttons and checkbox.
     */
    private void setListener() {
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {

                ((DialogPingPongListener)getTargetFragment()).initAndStartPingPong(spinner_ping.getSelectedItem().toString(),
                        spinner_pong.getSelectedItem().toString(), testmode_checkbox.isChecked());

                dismiss();
            }
        });
        no.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                dismiss();
            }
        });

        testmode_checkbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(testmode_checkbox.isChecked()) {
                    setSpinnerAdapter(spinner_pong,list_ping);

                    spinner_pong.setEnabled(false);
                } else {
                    setSpinnerAdapter(spinner_pong,list_pong);

                    spinner_pong.setEnabled(true);
                }
            }
        });
    }

}