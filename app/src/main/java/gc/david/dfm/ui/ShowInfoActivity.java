package gc.david.dfm.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.ShareActionProvider;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;
import com.splunk.mint.Mint;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import butterknife.InjectView;
import gc.david.dfm.DFMApplication;
import gc.david.dfm.R;
import gc.david.dfm.model.DaoSession;
import gc.david.dfm.model.Distance;
import gc.david.dfm.model.Position;

import static butterknife.ButterKnife.inject;
import static gc.david.dfm.Utils.isOnline;
import static gc.david.dfm.Utils.toastIt;

/**
 * ShowInfoActivity shows information about the distance to the user.
 *
 * @author David
 */
public class ShowInfoActivity extends ActionBarActivity {

    public static final String POSITIONS_LIST_EXTRA_KEY_NAME      = "positionsList";
    public static final String DISTANCE_EXTRA_KEY_NAME            = "distancia";
    private final       String originAddressKey                   = "originAddress";
    private final       String destinationAddressKey              = "destinationAddress";
    private final       String distanceKey                        = "distance";
    private final       String wasSavingWhenOrientationChangedKey = "wasSavingWhenOrientationChanged";
    private final       String aliasHintKey                       = "aliasHint";

    @InjectView(R.id.titulo_datos1)
    protected TextView tvHeaderOriginAddress;
    @InjectView(R.id.titulo_datos2)
    protected TextView tvHeaderDestinationAddress;
    @InjectView(R.id.datos1)
    protected TextView tvOriginAddress;
    @InjectView(R.id.datos2)
    protected TextView tvDestinationAddress;
    @InjectView(R.id.distancia)
    protected TextView tvDistance;

    private MenuItem     menuItem                        = null;
    private List<LatLng> positionsList                   = null;
    private String       originAddress                   = "";
    private String       destinationAddress              = "";
    private String       distance                        = null;
    private boolean      wasSavingWhenOrientationChanged = false;
    private Dialog       savingInDBDialog                = null;
    private EditText     etAlias                         = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Mint.leaveBreadcrumb("ShowInfoActivity::onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_info);
        inject(this);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getIntentData();

        fillTitlesHeaders();

        if (savedInstanceState == null) {
            fillAddressesInfo();
        } else {
            originAddress = savedInstanceState.getString(originAddressKey);
            destinationAddress = savedInstanceState.getString(destinationAddressKey);

            tvOriginAddress.setText(String.format("%s\n\n(%f,%f)",
                                                  originAddress, positionsList.get(0).latitude,
                                                  positionsList.get(0).longitude));

            tvDestinationAddress.setText(String.format("%s\n\n(%f,%f)",
                                                       destinationAddress,
                                                       positionsList.get(positionsList.size() - 1).latitude,
                                                       positionsList.get(positionsList.size() - 1).longitude));

            // Este se modifica dos veces...
            distance = savedInstanceState.getString(distanceKey);

            wasSavingWhenOrientationChanged = savedInstanceState.getBoolean(wasSavingWhenOrientationChangedKey);
            if (wasSavingWhenOrientationChanged) {
                final String aliasHint = savedInstanceState.getString(aliasHintKey);
                saveDataToDB(aliasHint);
            }
        }

        fillDistanceInfo();
    }

    private DaoSession getApplicationDaoSession() {
        Mint.leaveBreadcrumb("ShowInfoActivity::getApplicationDaoSession");
        return ((DFMApplication) getApplicationContext()).getDaoSession();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Mint.leaveBreadcrumb("ShowInfoActivity::onSaveInstanceState");
        super.onSaveInstanceState(outState);

        outState.putString(originAddressKey, originAddress);
        outState.putString(destinationAddressKey, destinationAddress);
        outState.putString(distanceKey, distance);
        outState.putBoolean(wasSavingWhenOrientationChangedKey, wasSavingWhenOrientationChanged);
        if (wasSavingWhenOrientationChanged) {
            outState.putString(aliasHintKey, etAlias.getText().toString());
            if (savingInDBDialog != null) {
                savingInDBDialog.dismiss();
                savingInDBDialog = null;
            }
        }
    }

    /**
     * Get data form the Intent.
     */
    private void getIntentData() {
        Mint.leaveBreadcrumb("ShowInfoActivity::getIntentData");
        final Intent inputDataIntent = getIntent();
        positionsList = (List<LatLng>) inputDataIntent.getSerializableExtra(POSITIONS_LIST_EXTRA_KEY_NAME);
        distance = inputDataIntent.getStringExtra(DISTANCE_EXTRA_KEY_NAME);
    }

    /**
     * Fill Textviews titles.
     */
    private void fillTitlesHeaders() {
        Mint.leaveBreadcrumb("ShowInfoActivity::fillTitlesHeaders");
        tvHeaderOriginAddress.setText(getString(R.string.info_current_position_title));
        tvHeaderDestinationAddress.setText(getString(R.string.info_destination_position_title));
    }

