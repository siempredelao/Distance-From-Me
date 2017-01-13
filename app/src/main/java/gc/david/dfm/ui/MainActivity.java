package gc.david.dfm.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.inmobi.ads.InMobiAdRequestStatus;
import com.inmobi.ads.InMobiBanner;
import com.inmobi.ads.InMobiBanner.BannerAdListener;
import com.inmobi.sdk.InMobiSdk;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;
import com.jjoe64.graphview.GraphViewStyle;
import com.jjoe64.graphview.LineGraphView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.OnClick;
import dagger.Lazy;
import gc.david.dfm.BuildConfig;
import gc.david.dfm.ConnectionManager;
import gc.david.dfm.DFMApplication;
import gc.david.dfm.DFMPreferences;
import gc.david.dfm.DeviceInfo;
import gc.david.dfm.PackageManager;
import gc.david.dfm.PreferencesProvider;
import gc.david.dfm.R;
import gc.david.dfm.Utils;
import gc.david.dfm.adapter.MarkerInfoWindowAdapter;
import gc.david.dfm.dagger.DaggerMainComponent;
import gc.david.dfm.dagger.MainModule;
import gc.david.dfm.dagger.RootModule;
import gc.david.dfm.dialog.AddressSuggestionsDialogFragment;
import gc.david.dfm.dialog.DistanceSelectionDialogFragment;
import gc.david.dfm.elevation.presentation.Elevation;
import gc.david.dfm.elevation.presentation.ElevationPresenter;
import gc.david.dfm.elevation.domain.ElevationUseCase;
import gc.david.dfm.feedback.Feedback;
import gc.david.dfm.feedback.FeedbackPresenter;
import gc.david.dfm.logger.DFMLogger;
import gc.david.dfm.map.Haversine;
import gc.david.dfm.map.LocationUtils;
import gc.david.dfm.model.DaoSession;
import gc.david.dfm.model.Distance;
import gc.david.dfm.model.Position;
import gc.david.dfm.service.GeofencingService;

