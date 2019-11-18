/*
 * Copyright (c) 2019 David Aguiar Gonzalez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gc.david.dfm.ui.activity

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.AlertDialog
import android.app.ProgressDialog
import android.app.SearchManager
import android.content.*
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import butterknife.BindView
import butterknife.ButterKnife.bind
import butterknife.OnClick
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.Lazy
import gc.david.dfm.*
import gc.david.dfm.adapter.MarkerInfoWindowAdapter
import gc.david.dfm.adapter.systemService
import gc.david.dfm.address.domain.GetAddressCoordinatesByNameUseCase
import gc.david.dfm.address.domain.GetAddressNameByCoordinatesUseCase
import gc.david.dfm.address.presentation.Address
import gc.david.dfm.address.presentation.AddressPresenter
import gc.david.dfm.dagger.DaggerMainComponent
import gc.david.dfm.dagger.MainModule
import gc.david.dfm.dagger.RootModule
import gc.david.dfm.dagger.StorageModule
import gc.david.dfm.database.Distance
import gc.david.dfm.database.Position
import gc.david.dfm.deviceinfo.DeviceInfo
import gc.david.dfm.deviceinfo.PackageManager
import gc.david.dfm.distance.domain.GetPositionListUseCase
import gc.david.dfm.distance.domain.LoadDistancesUseCase
import gc.david.dfm.elevation.domain.ElevationUseCase
import gc.david.dfm.elevation.presentation.Elevation
import gc.david.dfm.elevation.presentation.ElevationPresenter
import gc.david.dfm.feedback.Feedback
import gc.david.dfm.feedback.FeedbackPresenter
import gc.david.dfm.logger.DFMLogger
import gc.david.dfm.map.Haversine
import gc.david.dfm.service.GeofencingService
import gc.david.dfm.ui.ElevationChartView
import gc.david.dfm.ui.animation.AnimatorUtil
import gc.david.dfm.ui.dialog.AddressSuggestionsDialogFragment
import gc.david.dfm.ui.dialog.DistanceSelectionDialogFragment
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.collections.ArrayList

class MainActivity :
        AppCompatActivity(),
        OnMapReadyCallback,
        GoogleMap.OnMapLongClickListener,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnInfoWindowClickListener,
        Elevation.View,
        Address.View {

    @BindView(R.id.tbMain)
    lateinit var tbMain: Toolbar
    @BindView(R.id.drawer_layout)
    lateinit var drawerLayout: DrawerLayout
    @BindView(R.id.nvDrawer)
    lateinit var nvDrawer: NavigationView
    @BindView(R.id.main_activity_showchart_floatingactionbutton)
    lateinit var fabShowChart: FloatingActionButton
    @BindView(R.id.main_activity_mylocation_floatingactionbutton)
    lateinit var fabMyLocation: FloatingActionButton
    @BindView(R.id.elevationChartView)
    lateinit var elevationChartView: ElevationChartView

    @Inject
    lateinit var appContext: Context
    @Inject
    lateinit var packageManager: Lazy<PackageManager>
    @Inject
    lateinit var deviceInfo: Lazy<DeviceInfo>
    @Inject
    lateinit var elevationUseCase: ElevationUseCase
    @Inject
    lateinit var connectionManager: ConnectionManager
    @Inject
    lateinit var preferencesProvider: PreferencesProvider
    @Inject
    lateinit var getAddressCoordinatesByNameUseCase: GetAddressCoordinatesByNameUseCase
    @Inject
    lateinit var getAddressNameByCoordinatesUseCase: GetAddressNameByCoordinatesUseCase
    @Inject
    lateinit var loadDistancesUseCase: LoadDistancesUseCase
    @Inject
    lateinit var getPositionListUseCase: GetPositionListUseCase

    private lateinit var elevationPresenter: Elevation.Presenter
    private lateinit var addressPresenter: Address.Presenter

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val latitude = intent.getDoubleExtra(GeofencingService.GEOFENCE_RECEIVER_LATITUDE_KEY, 0.0)
            val longitude = intent.getDoubleExtra(GeofencingService.GEOFENCE_RECEIVER_LONGITUDE_KEY, 0.0)
            val location = Location("").apply {
                this.latitude = latitude
                this.longitude = longitude
            }
            onLocationChanged(location)
        }
    }

    private var googleMap: GoogleMap? = null
    private var currentLocation: Location? = null
    // Moves to current position if app has just started
    private var appHasJustStarted = true
    private var distanceMeasuredAsText = ""
    private var searchMenuItem: MenuItem? = null
    // Show position if we come from other app (p.e. Whatsapp)
    private var mustShowPositionWhenComingFromOutside = false
    private var sendDestinationPosition: LatLng? = null
    private val coordinates = ArrayList<LatLng>()
    private var calculatingDistance: Boolean = false
    private var progressDialog: ProgressDialog? = null

    private val selectedDistanceMode: DistanceMode
        get() = if (nvDrawer.menu.findItem(R.id.menu_current_position).isChecked)
            DistanceMode.DISTANCE_FROM_CURRENT_POINT
        else
            DistanceMode.DISTANCE_FROM_ANY_POINT

    private val isLocationPermissionGranted: Boolean
        get() = ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED

    override fun isMinimiseButtonShown(): Boolean = fabShowChart.isShown

    private val americanOrEuropeanLocale: Locale
        get() {
            val defaultUnit = DFMPreferences.getMeasureUnitPreference(baseContext)
            return if (DFMPreferences.MEASURE_AMERICAN_UNIT_VALUE == defaultUnit) Locale.US else Locale.FRANCE
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        DFMLogger.logMessage(TAG, "onCreate savedInstanceState=" + Utils.dumpBundleToString(savedInstanceState))

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        DaggerMainComponent.builder()
                .rootModule(RootModule(application as DFMApplication))
                .storageModule(StorageModule())
                .mainModule(MainModule())
                .build()
                .inject(this)
        bind(this)

        setSupportActionBar(tbMain)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
            val upArrow = appContext.getDrawable(R.drawable.ic_menu_white_24dp)
            setHomeAsUpIndicator(upArrow)
        }

        elevationPresenter = ElevationPresenter(
                this,
                elevationUseCase,
                connectionManager,
                preferencesProvider)
        addressPresenter = AddressPresenter(
                this,
                getAddressCoordinatesByNameUseCase,
                getAddressNameByCoordinatesUseCase,
                connectionManager)

        val supportMapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        supportMapFragment?.getMapAsync(this)

        if (!connectionManager.isOnline()) {
            showConnectionProblemsDialog()
        }

        handleIntents(intent)

        nvDrawer.setNavigationItemSelectedListener(NavigationView.OnNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawers()
            when (menuItem.itemId) {
                R.id.menu_current_position -> {
                    menuItem.isChecked = true
                    onStartingPointSelected()
                    if (SDK_INT >= M && !isLocationPermissionGranted) {
                        Snackbar.make(drawerLayout,
                                "This feature needs location permissions.",
                                Snackbar.LENGTH_INDEFINITE)
                                .setAction("Settings") {
                                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    intent.data = Uri.parse("package:$packageName")
                                    startActivity(intent)
                                }
                                .show()
                    }
                    return@OnNavigationItemSelectedListener true
                }
                R.id.menu_any_position -> {
                    menuItem.isChecked = true
                    onStartingPointSelected()
                    return@OnNavigationItemSelectedListener true
                }
                R.id.menu_rate_app -> {
                    showRateDialog()
                    return@OnNavigationItemSelectedListener true
                }
                R.id.menu_settings -> {
                    SettingsActivity.open(this@MainActivity)
                    return@OnNavigationItemSelectedListener true
                }
                R.id.menu_help_feedback -> {
                    HelpAndFeedbackActivity.open(this@MainActivity)
                    return@OnNavigationItemSelectedListener true
                }
                R.id.menu_about -> {
                    AboutActivity.open(this@MainActivity)
                    return@OnNavigationItemSelectedListener true
                }
            }
            false
        })

        elevationChartView.setOnCloseListener { elevationPresenter.onCloseChart() }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isMyLocationButtonEnabled = false
            mapType = GoogleMap.MAP_TYPE_HYBRID
            setOnMapLongClickListener(this@MainActivity)
            setOnMapClickListener(this@MainActivity)
            setOnInfoWindowClickListener(this@MainActivity)
            setInfoWindowAdapter(MarkerInfoWindowAdapter(this@MainActivity))
        }

        onStartingPointSelected()

        if (SDK_INT >= M && !isLocationPermissionGranted) {
            requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE)
        } else {
            Utils.toastIt(R.string.toast_loading_position, appContext)
            googleMap?.isMyLocationEnabled = true
            fabMyLocation.isVisible = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isEmpty()) { // happens when the permissions request interaction with the user is interrupted
                DFMLogger.logMessage(TAG, "onRequestPermissionsResult INTERRUPTED")
                fabMyLocation.isVisible = false
                nvDrawer.menu.findItem(R.id.menu_any_position).isChecked = true
                onStartingPointSelected()
            }

            // not necessary to check both permissions, they fall under location group
            if (grantResults.first() == PERMISSION_GRANTED) {
                DFMLogger.logMessage(TAG, "onRequestPermissionsResult GRANTED")

                Utils.toastIt(R.string.toast_loading_position, appContext)
                googleMap?.isMyLocationEnabled = true
                fabMyLocation.isVisible = true

                registerReceiver(locationReceiver, IntentFilter(GeofencingService.GEOFENCE_RECEIVER_ACTION))
                startService(Intent(this, GeofencingService::class.java))
            } else {
                DFMLogger.logMessage(TAG, "onRequestPermissionsResult DENIED")
                fabMyLocation.isVisible = false
                nvDrawer.menu.findItem(R.id.menu_any_position).isChecked = true
                onStartingPointSelected()
            }
        }
    }

    override fun onMapLongClick(point: LatLng) {
        DFMLogger.logMessage(TAG, "onMapLongClick")

        calculatingDistance = true

        if (selectedDistanceMode == DistanceMode.DISTANCE_FROM_ANY_POINT) {
            if (coordinates.isEmpty()) {
                Utils.toastIt(R.string.toast_first_point_needed, appContext)
            } else {
                coordinates.add(point)
                drawAndShowMultipleDistances(coordinates, "", false)
            }
        } else {
            currentLocation?.let { // Without current location, we cannot calculate any distance
                if (selectedDistanceMode == DistanceMode.DISTANCE_FROM_CURRENT_POINT && coordinates.isEmpty()) {
                    coordinates.add(LatLng(it.latitude, it.longitude))
                }
                coordinates.add(point)
                drawAndShowMultipleDistances(coordinates, "", false)
            }
        }

        calculatingDistance = false
    }

    override fun onMapClick(point: LatLng) {
        DFMLogger.logMessage(TAG, "onMapClick")

        if (selectedDistanceMode == DistanceMode.DISTANCE_FROM_ANY_POINT) {
            if (!calculatingDistance) {
                coordinates.clear()
            }

            calculatingDistance = true

            if (coordinates.isEmpty()) {
                googleMap?.clear()
            }
            coordinates.add(point)
            googleMap?.addMarker(MarkerOptions().position(point))
        } else {
            currentLocation?.let { // Without current location, we cannot calculate any distance
                if (!calculatingDistance) {
                    coordinates.clear()
                }
                calculatingDistance = true

                if (coordinates.isEmpty()) {
                    googleMap?.clear()
                    coordinates.add(LatLng(it.latitude, it.longitude))
                }
                coordinates.add(point)
                googleMap?.addMarker(MarkerOptions().position(point))
            }
        }
    }

    override fun onInfoWindowClick(marker: Marker) {
        DFMLogger.logMessage(TAG, "onInfoWindowClick")

        ShowInfoActivity.open(this, coordinates, distanceMeasuredAsText)
    }

    private fun onStartingPointSelected() {
        if (selectedDistanceMode == DistanceMode.DISTANCE_FROM_CURRENT_POINT) {
            DFMLogger.logMessage(TAG, "onStartingPointSelected Distance from current point")
        } else {
            DFMLogger.logMessage(TAG, "onStartingPointSelected Distance from any point")
        }

        calculatingDistance = false

        coordinates.clear()

        googleMap?.clear()

        elevationPresenter.onReset()
    }

    override fun onNewIntent(intent: Intent) {
        DFMLogger.logMessage(TAG, "onNewIntent " + Utils.dumpIntentToString(intent))

        setIntent(intent)
        handleIntents(intent)
    }

    private fun handleIntents(intent: Intent?) {
        intent ?: return
        when(intent.action) {
            Intent.ACTION_SEARCH -> handleSearchIntent(intent)
            Intent.ACTION_VIEW -> handleViewPositionIntent(intent)
        }
    }

    private fun handleSearchIntent(intent: Intent) {
        DFMLogger.logMessage(TAG, "handleSearchIntent")

        // Para controlar instancias únicas, no queremos que cada vez que
        // busquemos nos inicie una nueva instancia de la aplicación
        val query = intent.getStringExtra(SearchManager.QUERY)
        if (currentLocation != null) {
            addressPresenter.searchPositionByName(query)
        }
        searchMenuItem?.collapseActionView()
    }

    private fun handleViewPositionIntent(intent: Intent) {
        val uri = intent.data ?: return
        DFMLogger.logMessage(TAG, "handleViewPositionIntent uri=$uri")

        when(uri.scheme) {
            "geo" -> handleGeoSchemeIntent(uri)
            "http", "https" -> handleHttpSchemeIntent(uri)
            else -> {
                DFMLogger.logException(Exception("Unable to parse query $uri"))
                Utils.toastIt("Unable to parse address", this)
            }
        }
    }

    private fun handleGeoSchemeIntent(uri: Uri) {
        val schemeSpecificPart = uri.schemeSpecificPart
        val matcher = getMatcherForUri(schemeSpecificPart)
        if (matcher.find()) {
            if (matcher.group(1) == "0" && matcher.group(2) == "0") {
                if (matcher.find()) { // Manage geo:0,0?q=lat,lng(label)
                    setDestinationPosition(matcher)
                } else { // Manage geo:0,0?q=my+street+address
                    var destination = Uri.decode(uri.query).replace('+', ' ')
                    destination = destination.replace("q=", "")

                    // TODO check this ugly workaround
                    addressPresenter.searchPositionByName(destination)
                    searchMenuItem?.collapseActionView()
                    mustShowPositionWhenComingFromOutside = true
                }
            } else { // Manage geo:latitude,longitude or geo:latitude,longitude?z=zoom
                setDestinationPosition(matcher)
            }
        } else {
            val noSuchFieldException = NoSuchFieldException("Error al obtener las coordenadas. Matcher = $matcher")
            DFMLogger.logException(noSuchFieldException)
            Utils.toastIt("Unable to parse address", this)
        }
    }

    private fun handleHttpSchemeIntent(uri: Uri) {
        if (uri.host == "maps.google.com") {
            // Manage maps.google.com?q=latitude,longitude
            handleMapsHostIntent(uri)
        }
    }

    private fun handleMapsHostIntent(uri: Uri) {
        val queryParameter = uri.getQueryParameter("q")
        if (queryParameter != null) {
            val matcher = getMatcherForUri(queryParameter)
            if (matcher.find()) {
                setDestinationPosition(matcher)
            } else {
                val noSuchFieldException = NoSuchFieldException("Error al obtener las coordenadas. Matcher = $matcher")
                DFMLogger.logException(noSuchFieldException)
                Utils.toastIt("Unable to parse address", this)
            }
        } else {
            val noSuchFieldException = NoSuchFieldException("Query sin parámetro q.")
            DFMLogger.logException(noSuchFieldException)
            Utils.toastIt("Unable to parse address", this)
        }
    }

    private fun setDestinationPosition(matcher: Matcher) {
        DFMLogger.logMessage(TAG, "setDestinationPosition")

        sendDestinationPosition = LatLng(java.lang.Double.valueOf(matcher.group(1)), java.lang.Double.valueOf(matcher.group(2)))
        mustShowPositionWhenComingFromOutside = true
    }

    private fun getMatcherForUri(schemeSpecificPart: String): Matcher {
        DFMLogger.logMessage(TAG, "getMatcherForUri scheme=$schemeSpecificPart")

        val regex = "(-?\\d+(\\.\\d+)?),(-?\\d+(\\.\\d+)?)"
        val pattern = Pattern.compile(regex)
        return pattern.matcher(schemeSpecificPart)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        searchMenuItem = menu.findItem(R.id.action_search).apply {
            with(actionView as SearchView) {
                val searchManager = systemService<SearchManager>(Context.SEARCH_SERVICE)
                // Indicamos que la activity actual sea la buscadora
                setSearchableInfo(searchManager.getSearchableInfo(componentName))
                isSubmitButtonEnabled = false
                isQueryRefinementEnabled = true
                setIconifiedByDefault(true)
            }
        }

        // TODO: 16.01.17 move this to presenter
        val loadItem = menu.findItem(R.id.action_load)
        loadDistancesUseCase.execute(object : LoadDistancesUseCase.Callback {
            override fun onDistanceListLoaded(distanceList: List<Distance>) {
                if (distanceList.isEmpty()) {
                    loadItem.isVisible = false
                }
            }

            override fun onError() {
                loadItem.isVisible = false
            }
        })

        menu.findItem(R.id.action_crash).isVisible = !Utils.isReleaseBuild()

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                DFMLogger.logMessage(TAG, "onOptionsItemSelected home")
                drawerLayout.openDrawer(GravityCompat.START)
                return true
            }
            R.id.action_search -> {
                DFMLogger.logMessage(TAG, "onOptionsItemSelected search")
                return true
            }
            R.id.action_load -> {
                DFMLogger.logMessage(TAG, "onOptionsItemSelected load distances from ddbb")
                loadDistancesFromDB()
                return true
            }
            R.id.action_crash -> {
                DFMLogger.logMessage(TAG, "onOptionsItemSelected crash")
                throw RuntimeException("User forced crash")
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun loadDistancesFromDB() {
        // TODO: 16.01.17 move this to presenter
        loadDistancesUseCase.execute(object : LoadDistancesUseCase.Callback {
            override fun onDistanceListLoaded(distanceList: List<Distance>) {
                if (distanceList.isNotEmpty()) {
                    val distanceSelectionDialogFragment = DistanceSelectionDialogFragment()
                    distanceSelectionDialogFragment.setDistanceList(distanceList)
                    distanceSelectionDialogFragment.setOnDialogActionListener { position ->
                        val distance = distanceList[position]
                        getPositionListUseCase.execute(distance.id!!, object : GetPositionListUseCase.Callback {
                            override fun onPositionListLoaded(positionList: List<Position>) {
                                coordinates.clear()
                                coordinates.addAll(Utils.convertPositionListToLatLngList(positionList))

                                drawAndShowMultipleDistances(coordinates, distance.name + "\n", true)
                            }

                            override fun onError() {
                                DFMLogger.logException(Exception("Unable to get position by id."))
                            }
                        })
                    }
                    distanceSelectionDialogFragment.show(supportFragmentManager, null)
                }
            }

            override fun onError() {
                DFMLogger.logException(Exception("Unable to load distances."))
            }
        })
    }

    private fun showRateDialog() {
        DFMLogger.logMessage(TAG, "showRateDialog")

        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.dialog_rate_app_title)
                .setMessage(R.string.dialog_rate_app_message)
                .setPositiveButton(getString(R.string.dialog_rate_app_positive_button)
                ) { dialog, _ ->
                    dialog.dismiss()
                    openPlayStoreAppPage()
                }
                .setNegativeButton(getString(R.string.dialog_rate_app_negative_button)
                ) { dialog, _ ->
                    dialog.dismiss()
                    openFeedbackActivity()
                }
                .create()
                .show()
    }

    private fun openPlayStoreAppPage() {
        DFMLogger.logMessage(TAG, "openPlayStoreAppPage")

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=gc.david.dfm")))
        } catch (e: ActivityNotFoundException) {
            DFMLogger.logException(Exception("Unable to open Play Store, rooted device?"))
        }
    }

    private fun openFeedbackActivity() {
        DFMLogger.logMessage(TAG, "openFeedbackActivity")

        FeedbackPresenter(object : Feedback.View {
            override fun showError() {
                Utils.toastIt(R.string.toast_send_feedback_error, appContext)
            }

            override fun showEmailClient(intent: Intent) {
                startActivity(intent)
            }

            override fun context(): Context {
                return appContext
            }
        }, packageManager.get(), deviceInfo.get()).start()
    }

    /**
     * Called when the Activity is no longer visible at all. Stop updates and
     * disconnect.
     */
    public override fun onStop() {
        DFMLogger.logMessage(TAG, "onStop")

        super.onStop()
        try {
            unregisterReceiver(locationReceiver)
        } catch (exception: IllegalArgumentException) {
            DFMLogger.logMessage(TAG, "onStop receiver not registered, do nothing")
        }

        stopService(Intent(this, GeofencingService::class.java))
    }

    /**
     * Called when the Activity is restarted, even before it becomes visible.
     */
    public override fun onStart() {
        DFMLogger.logMessage(TAG, "onStart")

        super.onStart()
        if (isLocationPermissionGranted) {
            registerReceiver(locationReceiver, IntentFilter(GeofencingService.GEOFENCE_RECEIVER_ACTION))
            startService(Intent(this, GeofencingService::class.java))
            googleMap?.isMyLocationEnabled = true
            fabMyLocation.isVisible = true
        } else {
            fabMyLocation.isVisible = false
        }
    }

    /**
     * Called when the system detects that this Activity is now visible.
     */
    public override fun onResume() {
        DFMLogger.logMessage(TAG, "onResume")

        super.onResume()
        invalidateOptionsMenu()
    }

    public override fun onDestroy() {
        DFMLogger.logMessage(TAG, "onDestroy")

        elevationPresenter.onReset()
        super.onDestroy()
    }

    fun onLocationChanged(location: Location) {
        DFMLogger.logMessage(TAG, "onLocationChanged")

        if (currentLocation != null) {
            currentLocation!!.set(location)
        } else {
            currentLocation = Location(location)
        }

        if (appHasJustStarted) {
            DFMLogger.logMessage(TAG, "onLocationChanged appHasJustStarted")

            if (mustShowPositionWhenComingFromOutside) {
                DFMLogger.logMessage(TAG, "onLocationChanged mustShowPositionWhenComingFromOutside")

                if (currentLocation != null && sendDestinationPosition != null) {
                    addressPresenter.searchPositionByCoordinates(sendDestinationPosition!!)

                    mustShowPositionWhenComingFromOutside = false
                }
            } else {
                DFMLogger.logMessage(TAG, "onLocationChanged NOT mustShowPositionWhenComingFromOutside")

                val latlng = LatLng(location.latitude, location.longitude)
                // 17 is a good zoom level for this action
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latlng, 17F))
            }
            appHasJustStarted = false
        }
    }

    private fun drawAndShowMultipleDistances(coordinates: List<LatLng>,
                                             message: String,
                                             isLoadingFromDB: Boolean) {
        DFMLogger.logMessage(TAG, "drawAndShowMultipleDistances")

        googleMap?.clear()

        distanceMeasuredAsText = calculateDistance(coordinates)

        addMarkers(coordinates, distanceMeasuredAsText, message, isLoadingFromDB)

        addLines(coordinates, isLoadingFromDB)

        moveCameraZoom(coordinates)

        elevationPresenter.buildChart(coordinates)
    }

    private fun addMarkers(coordinates: List<LatLng>,
                           distance: String,
                           message: String,
                           isLoadingFromDB: Boolean) {
        for (i in coordinates.indices) {
            if (i == 0
                    && (isLoadingFromDB || selectedDistanceMode == DistanceMode.DISTANCE_FROM_ANY_POINT)
                    || i == coordinates.size - 1) {
                val coordinate = coordinates[i]
                val marker = addMarker(coordinate)

                if (i == coordinates.size - 1) {
                    marker.title = message + distance
                    marker.showInfoWindow()
                }
            }
        }
    }

    private fun addMarker(coordinate: LatLng): Marker {
        return googleMap!!.addMarker(MarkerOptions().position(coordinate))
    }

    private fun addLines(coordinates: List<LatLng>, isLoadingFromDB: Boolean) {
        for (i in 0 until coordinates.size - 1) {
            addLine(coordinates[i], coordinates[i + 1], isLoadingFromDB)
        }
    }

    private fun addLine(start: LatLng, end: LatLng, isLoadingFromDB: Boolean) {
        val lineOptions = PolylineOptions().add(start).add(end)
        lineOptions.width(resources.getDimension(R.dimen.map_line_width))
        lineOptions.color(if (isLoadingFromDB) Color.YELLOW else Color.GREEN)
        googleMap?.addPolyline(lineOptions)
    }

    private fun calculateDistance(coordinates: List<LatLng>): String {
        val distanceInMetres = Utils.calculateDistanceInMetres(coordinates)

        return Haversine.normalizeDistance(distanceInMetres, americanOrEuropeanLocale)
    }

    private fun moveCameraZoom(coordinatesList: List<LatLng>) {
        when (DFMPreferences.getAnimationPreference(baseContext)) {
            DFMPreferences.ANIMATION_CENTRE_VALUE -> {
                val latLngBoundsBuilder = LatLngBounds.Builder()
                coordinatesList.forEach { latLngBoundsBuilder.include(it) }
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBoundsBuilder.build(), 100))
            }
            DFMPreferences.ANIMATION_DESTINATION_VALUE -> {
                val lastCoordinates = coordinatesList[coordinatesList.size - 1]
                googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLng(
                                LatLng(lastCoordinates.latitude, lastCoordinates.longitude)))
            }
            DFMPreferences.NO_ANIMATION_DESTINATION_VALUE -> {
                // nothing
            }
        }
    }

    private fun fixMapPadding() {
        DFMLogger.logMessage(TAG,
                "fixMapPadding elevationChartShown ${elevationChartView.isShown}")
        googleMap?.setPadding(
                0,
                if (elevationChartView.isShown) elevationChartView.height else 0,
                0,
                0)
    }

    override fun setPresenter(presenter: Elevation.Presenter) {
        this.elevationPresenter = presenter
    }

    override fun hideChart() {
        elevationChartView.isInvisible = true
        fabShowChart.isInvisible = true
        fixMapPadding()
    }

    override fun showChart() {
        elevationChartView.isVisible = true
        fixMapPadding()
    }

    override fun buildChart(elevationList: List<Double>) {
        val locale = americanOrEuropeanLocale

        val normalizedElevation = elevationList.map { Haversine.normalizeAltitudeByLocale(it, locale) }
        elevationChartView.setElevationProfile(normalizedElevation)
        elevationChartView.setTitle(Haversine.getAltitudeUnitByLocale(locale))

        elevationPresenter.onChartBuilt()
    }

    override fun animateHideChart() {
        AnimatorUtil.replaceViews(elevationChartView, fabShowChart)
    }

    override fun animateShowChart() {
        AnimatorUtil.replaceViews(fabShowChart, elevationChartView)
    }

    override fun logError(errorMessage: String) {
        DFMLogger.logException(Exception(errorMessage))
    }

    @OnClick(R.id.main_activity_showchart_floatingactionbutton)
    internal fun onShowChartClick() {
        elevationPresenter.onOpenChart()
    }

    @OnClick(R.id.main_activity_mylocation_floatingactionbutton)
    internal fun onMyLocationClick() {
        currentLocation?.let {
            googleMap?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(it.latitude, it.longitude)))
        }
    }

    override fun setPresenter(presenter: Address.Presenter) {
        this.addressPresenter = presenter
    }

    override fun showConnectionProblemsDialog() {
        DFMLogger.logMessage(TAG, "showConnectionProblemsDialog")

        Utils.showAlertDialog(android.provider.Settings.ACTION_SETTINGS,
                R.string.dialog_connection_problems_title,
                R.string.dialog_connection_problems_message,
                R.string.dialog_connection_problems_positive_button,
                R.string.dialog_connection_problems_negative_button,
                this)
    }

    override fun showProgressDialog() {
        progressDialog = ProgressDialog(this).apply {
            setTitle(R.string.progressdialog_search_position_title)
            setMessage(getString(R.string.progressdialog_search_position_message))
            setCancelable(false)
            isIndeterminate = true
        }
        progressDialog!!.show()
    }

    override fun hideProgressDialog() {
        if (progressDialog?.isShowing == true) {
            progressDialog!!.dismiss()
        }
    }

    override fun showCallError(errorMessage: String) {
        logError(errorMessage)
        Utils.toastIt(R.string.toast_no_find_address, appContext)
    }

    override fun showNoMatchesMessage() {
        Utils.toastIt(R.string.toast_no_results, appContext)
    }

    override fun showAddressSelectionDialog(addressList: List<gc.david.dfm.address.domain.model.Address>) {
        val addressSuggestionsDialogFragment = AddressSuggestionsDialogFragment()
        addressSuggestionsDialogFragment.setAddressList(addressList)
        addressSuggestionsDialogFragment.setOnDialogActionListener {
            position -> addressPresenter.selectAddressInDialog(addressList[position])
        }
        addressSuggestionsDialogFragment.show(supportFragmentManager, null)
    }

    override fun showPositionByName(address: gc.david.dfm.address.domain.model.Address) {
        DFMLogger.logMessage(TAG, "showPositionByName $selectedDistanceMode")

        val addressCoordinates = address.coordinates
        if (selectedDistanceMode == DistanceMode.DISTANCE_FROM_ANY_POINT) {
            coordinates.add(addressCoordinates)
            if (coordinates.isEmpty()) {
                DFMLogger.logMessage(TAG, "showPositionByName empty coordinates list")

                // add marker
                addMarker(addressCoordinates).apply {
                    title = address.formattedAddress
                    showInfoWindow()
                }
                // moveCamera
                googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLng(
                                LatLng(addressCoordinates.latitude, addressCoordinates.longitude)))
                distanceMeasuredAsText = ""
                // That means we are looking for a first position, so we want to calculate a distance starting
                // from here
                calculatingDistance = true
            } else {
                drawAndShowMultipleDistances(coordinates, address.formattedAddress + "\n", false)
            }
        } else {
            if (!appHasJustStarted) {
                DFMLogger.logMessage(TAG, "showPositionByName appHasJustStarted")

                if (coordinates.isEmpty()) {
                    DFMLogger.logMessage(TAG, "showPositionByName empty coordinates list")

                    currentLocation?.let {
                        coordinates.add(LatLng(it.latitude, it.longitude))
                    }
                }
                coordinates.add(addressCoordinates)
                drawAndShowMultipleDistances(this.coordinates, address.formattedAddress + "\n", false)
            } else {
                DFMLogger.logMessage(TAG, "showPositionByName NOT appHasJustStarted")

                // Coming from View Action Intent
                sendDestinationPosition = addressCoordinates
            }
        }
    }

    override fun showPositionByCoordinates(address: gc.david.dfm.address.domain.model.Address) {
        currentLocation?.let {
            drawAndShowMultipleDistances(
                    listOf(LatLng(it.latitude, it.longitude),
                    address.coordinates),
                    address.formattedAddress + "\n",
                    false)
        }
    }

    private enum class DistanceMode {
        DISTANCE_FROM_CURRENT_POINT,
        DISTANCE_FROM_ANY_POINT
    }

    companion object {

        private val TAG = MainActivity::class.java.simpleName
        private val PERMISSIONS = arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
        private const val PERMISSIONS_REQUEST_CODE = 2
    }
}
