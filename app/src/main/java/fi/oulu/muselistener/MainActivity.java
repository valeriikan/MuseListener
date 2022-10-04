package fi.oulu.muselistener;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.choosemuse.libmuse.Accelerometer;
import com.choosemuse.libmuse.ConnectionState;
import com.choosemuse.libmuse.Eeg;
import com.choosemuse.libmuse.Muse;
import com.choosemuse.libmuse.MuseArtifactPacket;
import com.choosemuse.libmuse.MuseConnectionListener;
import com.choosemuse.libmuse.MuseConnectionPacket;
import com.choosemuse.libmuse.MuseDataListener;
import com.choosemuse.libmuse.MuseDataPacket;
import com.choosemuse.libmuse.MuseDataPacketType;
import com.choosemuse.libmuse.MuseListener;
import com.choosemuse.libmuse.MuseManagerAndroid;

import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    /**
     * Tag used for logging purposes.
     */
    private final String TAG = "HEADBAND";

    /**
     * The MuseManager is how you detect Muse headbands and receive notifications
     * when the list of available headbands changes.
     */
    private MuseManagerAndroid manager;

    /**
     * A Muse refers to a Muse headband.  Use this to connect/disconnect from the
     * headband, register listeners to receive EEG data and get headband
     * configuration and version information.
     */
    // private Muse muse;


    /**
     * In the UI, the list of Muses you can connect to is displayed in a Spinner object for this example.
     * This spinner adapter contains the MAC addresses of all of the headbands we have discovered.
     */
    private ArrayAdapter<String> spinnerAdapter;

    /**
     * It is possible to pause the data transmission from the headband.  This boolean tracks whether
     * or not the data transmission is enabled as we allow the user to pause transmission in the UI.
     */
    private boolean dataTransmission = true;

    // UI elements
    Spinner spinner;
    TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Assign click event listener to buttons
        findViewById(R.id.scan).setOnClickListener(this);
        findViewById(R.id.connect).setOnClickListener(this);
        findViewById(R.id.disconnect).setOnClickListener(this);
        findViewById(R.id.pause).setOnClickListener(this);

        // Initialize spinner
        spinnerAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item);
        spinner = findViewById(R.id.spinner);
        spinner.setAdapter(spinnerAdapter);
        status = findViewById(R.id.status);

        // We need to set the context on MuseManagerAndroid before we can do anything.
        // This must come before other LibMuse API calls as it also loads the library.
        manager = MuseManagerAndroid.getInstance();
        manager.setContext(this);

        // Register a listener to receive notifications of what Muse headbands
        // we can connect to.
        manager.setMuseListener(museListener);

        // Request permissions for devices with API 23+
        ensurePermissions();
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.scan) {
            // The user has pressed the "Scan" button.
            // Start listening for nearby or paired Muse headbands. We call stopListening
            // first to make sure startListening will clear the list of headbands and start fresh.
            manager.stopListening();
            manager.startListening();

        } else if (v.getId() == R.id.connect) {

            // The user has pressed the "Connect" button to connect to
            // the headband in the spinner.

            // Listening is an expensive operation, so now that we know
            // which headband the user wants to connect to we can stop
            // listening for other headbands.
            manager.stopListening();

            List<Muse> availableMuses = manager.getMuses();

            // Check that we actually have something to connect to.
            if (availableMuses.size() < 1 || spinner.getAdapter().getCount() < 1) {
                Toast.makeText(getApplicationContext(), R.string.nothing, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "There is nothing to connect to");
            } else {
                // Cache the Muse that the user has selected.
                muse = availableMuses.get(spinner.getSelectedItemPosition());

                // Unregister all prior listeners and register our data listener to
                // receive the MuseDataPacketTypes we are interested in.  If you do
                // not register a listener for a particular data type, you will not
                // receive data packets of that type.
                muse.unregisterAllListeners();
                muse.registerConnectionListener(connectionListener);
                muse.registerDataListener(dataListener, MuseDataPacketType.EEG);
                muse.registerDataListener(dataListener, MuseDataPacketType.ALPHA_RELATIVE);
                muse.registerDataListener(dataListener, MuseDataPacketType.ACCELEROMETER);
                muse.registerDataListener(dataListener, MuseDataPacketType.BATTERY);
                muse.registerDataListener(dataListener, MuseDataPacketType.DRL_REF);
                muse.registerDataListener(dataListener, MuseDataPacketType.QUANTIZATION);

                // Initiate a connection to the headband and stream the data asynchronously.
                muse.runAsynchronously();
            }

        } else if (v.getId() == R.id.disconnect) {

            // The user has pressed the "Disconnect" button.
            // Disconnect from the selected Muse.
            if (muse != null) {
                muse.disconnect();
            }

        } else if (v.getId() == R.id.pause) {

            // The user has pressed the "Pause/Resume" button to either pause or
            // resume data transmission.  Toggle the state and pause or resume the
            // transmission on the headband.
            if (muse != null) {
                dataTransmission = !dataTransmission;
                muse.enableDataTransmission(dataTransmission);
            }
        }
    }


    //--------------------------------------
    // Permissions

    /**
     * The ACCESS_COARSE_LOCATION permission is required to use the
     * Bluetooth Low Energy library and must be requested at runtime for Android 6.0+
     * On an Android 6.0 device, the following code will display 2 dialogs,
     * one to provide context and the second to request the permission.
     * On an Android device running an earlier version, nothing is displayed
     * as the permission is granted from the manifest.
     *
     * If the permission is not granted, then Muse 2016 (MU-02) headbands will
     * not be discovered and a SecurityException will be thrown.
     */
    private void ensurePermissions() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // We don't have the ACCESS_COARSE_LOCATION permission so create the dialogs asking
            // the user to grant us the permission.
            DialogInterface.OnClickListener buttonListener =
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which){
                            dialog.dismiss();
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                    0);
                        }
                    };

            // This is the context dialog which explains to the user the reason we are requesting
            // this permission.  When the user presses the positive (I Understand) button, the
            // standard Android permission dialog will be displayed (as defined in the button
            // listener above).
            AlertDialog introDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_dialog_title)
                    .setMessage(R.string.permission_dialog_description)
                    .setPositiveButton(R.string.permission_dialog_understand, buttonListener)
                    .setCancelable(false)
                    .create();
            introDialog.show();
        }
    }


    //--------------------------------------
    // Listeners

    // Listener class for scanning Muse headbands nearby
    private MuseListener museListener = new MuseListener() {

        /**
         * You will receive a callback to this method each time a headband is discovered.
         * In this example, we update the spinner with the MAC address of the headband.
         */
        @Override
        public void museListChanged() {
            final List<Muse> list = manager.getMuses();
            spinnerAdapter.clear();
            for (Muse m : list) {
                spinnerAdapter.add(m.getName() + " - " + m.getMacAddress());
            }
        }
    };


    // ConnectionListener will be notified whenever there is a change in the connection
    // state of a headband, for example when the headband connects or disconnects
    private MuseConnectionListener connectionListener = new MuseConnectionListener() {

        /**
         * You will receive a callback to this method each time there is a change to the
         * connection state of one of the headbands.
         * @param p     A packet containing the current and prior connection states
         * @param muse  The headband whose state changed.
         */
        @Override
        public void receiveMuseConnectionPacket(MuseConnectionPacket p, final Muse muse) {

            // Update the UI with the change in connection state.
            ConnectionState current = p.getCurrentConnectionState();
            status.setText(String.valueOf(current));
            Log.d(TAG, "Status: " + current);

            if (current == ConnectionState.DISCONNECTED) {
                Log.d(TAG, "Muse disconnected:" + muse.getName());
                // We have disconnected from the headband, so set our cached copy to null.
                MainActivity.this.muse = null;
            }
        }
    };


    // DataListener is how you will receive EEG (and other) data from the headband
    private MuseDataListener dataListener = new MuseDataListener() {

        /**
         * You will receive a callback to this method each time the headband sends a MuseDataPacket
         * that you have registered.  You can use different listeners for different packet types or
         * a single listener for all packet types as we have done here.
         * @param p     The data packet containing the data from the headband (eg. EEG data)
         * @param muse  The headband that sent the information.
         */
        @Override
        public void receiveMuseDataPacket(MuseDataPacket p, Muse muse) {
            switch (p.packetType()) {
                case EEG:
                    // Do something here

                    double eeg1 = p.getEegChannelValue(Eeg.EEG1);
                    double eeg2 = p.getEegChannelValue(Eeg.EEG2);
                    double eeg3 = p.getEegChannelValue(Eeg.EEG3);
                    double eeg4 = p.getEegChannelValue(Eeg.EEG4);
                    double aux_l = p.getEegChannelValue(Eeg.AUX_LEFT);
                    double aux_r = p.getEegChannelValue(Eeg.AUX_RIGHT);
                    Log.d(TAG, "EEG: " + eeg1 + " " + eeg2 + " " + eeg3
                            + " " + eeg4 + " " + aux_l + " " + aux_r);
                    break;
                case ACCELEROMETER:
                    // Do something here

                    double x = p.getAccelerometerValue(Accelerometer.X);
                    double y = p.getAccelerometerValue(Accelerometer.Y);
                    double z = p.getAccelerometerValue(Accelerometer.Z);
                    Log.d(TAG, "Accelerometer: " + x + " " + y + " " + z);
                    break;
                case ALPHA_RELATIVE:
                case BATTERY:
                case DRL_REF:
                case QUANTIZATION:
                default:
                    break;
            }
        }

        @Override
        public void receiveMuseArtifactPacket(MuseArtifactPacket museArtifactPacket, Muse muse) {
        }
    };
}
