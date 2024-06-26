package fr.spiderboy.navigoat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcB;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private NfcAdapter mNfcAdapter;
    public static final String dTag = "Navigoat";
    private Navigo card = null;
    private CustomListAdapter listAdapter;
    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        mTextView = findViewById(R.id.text_view_main);
        mTextView.setMovementMethod(new ScrollingMovementMethod());

        ListView lView = findViewById(R.id.listView);
        listAdapter = new CustomListAdapter(this);
        lView.setAdapter(listAdapter);

        /// TODO: Better verbose/non-verbose switcher handling
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (!preferences.getBoolean("verbose_checkbox", false)) {
            ViewSwitcher switcher = findViewById(R.id.profileSwitcher);
            switcher.reset();
            switcher.showNext();
        }

        checkNfc();

        handleIntent(getIntent());
    }

    private void checkNfc() {
        if (mNfcAdapter == null) {
            mTextView.setText("NFC not supported on this device. Go get a new one.\n");
            listAdapter.add("NFC not supported on this device. Go get a new one.");
            return;
        }

        if (!mNfcAdapter.isEnabled()) {
            mTextView.setText("NFC is not enabled. Go enable it!\n");
            listAdapter.add("NFC is not enabled. Go enable it!");

            AlertDialog.Builder alertbox = new AlertDialog.Builder(this);
            alertbox.setTitle("Info");
            alertbox.setMessage("NFC is not enabled. Go enable it!");
            alertbox.setPositiveButton("Turn On", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
                startActivity(intent);
            });
            alertbox.setNegativeButton("Cancel", (dialog, which) -> { });
            alertbox.show();
        } else {
            mTextView.setText("Waiting for card...\n");
            listAdapter.add("Waiting for card...");
        }
    }

    private void addText(String text) {
        TextView mTextView = findViewById(R.id.text_view_main);
        if (mTextView != null) {
            mTextView.append(text + "\n");
        }
    }

    private void updateCustomList() {
        listAdapter.add_header("Card information");
        listAdapter.add("UID", card.getId());
        listAdapter.add_header("Events");
        for (CustomListAdapter.Element e: card.getEvents()) {
            listAdapter.add(e);
        }
        listAdapter.add_header("Contracts");
        for (CustomListAdapter.Element e: card.getContracts()) {
            listAdapter.add(e);
        }
        listAdapter.add_header("Special events");
        for (CustomListAdapter.Element e: card.getSpecialEvents()) {
            listAdapter.add(e);
        }
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        Log.d(MainActivity.dTag, "TOTO " + action + " VS " + NfcAdapter.ACTION_TECH_DISCOVERED);
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String sIsoDep = IsoDep.class.getName();
            String sNfcB = NfcB.class.getName();
            for (String tech : tag.getTechList()) {
                if (tech.equals(sIsoDep) || tech.equals(sNfcB)) {
                    TextView mTextView = findViewById(R.id.text_view_main);
                    mTextView.setText("Waiting for card...\n");
                    listAdapter.clear();
                    card = new Navigo(tag.getId(), getResources().getXml(R.xml.card_struct),
                                        getResources().getXml(R.xml.stations));
                    addText("Found tag class " + tech);
                    synchronized (this) {
                        new NfcReaderTask().execute(tag);
                    }
                    break;
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        /* This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent(intent);
    }

    @Override
    protected void onPause() {
        /*
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        stopForegroundDispatch(this, mNfcAdapter);

        /// Listener for verbose checkbox
        SharedPreferences.OnSharedPreferenceChangeListener changeListener = (sharedPreferences, key) -> {
            if (key.equals("verbose_checkbox")) {
                ViewSwitcher switcher = findViewById(R.id.profileSwitcher);
                switcher.showNext();
            }
        };
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        preferences.unregisterOnSharedPreferenceChangeListener(changeListener);

        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivityForResult(i, 0);
            return true;
        } else if (id == R.id.action_dump) {
            if (card != null && !"".equals(card.getDump())) {
                try {
                    String result_file = save_dump_file();
                    Toast.makeText(getApplicationContext(), "Dump saved in " + result_file, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "Error dumping your card content", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), "No card dump to save!", Toast.LENGTH_LONG).show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    private String save_dump_file() throws Exception {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String path = preferences.getString("dumps_location", Environment.getExternalStorageDirectory().getPath() + "/Navigoat/");
        File myFile = new File(path);
        if (!myFile.exists()) {
            myFile.mkdirs();
        }
        String cur_date = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new Date());
        path = path + "/" + card.getId() + "-" + cur_date + ".txt";
        myFile = new File(path);
        myFile.createNewFile();
        FileOutputStream fOut = new FileOutputStream(myFile);
        OutputStreamWriter myOutWriter =
                new OutputStreamWriter(fOut);
        myOutWriter.append(card.getDump());
        myOutWriter.close();
        fOut.close();
        return path;
    }

    @Override
    protected void onResume() {
        super.onResume();

        /// Listener for verbose checkbox
        SharedPreferences.OnSharedPreferenceChangeListener changeListener = (sharedPreferences, key) -> {
            if (key.equals("verbose_checkbox")) {
                ViewSwitcher switcher = findViewById(R.id.profileSwitcher);
                switcher.showNext();
            }
        };
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        preferences.registerOnSharedPreferenceChangeListener(changeListener);

        /*
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        setupForegroundDispatch(this, mNfcAdapter);
    }

    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, PendingIntent.FLAG_MUTABLE);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{
                new String[] {
                        IsoDep.class.getName(),
                        NfcB.class.getName(),
                }
        };

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_TECH_DISCOVERED);

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    /**
     * @param activity The corresponding {@link MainActivity} requesting to stop the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    private class NfcReaderTask extends AsyncTask<Tag, Void, String> {
        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            IsoDep iso = IsoDep.get(tag);
            try {
                return readTag(iso);
            } catch (IOException e) {
                return "Could not read tag: " + e.getMessage();
            }
        }

        private String readTag(IsoDep iso) throws IOException {
            final IsoDep isodep = iso;
            isodep.connect();
            isodep.setTimeout(5000);
            if (isodep.isConnected()) {
                try {
                    synchronized (this) {
                        runOnUiThread(() -> {
                            addText("Connected!");
                            addText("Parsing card...");
                            card.parseIsoDep(isodep);
                            addText("Dumping card...");
                            card.dump();
                            updateCustomList();
                        });
                        wait(2000);
                        return card.getDump();
                    }
                } catch (InterruptedException e) {
                    return "Thread interrupted: " + e.getMessage();
                }
            } else {
                return "Could not connect to card " + card.getId();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                addText(result);
            }
        }
    }
}