import static butterknife.ButterKnife.bind;
import static gc.david.dfm.Utils.showAlertDialog;
import static gc.david.dfm.Utils.toastIt;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener,
                                                               OnMapReadyCallback,
                                                               OnMapLongClickListener,
                                                               OnMapClickListener,
                                                               OnInfoWindowClickListener,
                                                               Elevation.View {

    private static final String TAG = MainActivity.class.getSimpleName();

    @BindView(R.id.elevationchart)
    protected RelativeLayout       rlElevationChart;
    @BindView(R.id.closeChart)
    protected ImageView            ivCloseElevationChart;
    @BindView(R.id.tbMain)
    protected Toolbar              tbMain;
    @BindView(R.id.banner)
    protected InMobiBanner         banner;
    @BindView(R.id.drawer_layout)
    protected DrawerLayout         drawerLayout;
    @BindView(R.id.nvDrawer)
    protected NavigationView       nvDrawer;
    @BindView(R.id.main_activity_showchart_floatingactionbutton)
    protected FloatingActionButton fabShowChart;
    @BindView(R.id.main_activity_mylocation_floatingactionbutton)
    protected FloatingActionButton fabMyLocation;

    @Inject
    protected DaoSession           daoSession;
    @Inject
    protected Context              appContext;
    @Inject
    protected Lazy<PackageManager> packageManager;
    @Inject
    protected Lazy<DeviceInfo>     deviceInfo;
    @Inject
    protected ElevationUseCase     elevationUseCase;
    @Inject
    protected ConnectionManager    connectionManager;
    @Inject
    protected PreferencesProvider  preferencesProvider;

    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final double latitude = intent.getDoubleExtra(GeofencingService.GEOFENCE_RECEIVER_LATITUDE_KEY, 0D);
            final double longitude = intent.getDoubleExtra(GeofencingService.GEOFENCE_RECEIVER_LONGITUDE_KEY, 0D);
            final Location location = new Location("");
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            onLocationChanged(location);
        }
    };

    private GoogleMap       googleMap                             = null;
    private Location        currentLocation                       = null;
    // Moves to current position if app has just started
    private boolean         appHasJustStarted                     = true;
    private String          distanceMeasuredAsText                = "";
    private MenuItem        searchMenuItem                        = null;
    // Show position if we come from other app (p.e. Whatsapp)
    private boolean         mustShowPositionWhenComingFromOutside = false;
    private LatLng          sendDestinationPosition               = null;
    private GraphView       graphView                             = null;
    private List<LatLng>    coordinates                           = new ArrayList<>();
    private ActionBarDrawerToggle actionBarDrawerToggle;
    private boolean               calculatingDistance;

    private Elevation.Presenter elevationPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DFMLogger.logMessage(TAG, "onCreate savedInstanceState=" + Utils.dumpBundleToString(savedInstanceState));

        super.onCreate(savedInstanceState);
        InMobiSdk.setLogLevel(BuildConfig.DEBUG ? InMobiSdk.LogLevel.DEBUG : InMobiSdk.LogLevel.NONE);
        InMobiSdk.init(this, getString(R.string.inmobi_api_key));
        setContentView(R.layout.activity_main);
        DaggerMainComponent.builder()
                           .rootModule(new RootModule((DFMApplication) getApplication()))
                           .mainModule(new MainModule())
                           .build()
                           .inject(this);
        bind(this);

        setSupportActionBar(tbMain);

        final ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(true);
            supportActionBar.setHomeButtonEnabled(true);
        }

        elevationPresenter = new ElevationPresenter(this, elevationUseCase, connectionManager, preferencesProvider);

        final SupportMapFragment supportMapFragment = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map));
        supportMapFragment.getMapAsync(this);

        // FIXME: 11.01.17 workaround: InMobi SDK v6.0.3 stops fetching ads if banner is not visible
        // and when it is visible, it checks if height is valid (not zero)
        banner.setVisibility(View.VISIBLE);
        final ViewGroup.LayoutParams newLayoutParams = banner.getLayoutParams();
        newLayoutParams.height = 1;
        banner.setLayoutParams(newLayoutParams);

        banner.setListener(new BannerAdListener() {
            @Override
            public void onAdLoadSucceeded(InMobiBanner inMobiBanner) {
                DFMLogger.logMessage(TAG, "onAdLoadSucceeded");

                final ViewGroup.LayoutParams newLayoutParams = banner.getLayoutParams();
                newLayoutParams.height = getResources().getDimensionPixelSize(R.dimen.banner_height);
                banner.setLayoutParams(newLayoutParams);

                fixMapPadding();
            }

            @Override
            public void onAdLoadFailed(InMobiBanner inMobiBanner, InMobiAdRequestStatus inMobiAdRequestStatus) {
                DFMLogger.logMessage(TAG,
                                     String.format("onAdLoadFailed %s %s",
                                                   inMobiAdRequestStatus.getStatusCode(),
                                                   inMobiAdRequestStatus.getMessage()));
            }

            @Override
            public void onAdDisplayed(InMobiBanner inMobiBanner) {
                DFMLogger.logMessage(TAG, "onAdDisplayed");
            }

            @Override
            public void onAdDismissed(InMobiBanner inMobiBanner) {
                DFMLogger.logMessage(TAG, "onAdDismissed");
            }

            @Override
            public void onAdInteraction(InMobiBanner inMobiBanner, Map<Object, Object> map) {
                DFMLogger.logMessage(TAG, String.format("onAdInteraction %s", map.toString()));

                DFMLogger.logEvent("Ad tapped");
            }

            @Override
            public void onUserLeftApplication(InMobiBanner inMobiBanner) {
                DFMLogger.logMessage(TAG, "onUserLeftApplication");
            }

            @Override
            public void onAdRewardActionCompleted(InMobiBanner inMobiBanner, Map<Object, Object> map) {
                DFMLogger.logMessage(TAG, String.format("onAdRewardActionCompleted %s", map.toString()));
            }
        });
        if (!BuildConfig.DEBUG) {
            banner.load();
        }

        if (!connectionManager.isOnline()) {
            showConnectionProblemsDialog();
        }

        // Iniciando la app
        if (currentLocation == null) {
            toastIt(getString(R.string.toast_loading_position), appContext);
        }

        handleIntents(getIntent());

        nvDrawer.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.menu_current_position:
                        menuItem.setChecked(true);
                        drawerLayout.closeDrawers();
                        onStartingPointSelected();
                        return true;
                    case R.id.menu_any_position:
                        menuItem.setChecked(true);
                        drawerLayout.closeDrawers();
                        onStartingPointSelected();
                        return true;
                    case R.id.menu_rate_app:
                        drawerLayout.closeDrawers();
                        showRateDialog();
                        return true;
                    case R.id.menu_legal_notices:
                        drawerLayout.closeDrawers();
                        showGooglePlayServiceLicenseDialog();
                        return true;
                    case R.id.menu_settings:
                        drawerLayout.closeDrawers();
                        openSettingsActivity();
                        return true;
                    case R.id.menu_help_feedback:
                        drawerLayout.closeDrawers();
                        startActivity(new Intent(MainActivity.this, HelpAndFeedbackActivity.class));
                        return true;
                }
                return false;
            }
        });

        // TODO: 23.08.15 check if this is still needed
        actionBarDrawerToggle = new ActionBarDrawerToggle(this,
                                                          drawerLayout,
                                                          R.string.progressdialog_search_position_message,
                                                          R.string.progressdialog_search_position_message) {
            @Override
            public void onDrawerOpened(View drawerView) {
                DFMLogger.logMessage(TAG, "onDrawerOpened");

                super.onDrawerOpened(drawerView);
                supportInvalidateOptionsMenu();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                DFMLogger.logMessage(TAG, "onDrawerClosed");

                super.onDrawerClosed(drawerView);
                supportInvalidateOptionsMenu();
            }
        };
        drawerLayout.addDrawerListener(actionBarDrawerToggle);
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        googleMap = map;

        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        googleMap.setMyLocationEnabled(true);
        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        googleMap.setOnMapLongClickListener(this);
        googleMap.setOnMapClickListener(this);
        googleMap.setOnInfoWindowClickListener(this);
        googleMap.setInfoWindowAdapter(new MarkerInfoWindowAdapter(this));

        onStartingPointSelected();
    }

    @Override
    public void onMapLongClick(LatLng point) {
        DFMLogger.logMessage(TAG, "onMapLongClick");

        calculatingDistance = true;

        if (getSelectedDistanceMode() == DistanceMode.DISTANCE_FROM_ANY_POINT) {
            if (coordinates.isEmpty()) {
                toastIt(getString(R.string.toast_first_point_needed), appContext);
            } else {
                coordinates.add(point);
                drawAndShowMultipleDistances(coordinates, "", false, true);
            }
        } else if (currentLocation != null) { // Without current location, we cannot calculate any distance
            if (getSelectedDistanceMode() == DistanceMode.DISTANCE_FROM_CURRENT_POINT && coordinates.isEmpty()) {
                coordinates.add(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()));
            }
            coordinates.add(point);
            drawAndShowMultipleDistances(coordinates, "", false, true);
        }

        calculatingDistance = false;
    }

    @Override
    public void onMapClick(LatLng point) {
        DFMLogger.logMessage(TAG, "onMapClick");

        if (getSelectedDistanceMode() == DistanceMode.DISTANCE_FROM_ANY_POINT) {
            if (!calculatingDistance) {
                coordinates.clear();
            }

            calculatingDistance = true;

            if (coordinates.isEmpty()) {
                googleMap.clear();
            }
            coordinates.add(point);
            googleMap.addMarker(new MarkerOptions().position(point));
        } else {
            // Without current location, we cannot calculate any distance
            if (currentLocation != null) {
                if (!calculatingDistance) {
                    coordinates.clear();
                }
                calculatingDistance = true;

                if (coordinates.isEmpty()) {
                    googleMap.clear();
                    coordinates.add(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()));
                }
                coordinates.add(point);
                googleMap.addMarker(new MarkerOptions().position(point));
            }
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        DFMLogger.logMessage(TAG, "onInfoWindowClick");

        ShowInfoActivity.open(this, coordinates, distanceMeasuredAsText);
    }

    private void onStartingPointSelected() {
        if (getSelectedDistanceMode() == DistanceMode.DISTANCE_FROM_CURRENT_POINT) {
            DFMLogger.logMessage(TAG, "onStartingPointSelected Distance from current point");
        } else{
            DFMLogger.logMessage(TAG, "onStartingPointSelected Distance from any point");
        }

        calculatingDistance = false;

        coordinates.clear();
        googleMap.clear();

        elevationPresenter.onReset();
    }

    private DistanceMode getSelectedDistanceMode() {
        return nvDrawer.getMenu().findItem(R.id.menu_current_position).isChecked()
               ? DistanceMode.DISTANCE_FROM_CURRENT_POINT
               : DistanceMode.DISTANCE_FROM_ANY_POINT;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        DFMLogger.logMessage(TAG, "onPostCreate savedInstanceState=" + Utils.dumpBundleToString(savedInstanceState));

        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (actionBarDrawerToggle != null) {
            actionBarDrawerToggle.syncState();
        } else {
            DFMLogger.logMessage(TAG, "onPostCreate actionBarDrawerToggle null");
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        DFMLogger.logMessage(TAG, "onConfigurationChanged");

        super.onConfigurationChanged(newConfig);
        if (actionBarDrawerToggle != null) {
            actionBarDrawerToggle.onConfigurationChanged(newConfig);
        } else {
            DFMLogger.logMessage(TAG, "onConfigurationChanged actionBarDrawerToggle null");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        DFMLogger.logMessage(TAG, "onNewIntent " + Utils.dumpIntentToString(intent));

        setIntent(intent);
        handleIntents(intent);
    }

    /**
     * Handles all Intent types.
     *
     * @param intent The input intent.
     */
    private void handleIntents(final Intent intent) {
        DFMLogger.logMessage(TAG, "handleIntents " + Utils.dumpIntentToString(intent));

        if (intent != null) {
            if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
                handleSearchIntent(intent);
            } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                handleViewPositionIntent(intent);
            }
        }
    }

    /**
     * Handles a search intent.
     *
     * @param intent Input intent.
     */
    private void handleSearchIntent(final Intent intent) {
        DFMLogger.logMessage(TAG, "handleSearchIntent");

        // Para controlar instancias únicas, no queremos que cada vez que
        // busquemos nos inicie una nueva instancia de la aplicación
        final String query = intent.getStringExtra(SearchManager.QUERY);
        if (currentLocation != null) {
            new SearchPositionByName().execute(query);
        }
        if (searchMenuItem != null) {
            MenuItemCompat.collapseActionView(searchMenuItem);
        }
    }

    /**
     * Handles a send intent with position data.
     *
     * @param intent Input intent with position data.
     */
    private void handleViewPositionIntent(final Intent intent) {
        DFMLogger.logMessage(TAG, "handleViewPositionIntent");
        final Uri uri = intent.getData();
        DFMLogger.logMessage(TAG, "handleViewPositionIntent uri=" + uri.toString());

        final String uriScheme = uri.getScheme();
        if (uriScheme.equals("geo")) {
            handleGeoSchemeIntent(uri);
        } else if ((uriScheme.equals("http") || uriScheme.equals("https"))
                   && (uri.getHost().equals("maps.google.com"))) { // Manage maps.google.com?q=latitude,longitude
            handleMapsHostIntent(uri);
        } else {
            final Exception exception = new Exception("Imposible tratar la query " + uri.toString());
            DFMLogger.logException(exception);
            toastIt("Unable to parse address", this);
        }
    }

    private void handleGeoSchemeIntent(final Uri uri) {
        final String schemeSpecificPart = uri.getSchemeSpecificPart();
        final Matcher matcher = getMatcherForUri(schemeSpecificPart);
        if (matcher.find()) {
            if (matcher.group(1).equals("0") && matcher.group(2).equals("0")) {
                if (matcher.find()) { // Manage geo:0,0?q=lat,lng(label)
                    setDestinationPosition(matcher);
                } else { // Manage geo:0,0?q=my+street+address
                    String destination = Uri.decode(uri.getQuery()).replace('+', ' ');
                    destination = destination.replace("q=", "");

                    // TODO check this ugly workaround
                    new SearchPositionByName().execute(destination);
                    mustShowPositionWhenComingFromOutside = true;
                }
            } else { // Manage geo:latitude,longitude or geo:latitude,longitude?z=zoom
                setDestinationPosition(matcher);
            }
        } else {
            final NoSuchFieldException noSuchFieldException = new NoSuchFieldException("Error al obtener las coordenadas. Matcher = " +
                                                                                       matcher.toString());
            DFMLogger.logException(noSuchFieldException);
            toastIt("Unable to parse address", this);
        }
    }

    private void handleMapsHostIntent(final Uri uri) {
        final String queryParameter = uri.getQueryParameter("q");
        if (queryParameter != null) {
            final Matcher matcher = getMatcherForUri(queryParameter);
            if (matcher.find()) {
                setDestinationPosition(matcher);
            } else {
                final NoSuchFieldException noSuchFieldException = new NoSuchFieldException("Error al obtener las coordenadas. Matcher = " +
                                                                                           matcher.toString());
                DFMLogger.logException(noSuchFieldException);
                toastIt("Unable to parse address", this);
            }
        } else {
            final NoSuchFieldException noSuchFieldException = new NoSuchFieldException("Query sin parámetro q.");
            DFMLogger.logException(noSuchFieldException);
            toastIt("Unable to parse address", this);
        }
    }

    private void setDestinationPosition(final Matcher matcher) {
        DFMLogger.logMessage(TAG, "setDestinationPosition");

        sendDestinationPosition = new LatLng(Double.valueOf(matcher.group(1)), Double.valueOf(matcher.group(2)));
        mustShowPositionWhenComingFromOutside = true;
    }

    private Matcher getMatcherForUri(final String schemeSpecificPart) {
        DFMLogger.logMessage(TAG, "getMatcherForUri scheme=" + schemeSpecificPart);

        // http://regex101.com/
        // http://www.regexplanet.com/advanced/java/index.html
        final String regex = "(\\-?\\d+\\.*\\d*),(\\-?\\d+\\.*\\d*)";
        final Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(schemeSpecificPart);
    }

    private void showConnectionProblemsDialog() {
        DFMLogger.logMessage(TAG, "showConnectionProblemsDialog");

        showAlertDialog(android.provider.Settings.ACTION_SETTINGS,
                        getString(R.string.dialog_connection_problems_title),
                        getString(R.string.dialog_connection_problems_message),
                        getString(R.string.dialog_connection_problems_positive_button),
                        getString(R.string.dialog_connection_problems_negative_button),
                        MainActivity.this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        DFMLogger.logMessage(TAG, "onCreateOptionsMenu");

        // Inflate the options menu from XML
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);

        // Expandir el EditText de la búsqueda a lo largo del ActionBar
        searchMenuItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchMenuItem);
        // Configure the search info and add any event listeners
        final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        // Indicamos que la activity actual sea la buscadora
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(false);
        searchView.setQueryRefinementEnabled(true);
        searchView.setIconifiedByDefault(true);

        // Muestra el item de menú de cargar si hay elementos en la BD
        // TODO hacerlo en segundo plano
        final List<Distance> allDistances = daoSession.loadAll(Distance.class);
        if (allDistances.isEmpty()) {
            final MenuItem loadItem = menu.findItem(R.id.action_load);
            loadItem.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        DFMLogger.logMessage(TAG, "onOptionsItemSelected item=" + item.getItemId());

        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
        if (actionBarDrawerToggle != null && actionBarDrawerToggle.onOptionsItemSelected(item)) {
            DFMLogger.logMessage(TAG, "onOptionsItemSelected ActionBar home button click");

            return true;
        }

        switch (item.getItemId()) {
            case R.id.action_search:
                return true;
            case R.id.action_load:
                loadDistancesFromDB();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        DFMLogger.logMessage(TAG, "onBackPressed");

        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Loads all entries stored in the database and show them to the user in a
     * dialog.
     */
    private void loadDistancesFromDB() {
        DFMLogger.logMessage(TAG, "loadDistancesFromDB");

        // TODO hacer esto en segundo plano
        final List<Distance> allDistances = daoSession.loadAll(Distance.class);

        if (allDistances != null && !allDistances.isEmpty()) {
            final DistanceSelectionDialogFragment distanceSelectionDialogFragment = new DistanceSelectionDialogFragment();
            distanceSelectionDialogFragment.setDistanceList(allDistances);
            distanceSelectionDialogFragment.setOnDialogActionListener(new DistanceSelectionDialogFragment.OnDialogActionListener() {
                @Override
                public void onItemClick(int position) {
                    final Distance distance = allDistances.get(position);
                    final List<Position> positionList = daoSession.getPositionDao()
                                                                  ._queryDistance_PositionList(distance.getId());
                    coordinates.clear();
                    coordinates.addAll(Utils.convertPositionListToLatLngList(positionList));

                    drawAndShowMultipleDistances(coordinates, distance.getName() + "\n", true, true);
                }
            });
            distanceSelectionDialogFragment.show(getSupportFragmentManager(), null);
        }
    }

    /**
     * Shows settings activity.
     */
    private void openSettingsActivity() {
        DFMLogger.logMessage(TAG, "openSettingsActivity");

        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
    }

    /**
     * Shows rate dialog.
     */
    private void showRateDialog() {
        DFMLogger.logMessage(TAG, "showRateDialog");

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_rate_app_title)
               .setMessage(R.string.dialog_rate_app_message)
               .setPositiveButton(getString(R.string.dialog_rate_app_positive_button),
                                  new DialogInterface.OnClickListener() {
                                      @Override
                                      public void onClick(DialogInterface dialog, int which) {
                                          dialog.dismiss();
                                          openPlayStoreAppPage();
                                      }
                                  })
               .setNegativeButton(getString(R.string.dialog_rate_app_negative_button),
                                  new DialogInterface.OnClickListener() {
                                      @Override
                                      public void onClick(DialogInterface dialog, int which) {
                                          dialog.dismiss();
                                          openFeedbackActivity();
                                      }
                                  }).create().show();
    }

    /**
     * Opens Google Play Store, in Distance From Me page
     */
    private void openPlayStoreAppPage() {
        DFMLogger.logMessage(TAG, "openPlayStoreAppPage");

        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=gc.david.dfm")));
    }

    /**
     * Opens the feedback activity.
     */
    private void openFeedbackActivity() {
        DFMLogger.logMessage(TAG, "openFeedbackActivity");

        new FeedbackPresenter(new Feedback.View() {
            @Override
            public void showError() {
                toastIt(getString(R.string.toast_send_feedback_error), appContext);
            }

            @Override
            public void showEmailClient(final Intent intent) {
                startActivity(intent);
            }

            @Override
            public Context context() {
                return appContext;
            }
        }, packageManager.get(), deviceInfo.get()).start();
    }

    /**
     * Shows an AlertDialog with the Google Play Services License.
     */
    private void showGooglePlayServiceLicenseDialog() {
        DFMLogger.logMessage(TAG, "showGooglePlayServiceLicenseDialog");

        final String LicenseInfo = GoogleApiAvailability.getInstance().getOpenSourceSoftwareLicenseInfo(appContext);
        final AlertDialog.Builder LicenseDialog = new AlertDialog.Builder(MainActivity.this);
        LicenseDialog.setTitle(R.string.menu_legal_notices_title);
        LicenseDialog.setMessage(LicenseInfo);
        LicenseDialog.show();
    }

    /**
     * Called when the Activity is no longer visible at all. Stop updates and
     * disconnect.
     */
    @Override
    public void onStop() {
        DFMLogger.logMessage(TAG, "onStop");

        super.onStop();
        unregisterReceiver(locationReceiver);
        stopService(new Intent(this, GeofencingService.class));
    }

    /**
     * Called when the Activity is restarted, even before it becomes visible.
     */
    @Override
    public void onStart() {
        DFMLogger.logMessage(TAG, "onStart");

        super.onStart();
        registerReceiver(locationReceiver, new IntentFilter(GeofencingService.GEOFENCE_RECEIVER_ACTION));
        startService(new Intent(this, GeofencingService.class));
    }

    /**
     * Called when the system detects that this Activity is now visible.
     */
    @Override
    public void onResume() {
        DFMLogger.logMessage(TAG, "onResume");

        super.onResume();
        invalidateOptionsMenu();
        checkPlayServices();
    }

    @Override
    public void onDestroy() {
        DFMLogger.logMessage(TAG, "onDestroy");

        elevationPresenter.onReset();
        super.onDestroy();
    }

    /**
     * Handle results returned to this Activity by other Activities started with
     * startActivityForResult(). In particular, the method onConnectionFailed()
     * in LocationUpdateRemover and LocationUpdateRequester may call
     * startResolutionForResult() to start an Activity that handles Google Play
     * services problems. The result of this call returns here, to
     * onActivityResult.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        DFMLogger.logMessage(TAG, "onActivityResult requestCode=" + requestCode + ", " +
                                  "resultCode=" + resultCode + "intent=" + Utils.dumpIntentToString(intent));

        // Choose what to do based on the request code
        switch (requestCode) {

            // If the request code matches the code sent in onConnectionFailed
            case LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST:

                switch (resultCode) {
                    // If Google Play services resolved the problem
                    case Activity.RESULT_OK:

                        // Log the result
                        // Log.d(LocationUtils.APPTAG, getString(R.string.resolved));

                        // Display the result
                        // mConnectionState.setText(R.string.connected);
                        // mConnectionStatus.setText(R.string.resolved);
                        break;

                    // If any other result was returned by Google Play services
                    default:
                        // Log the result
                        // Log.d(LocationUtils.APPTAG,
                        // getString(R.string.no_resolution));

                        // Display the result
                        // mConnectionState.setText(R.string.disconnected);
                        // mConnectionStatus.setText(R.string.no_resolution);

                        break;
                }

                // If any other request code was received
            default:
                // Report that this Activity received an unknown requestCode
                // Log.d(LocationUtils.APPTAG,
                // getString(R.string.unknown_activity_request_code, requestCode));

                break;
        }
    }

    /**
     * Checks if Google Play Services is available on the device.
     *
     * @return Returns <code>true</code> if available; <code>false</code>
     * otherwise.
     */
    private boolean checkPlayServices() {
        DFMLogger.logMessage(TAG, "checkPlayServices");

        final GoogleApiAvailability googleApiAvailabilityInstance = GoogleApiAvailability.getInstance();
        // Comprobamos que Google Play Services está disponible en el terminal
        final int resultCode = googleApiAvailabilityInstance.isGooglePlayServicesAvailable(appContext);

        // Si está disponible, devolvemos verdadero. Si no, mostramos un mensaje
        // de error y devolvemos falso
        if (resultCode == ConnectionResult.SUCCESS) {
            DFMLogger.logMessage(TAG, "checkPlayServices success");

            return true;
        } else {
            if (googleApiAvailabilityInstance.isUserResolvableError(resultCode)) {
                DFMLogger.logMessage(TAG, "checkPlayServices isUserRecoverableError");

                final int RQS_GooglePlayServices = 1;
                googleApiAvailabilityInstance.getErrorDialog(this, resultCode, RQS_GooglePlayServices).show();
            } else {
                DFMLogger.logMessage(TAG, "checkPlayServices device not supported, finishing");

                finish();
            }
            return false;
        }
    }

    /**
     * Called by Location Services if the attempt to Location Services fails.
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        DFMLogger.logMessage(TAG, "onConnectionFailed");

        /*
         * Google Play services can resolve some errors it detects. If the error
		 * has a resolution, try sending an Intent to start a Google Play
		 * services activity that can resolve error.
		 */
        if (connectionResult.hasResolution()) {
            DFMLogger.logMessage(TAG, "onConnectionFailed connection has resolution");

            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services cancelled the original
				 * PendingIntent
				 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
                DFMLogger.logException(e);
            }
        } else {
            DFMLogger.logMessage(TAG, "onConnectionFailed connection does not have resolution");
            // If no resolution is available, display a dialog to the user with
            // the error.
            showErrorDialog(connectionResult.getErrorCode());
        }
    }

    public void onLocationChanged(final Location location) {
        DFMLogger.logMessage(TAG, "onLocationChanged");

        if (currentLocation != null) {
            currentLocation.set(location);
        } else {
            currentLocation = new Location(location);
        }

        if (appHasJustStarted) {
            DFMLogger.logMessage(TAG, "onLocationChanged appHasJustStarted");

            if (mustShowPositionWhenComingFromOutside) {
                DFMLogger.logMessage(TAG, "onLocationChanged mustShowPositionWhenComingFromOutside");

                if (currentLocation != null && sendDestinationPosition != null) {
                    new SearchPositionByCoordinates().execute(sendDestinationPosition);
                    mustShowPositionWhenComingFromOutside = false;
                }
            } else {
                DFMLogger.logMessage(TAG, "onLocationChanged NOT mustShowPositionWhenComingFromOutside");

                final LatLng latlng = new LatLng(location.getLatitude(), location.getLongitude());
                // 17 is a good zoom level for this action
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latlng, 17));
            }
            appHasJustStarted = false;
        }
    }

    /**
     * Shows a dialog returned by Google Play services for the connection error
     * code
     *
     * @param errorCode An error code returned from onConnectionFailed
     */
    private void showErrorDialog(final int errorCode) {
        DFMLogger.logMessage(TAG, "showErrorDialog errorCode=" + errorCode);

        // Get the error dialog from Google Play services
        final Dialog errorDialog = GoogleApiAvailability.getInstance().getErrorDialog(this,
                                                                                      errorCode,
                                                                                      LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);

        // If Google Play services can provide an error dialog
        if (errorDialog != null) {
            // Create a new DialogFragment in which to show the error dialog
            final ErrorDialogFragment errorFragment = new ErrorDialogFragment();

            // Set the dialog in the DialogFragment
            errorFragment.setDialog(errorDialog);

            // Show the error dialog in the DialogFragment
            errorFragment.show(getSupportFragmentManager(), "Geofence detection");
        }
    }

    private void drawAndShowMultipleDistances(final List<LatLng> coordinates,
                                              final String message,
                                              final boolean isLoadingFromDB,
                                              final boolean mustApplyZoomIfNeeded) {
        DFMLogger.logMessage(TAG, "drawAndShowMultipleDistances");

        // Borramos los antiguos marcadores y lineas
        googleMap.clear();

        // Calculamos la distancia
        distanceMeasuredAsText = calculateDistance(coordinates);

        // Pintar todos menos el primero si es desde la posición actual
        addMarkers(coordinates, distanceMeasuredAsText, message, isLoadingFromDB);

        // Añadimos las líneas
        addLines(coordinates, isLoadingFromDB);

        // Aquí hacer la animación de la cámara
        moveCameraZoom(coordinates.get(0), coordinates.get(coordinates.size() - 1), mustApplyZoomIfNeeded);

        elevationPresenter.buildChart(coordinates);
    }

    /**
     * Adds a marker to the map in a specified position and shows its info
     * window.
     *
     * @param coordinates     Positions list.
     * @param distance        Distance to destination.
     * @param message         Destination address (if needed).
     * @param isLoadingFromDB Indicates whether we are loading data from database.
     */
    private void addMarkers(final List<LatLng> coordinates,
                            final String distance,
                            final String message,
                            final boolean isLoadingFromDB) {
        DFMLogger.logMessage(TAG, "addMarkers");

        for (int i = 0; i < coordinates.size(); i++) {
            if ((i == 0 && (isLoadingFromDB || getSelectedDistanceMode() == DistanceMode.DISTANCE_FROM_ANY_POINT)) ||
                (i == coordinates.size() - 1)) {
                final LatLng coordinate = coordinates.get(i);
                final Marker marker = addMarker(coordinate);

                if (i == coordinates.size() - 1) {
                    marker.setTitle(message + distance);
                    marker.showInfoWindow();
                }
            }
        }
    }

    private Marker addMarker(final LatLng coordinate) {
        DFMLogger.logMessage(TAG, "addMarker");

        return googleMap.addMarker(new MarkerOptions().position(coordinate));
    }

    private void addLines(final List<LatLng> coordinates, final boolean isLoadingFromDB) {
        DFMLogger.logMessage(TAG, "addLines");

        for (int i = 0; i < coordinates.size() - 1; i++) {
            addLine(coordinates.get(i), coordinates.get(i + 1), isLoadingFromDB);
        }
    }

    /**
     * Adds a line between start and end positions.
     *
     * @param start Start position.
     * @param end   Destination position.
     */
    private void addLine(final LatLng start, final LatLng end, final boolean isLoadingFromDB) {
        DFMLogger.logMessage(TAG, "addLine");

        final PolylineOptions lineOptions = new PolylineOptions().add(start).add(end);
        lineOptions.width(3 * getResources().getDisplayMetrics().density);
        lineOptions.color(isLoadingFromDB ? Color.YELLOW : Color.GREEN);
        googleMap.addPolyline(lineOptions);
    }

    /**
     * Returns the distance between start and end positions normalized by device
     * locale.
     *
     * @param coordinates position list.
     * @return The normalized distance.
     */
    private String calculateDistance(final List<LatLng> coordinates) {
        DFMLogger.logMessage(TAG, "calculateDistance");

        double distanceInMetres = Utils.calculateDistanceInMetres(coordinates);

        return Haversine.normalizeDistance(distanceInMetres, getAmericanOrEuropeanLocale());
    }

    /**
     * Moves camera position and applies zoom if needed.
     *
     * @param p1 Start position.
     * @param p2 Destination position.
     */
    private void moveCameraZoom(final LatLng p1, final LatLng p2, final boolean mustApplyZoomIfNeeded) {
        DFMLogger.logMessage(TAG, "moveCameraZoom");

        double centerLat = 0.0;
        double centerLon = 0.0;

        // Diferenciamos según preferencias
        final String centre = DFMPreferences.getAnimationPreference(getBaseContext());
        if (DFMPreferences.ANIMATION_CENTRE_VALUE.equals(centre)) {
            centerLat = (p1.latitude + p2.latitude) / 2;
            centerLon = (p1.longitude + p2.longitude) / 2;
        } else if (DFMPreferences.ANIMATION_DESTINATION_VALUE.equals(centre)) {
            centerLat = p2.latitude;
            centerLon = p2.longitude;
        } else if (centre.equals(DFMPreferences.NO_ANIMATION_DESTINATION_VALUE)) {
            return;
        }

        if (mustApplyZoomIfNeeded) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(centerLat, centerLon),
                                                                      Utils.calculateZoom(p1, p2)));
        } else {
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(p2.latitude, p2.longitude)));
        }
    }

    // FIXME: 11.01.17 workaround
    private boolean isBannerShown() {
        return banner.getLayoutParams().height > 1;
    }

    private void fixMapPadding() {
        DFMLogger.logMessage(TAG,
                             String.format("fixMapPadding bannerShown %s elevationChartShown %s",
                                           isBannerShown(),
                                           rlElevationChart.isShown()));
        googleMap.setPadding(0,
                             rlElevationChart.isShown() ? rlElevationChart.getHeight() : 0,
                             0,
                             isBannerShown() ? banner.getLayoutParams().height : 0);
    }

    @Override
    public void setPresenter(final Elevation.Presenter presenter) {
        this.elevationPresenter = presenter;
    }

    @Override
    public void hideChart() {
        rlElevationChart.setVisibility(View.INVISIBLE);
        fabShowChart.setVisibility(View.INVISIBLE);
        fixMapPadding();
    }

    @Override
    public void showChart() {
        rlElevationChart.setVisibility(View.VISIBLE);
        fixMapPadding();
    }

    @Override
    public void buildChart(final List<Double> elevationList) {
        final Locale locale = getAmericanOrEuropeanLocale();

        // Creates the series and adds data to it
        final GraphViewSeries series = buildGraphViewSeries(elevationList, locale);

        if (graphView == null) {
            graphView = new LineGraphView(appContext,
                                          getString(R.string.elevation_chart_title,
                                                    Haversine.getAltitudeUnitByLocale(locale)));
            final GraphViewStyle graphViewStyle = graphView.getGraphViewStyle();
            graphViewStyle.setGridColor(Color.TRANSPARENT);
            graphViewStyle.setNumHorizontalLabels(1); // Not working with zero?
            graphViewStyle.setTextSize(getResources().getDimension(R.dimen.elevation_chart_text_size));
            graphViewStyle.setVerticalLabelsWidth(getResources().getDimensionPixelSize(R.dimen.elevation_chart_vertical_label_width));
            rlElevationChart.addView(graphView);

            ivCloseElevationChart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    elevationPresenter.onCloseChart();
                }
            });
        }
        graphView.removeAllSeries();
        graphView.addSeries(series);

        elevationPresenter.onChartBuilt();
    }

    @NonNull
    private GraphViewSeries buildGraphViewSeries(final List<Double> elevationList, final Locale locale) {
        final GraphViewSeriesStyle style = new GraphViewSeriesStyle(ContextCompat.getColor(getApplicationContext(),
                                                                                           R.color.elevation_chart_line),
                                                                    getResources().getDimensionPixelSize(R.dimen.elevation_chart_line_size));
        final GraphViewSeries series = new GraphViewSeries(null, style, new GraphView.GraphViewData[]{});

        for (int w = 0; w < elevationList.size(); w++) {
            series.appendData(new GraphView.GraphViewData(w,
                                                          Haversine.normalizeAltitudeByLocale(elevationList.get(w),
                                                                                              locale)),
                              false,
                              elevationList.size());
        }
        return series;
    }

    @Override
    public void animateHideChart() {
        AnimatorUtil.replaceViews(rlElevationChart, fabShowChart);
    }

    @Override
    public void animateShowChart() {
        AnimatorUtil.replaceViews(fabShowChart, rlElevationChart);
    }

    @Override
    public boolean isMinimiseButtonShown() {
        return fabShowChart.isShown();
    }

    @OnClick(R.id.main_activity_showchart_floatingactionbutton)
    void onShowChartClick() {
        elevationPresenter.onOpenChart();
    }

    @OnClick(R.id.main_activity_mylocation_floatingactionbutton)
    void onMyLocationClick() {
        if (currentLocation != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(currentLocation.getLatitude(),
                                                                             currentLocation.getLongitude())));
        }
    }

    private enum DistanceMode {
        DISTANCE_FROM_CURRENT_POINT,
        DISTANCE_FROM_ANY_POINT
    }

    private class SearchPositionByName extends AsyncTask<Object, Void, Integer> {

        private final String TAG = SearchPositionByName.class.getSimpleName();

        protected List<Address>  addressList;
        protected StringBuilder  fullAddress;
        protected LatLng         selectedPosition;
        protected ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            DFMLogger.logMessage(TAG, "onPreExecute");

            addressList = null;
            fullAddress = new StringBuilder();
            selectedPosition = null;

            // Comprobamos que haya conexión con internet (WiFi o Datos)
            if (!connectionManager.isOnline()) {
                showConnectionProblemsDialog();

                // Restauramos el menú y que vuelva a empezar de nuevo
                MenuItemCompat.collapseActionView(searchMenuItem);
                cancel(false);
            } else {
                progressDialog = new ProgressDialog(MainActivity.this);
                progressDialog.setTitle(R.string.progressdialog_search_position_title);
                progressDialog.setMessage(getString(R.string.progressdialog_search_position_message));
                progressDialog.setCancelable(false);
                progressDialog.setIndeterminate(true);
                progressDialog.show();
            }
        }

        @Override
        protected Integer doInBackground(Object... params) {
            DFMLogger.logMessage(TAG, "doInBackground");

            /* get latitude and longitude from the addressList */
            final Geocoder geoCoder = new Geocoder(appContext, Locale.getDefault());
            try {
                addressList = geoCoder.getFromLocationName((String) params[0], 5);
            } catch (IOException e) {
                e.printStackTrace();
                DFMLogger.logException(e);
                return -1; // Network is unavailable or any other I/O problem occurs
            }
            if (addressList == null) {
                return -3; // No backend service available
            } else if (addressList.isEmpty()) {
                return -2; // No matches were found
            } else {
                return 0;
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            DFMLogger.logMessage(TAG, "onPostExecute result=" + result);

            switch (result) {
                case 0:
                    if (addressList != null && !addressList.isEmpty()) {
                        // Si hay varios, elegimos uno. Si solo hay uno, mostramos ese
                        if (addressList.size() == 1) {
                            processSelectedAddress(0);
                            handleSelectedAddress();
                        } else {
                            final AddressSuggestionsDialogFragment addressSuggestionsDialogFragment = new AddressSuggestionsDialogFragment();
                            addressSuggestionsDialogFragment.setAddressList(addressList);
                            addressSuggestionsDialogFragment.setOnDialogActionListener(new AddressSuggestionsDialogFragment.OnDialogActionListener() {
                                @Override
                                public void onItemClick(int position) {
                                    processSelectedAddress(position);
                                    handleSelectedAddress();
                                }
                            });
                            addressSuggestionsDialogFragment.show(getSupportFragmentManager(), null);
                        }
                    }
                    break;
                case -1:
                    toastIt(getString(R.string.toast_no_find_address), appContext);
                    break;
                case -2:
                    toastIt(getString(R.string.toast_no_results), appContext);
                    break;
                case -3:
                    toastIt(getString(R.string.toast_no_find_address), appContext);
                    break;
            }
            progressDialog.dismiss();
            if (searchMenuItem != null) {
                MenuItemCompat.collapseActionView(searchMenuItem);
            }
        }

        private void handleSelectedAddress() {
            DFMLogger.logMessage(TAG, "handleSelectedAddress" + getSelectedDistanceMode());

            if (getSelectedDistanceMode() == DistanceMode.DISTANCE_FROM_ANY_POINT) {
                coordinates.add(selectedPosition);
                if (coordinates.isEmpty()) {
                    DFMLogger.logMessage(TAG, "handleSelectedAddress empty coordinates list");

                    // add marker
                    final Marker marker = addMarker(selectedPosition);
                    marker.setTitle(fullAddress.toString());
                    marker.showInfoWindow();
                    // moveCamera
                    moveCameraZoom(selectedPosition, selectedPosition, false);
                    distanceMeasuredAsText = calculateDistance(Arrays.asList(selectedPosition, selectedPosition));
                    // That means we are looking for a first position, so we want to calculate a distance starting
                    // from here
                    calculatingDistance = true;
                } else {
                    drawAndShowMultipleDistances(coordinates, fullAddress.toString(), false, true);
                }
            } else {
                if (!appHasJustStarted) {
                    DFMLogger.logMessage(TAG, "handleSelectedAddress appHasJustStarted");

                    if (coordinates.isEmpty()) {
                        DFMLogger.logMessage(TAG, "handleSelectedAddress empty coordinates list");

                        coordinates.add(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()));
                    }
                    coordinates.add(selectedPosition);
                    drawAndShowMultipleDistances(coordinates, fullAddress.toString(), false, true);
                } else {
                    DFMLogger.logMessage(TAG, "handleSelectedAddress NOT appHasJustStarted");

                    // Coming from View Action Intent
                    sendDestinationPosition = selectedPosition;
                }
            }
        }

        protected void processSelectedAddress(final int item) {
            DFMLogger.logMessage(TAG, "processSelectedAddress item=" + item);

            // Fill address info to show in the marker info window
            final Address address = addressList.get(item);
            for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                fullAddress.append(address.getAddressLine(i)).append("\n");
            }
            selectedPosition = new LatLng(address.getLatitude(), address.getLongitude());
        }
    }

    private class SearchPositionByCoordinates extends SearchPositionByName {

        private final String TAG = SearchPositionByCoordinates.class.getSimpleName();

        @Override
        protected Integer doInBackground(Object... params) {
            DFMLogger.logMessage(TAG, "doInBackground");

            /* get latitude and longitude from the addressList */
            final Geocoder geoCoder = new Geocoder(appContext, Locale.getDefault());
            final LatLng latLng = (LatLng) params[0];
            try {
                addressList = geoCoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            } catch (final IOException e) {
                e.printStackTrace();
                DFMLogger.logException(e);
                return -1; // No encuentra una dirección, no puede conectar con el servidor
            } catch (final IllegalArgumentException e) {
                final IllegalArgumentException illegalArgumentException = new IllegalArgumentException(String.format(Locale.getDefault(),
                                                                                                                     "Error en latitud=%f o longitud=%f.\n%s",
                                                                                                                     latLng.latitude,
                                                                                                                     latLng.longitude,
                                                                                                                     e.toString()));
                DFMLogger.logException(illegalArgumentException);
                throw illegalArgumentException;
            }
            if (addressList == null) {
                return -3; // empty list if there is no backend service available
            } else if (addressList.size() > 0) {
                return 0;
            } else {
                return -2; // null if no matches were found // Cuando no hay conexión que sirva
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            DFMLogger.logMessage(TAG, "onPostExecute result=" + result);

            switch (result) {
                case 0:
                    processSelectedAddress(0);
                    drawAndShowMultipleDistances(Arrays.asList(new LatLng(currentLocation.getLatitude(),
                                                                          currentLocation.getLongitude()),
                                                               selectedPosition), fullAddress.toString(),
                                                 false,
                                                 true);
                    break;
                case -1:
                    toastIt(getString(R.string.toast_no_find_address), appContext);
                    break;
                case -2:
                    toastIt(getString(R.string.toast_no_results), appContext);
                    break;
                case -3:
                    toastIt(getString(R.string.toast_no_find_address), appContext);
                    break;
            }
            progressDialog.dismiss();
        }

    }

    private Locale getAmericanOrEuropeanLocale() {
        final String defaultUnit = DFMPreferences.getMeasureUnitPreference(getBaseContext());
        return DFMPreferences.MEASURE_AMERICAN_UNIT_VALUE.equals(defaultUnit) ? Locale.US : Locale.FRANCE;
    }
}