    /**
     * Get the addresses associated to LatLng points and fill the Textviews.
     */
    private void fillAddressesInfo() {
        Mint.leaveBreadcrumb("ShowInfoActivity::fillAddressesInfo");
        try {
            originAddress = new GetAddressTask().execute(positionsList.get(0), tvOriginAddress).get();
            destinationAddress = new GetAddressTask().execute(positionsList.get(positionsList.size() - 1),
                                                              tvDestinationAddress).get();

            // Esto a lo mejor hay que ponerlo en el onPostExecute!
            tvOriginAddress.setText(String.format("%s\n\n(%f,%f)",
                                                  originAddress,
                                                  positionsList.get(0).latitude,
                                                  positionsList.get(0).longitude));
            tvDestinationAddress.setText(String.format("%s\n\n(%f,%f)",
                                                       destinationAddress,
                                                       positionsList.get(positionsList.size() - 1).latitude,
                                                       positionsList.get(positionsList.size() - 1).longitude));
        } catch (final InterruptedException e) {
            e.printStackTrace();
            Mint.logException(e);
        } catch (final ExecutionException e) {
            e.printStackTrace();
            Mint.logException(e);
        } catch (final CancellationException e) {
            Mint.logException(e);
            // No hay conexión, se cancela la búsqueda de las direcciones
            // No se hace nada aquí, ya lo hace el hilo
        }
    }

    /**
     * Fill Textview tvDistance.
     */
    private void fillDistanceInfo() {
        Mint.leaveBreadcrumb("ShowInfoActivity::fillDistanceInfo");
        tvDistance.setText(getString(R.string.info_distance_title, distance));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Mint.leaveBreadcrumb("ShowInfoActivity::onCreateOptionsMenu");
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.show_info, menu);

        // Establecer el menu Compartir
        final MenuItem shareItem = menu.findItem(R.id.action_social_share);
        final ShareActionProvider mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(shareItem);
        final Intent shareDistanceIntent = createDefaultShareIntent();
        if (verifyAppReceiveIntent(shareDistanceIntent)) {
            mShareActionProvider.setShareIntent(shareDistanceIntent);
        }
        // else mostrar un Toast
        return true;
    }

    /**
     * Creates an Intent to share data.
     *
     * @return A new Intent to show different options to share data.
     */
    private Intent createDefaultShareIntent() {
        Mint.leaveBreadcrumb("ShowInfoActivity::createDefaultShareIntent");
        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Distance From Me (http://goo.gl/0IBHFN)");

        final String extra_text = String.format("\nDistance From Me (http://goo.gl/0IBHFN)\n%s\n%s\n\n%s\n%s\n\n%s\n%s",
                                                getString(R.string.share_distance_from_message),
                                                originAddress,
                                                getString(R.string.share_distance_to_message),
                                                destinationAddress,
                                                getString(R.string.share_distance_there_are_message),
                                                distance);
        shareIntent.putExtra(Intent.EXTRA_TEXT, extra_text);
        return shareIntent;
    }

    /**
     * Verify if there are applications that can handle the intent.
     *
     * @param intent The intent to verify.
     * @return Returns <code>true</code> if there are applications; <code>false</code>, otherwise.
     */
    private boolean verifyAppReceiveIntent(final Intent intent) {
        Mint.leaveBreadcrumb("ShowInfoActivity::verifyAppReceiveIntent");
        final PackageManager packageManager = getPackageManager();
        final List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
        return activities.size() > 0;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Mint.leaveBreadcrumb("ShowInfoActivity::onOptionsItemSelected");
        switch (item.getItemId()) {
            case R.id.action_social_share:
                return true;
            case R.id.refresh:
                menuItem = item;
                fillAddressesInfo();
                return true;
            case R.id.menu_save:
                saveDataToDB("");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Saves the current data into the database.
     *
     * @param defaultText String text when orientation changes.
     */
    private void saveDataToDB(final String defaultText) {
        Mint.leaveBreadcrumb("ShowInfoActivity::saveDataToDB");
        wasSavingWhenOrientationChanged = true;
        // Pedir al usuario que introduzca un texto descriptivo
        final AlertDialog.Builder builder = new AlertDialog.Builder(ShowInfoActivity.this);
        etAlias = new EditText(getApplicationContext());
        etAlias.setTextColor(Color.BLACK);
        etAlias.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etAlias.setText(defaultText);

        builder.setMessage(getString(R.string.alias_dialog_message))
               .setTitle(getString(R.string.alias_dialog_title))
               .setView(etAlias)
               .setInverseBackgroundForced(false)
               .setOnCancelListener(new OnCancelListener() {
                   @Override
                   public void onCancel(DialogInterface dialog) {
                       wasSavingWhenOrientationChanged = false;
                   }
               })
               .setPositiveButton(getString(R.string.alias_dialog_accept), new OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int which) {
                       insertDataIntoDatabase(etAlias.getText().toString());
                       wasSavingWhenOrientationChanged = false;
                   }

                   /**
                    * Adds a new distance to the database with the current
                    * data and shows the user a message.
                    *
                    * @param alias Alias written by the user.
                    */
                   private void insertDataIntoDatabase(final String alias) {
                       Mint.leaveBreadcrumb("ShowInfoActivity::insertDataIntoDatabase");
                       String aliasToSave = "";
                       if (alias.compareTo("") != 0) {
                           aliasToSave = alias;
                       }
                       // TODO hacer esto en segundo plano
                       final Distance distance1 = new Distance(null, aliasToSave, distance, new Date());
                       final long distanceId = getApplicationDaoSession().insert(distance1);

                       for (LatLng positionAsLatLng : positionsList) {
                           final Position position = new Position(null,
                                                                  positionAsLatLng.latitude,
                                                                  positionAsLatLng.longitude,
                                                                  distanceId);
                           getApplicationDaoSession().insert(position);
                       }

                       // Mostrar un mensaje de que se ha guardado correctamente
                       if (!aliasToSave.equals("")) {
                           toastIt(getString(R.string.alias_dialog_with_name_toast, aliasToSave),
                                   getApplicationContext());
                       } else {
                           toastIt(getString(R.string.alias_dialog_no_name_toast), getApplicationContext());
                       }
                   }
               });
        (savingInDBDialog = builder.create()).show();
    }

    /**
     * A subclass of AsyncTask that calls getFromLocation() in the background.
     */
    private class GetAddressTask extends AsyncTask<Object, Void, String> {

        private Context context;

        @Override
        protected void onPreExecute() {
            Mint.leaveBreadcrumb("GetAddressTask::onPreExecute");
            super.onPreExecute();
            this.context = getApplicationContext();

            startUpdate();

            if (!isOnline(context)) {
                toastIt(getString(R.string.toast_network_problems), context);

                endUpdate();
                cancel(false);
            }
        }

        @Override
        protected String doInBackground(Object... params) {
            Mint.leaveBreadcrumb("GetAddressTask::doInBackground");
            final Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            // Get the current location from the input parameter list
            final LatLng currentLocation = (LatLng) params[0];
            // Create a list to contain the result address
            List<Address> addresses;
            try {
                addresses = geocoder.getFromLocation(currentLocation.latitude,
                                                     currentLocation.longitude, 1);
            } catch (final IOException e1) {
                e1.printStackTrace();
                Mint.logException(e1);
                return (getString(R.string.toast_no_location_found));
            } catch (final IllegalArgumentException e2) {
                // Error message to post in the log
                final String errorString = String.format("Illegal arguments %s.%s passed to address service",
                                                         Double.toString(currentLocation.latitude),
                                                         Double.toString(currentLocation.longitude));
                e2.printStackTrace();
                Mint.logException(e2);
                return errorString;
            }
            // If the reverse geocode returned an address
            if (addresses != null && addresses.size() > 0) {
                // Get the first address
                final Address address = addresses.get(0);
                // Format the first line of address (if available), city, and
                // country name.
                return String.format("%s%s%s%s",
                                     // If there's a street address, add it
                                     address.getMaxAddressLineIndex() > 0 ?
                                     address.getAddressLine(0) + "\n" : "",
                                     // Añadimos también el código postal
                                     address.getPostalCode() != null ?
                                     address.getPostalCode() + " " : "",
                                     // Locality is usually a city
                                     address.getLocality() != null ?
                                     address.getLocality() + "\n" : "",
                                     // The country of the address
                                     address.getCountryName());
            } else {
                // If there aren't any addresses, post a message
                return getString(R.string.error_no_address_found_message);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Mint.leaveBreadcrumb("GetAddressTask::onPostExecute");
            endUpdate();

            super.onPostExecute(result);
        }

        /**
         * Change the appearance of the refresh button to a ProgressBar.
         */
        private void startUpdate() {
            Mint.leaveBreadcrumb("GetAddressTask::startUpdate");
            if (menuItem != null) {
                MenuItemCompat.setActionView(menuItem, R.layout.actionbar_indeterminate_progress);
                MenuItemCompat.expandActionView(menuItem);
            }
        }

        /**
         * Restore the refresh button to his normal appearance.
         */
        private void endUpdate() {
            Mint.leaveBreadcrumb("GetAddressTask::endUpdate");
            if (menuItem != null) {
                MenuItemCompat.collapseActionView(menuItem);
                MenuItemCompat.setActionView(menuItem, null);
            }
        }
    }
}