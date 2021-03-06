/*
 * Copyright (c) 2021 David Aguiar Gonzalez
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
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import gc.david.dfm.*
import gc.david.dfm.adapter.MarkerInfoWindowAdapter
import gc.david.dfm.adapter.systemService
import gc.david.dfm.address.presentation.AddressViewModel
import gc.david.dfm.database.Distance
import gc.david.dfm.databinding.ActivityMainBinding
import gc.david.dfm.elevation.presentation.ElevationViewModel
import gc.david.dfm.main.presentation.MainViewModel
import gc.david.dfm.map.Haversine
import gc.david.dfm.service.GeofencingService
import gc.david.dfm.ui.animation.AnimatorUtil
import gc.david.dfm.ui.dialog.AddressSuggestionsDialogFragment
import gc.david.dfm.ui.dialog.DistanceSelectionDialogFragment
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class MainActivity :
        AppCompatActivity(),
        OnMapReadyCallback,
        GoogleMap.OnMapLongClickListener,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnInfoWindowClickListener {

    val appContext: Context by inject()

    private val mainViewModel: MainViewModel by viewModel()
    private val elevationViewModel: ElevationViewModel by viewModel()
    private val addressViewModel: AddressViewModel by viewModel()

    private lateinit var binding: ActivityMainBinding

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
    // Show position if we come from other app (p.e. Whatsapp)
    private var mustShowPositionWhenComingFromOutside = false
    private var sendDestinationPosition: LatLng? = null
    private val coordinates = ArrayList<LatLng>()
    private var calculatingDistance: Boolean = false
    private var progressDialog: ProgressDialog? = null

    private val selectedDistanceMode: DistanceMode
        get() = if (binding.nvDrawer.menu.findItem(R.id.menu_current_position).isChecked)
            DistanceMode.DISTANCE_FROM_CURRENT_POINT
        else
            DistanceMode.DISTANCE_FROM_ANY_POINT

    private val isLocationPermissionGranted: Boolean
        get() = ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED

    private fun isMinimiseButtonShown(): Boolean = binding.fabShowChart.isShown

    private val americanOrEuropeanLocale: Locale
        get() {
            val defaultUnit = DFMPreferences.getMeasureUnitPreference(baseContext)
            return if (DFMPreferences.MEASURE_AMERICAN_UNIT_VALUE == defaultUnit) Locale.US else Locale.FRANCE
        }

    private val onNavigationItemSelectedListener: NavigationView.OnNavigationItemSelectedListener
        get() = NavigationView.OnNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawers()
            when (menuItem.itemId) {
                R.id.menu_current_position -> {
                    menuItem.isChecked = true
                    onStartingPointSelected()
                    if (SDK_INT >= M && !isLocationPermissionGranted) {
                        Snackbar.make(binding.drawerLayout,
                                "This feature needs location permissions.",
                                Snackbar.LENGTH_INDEFINITE)
                                .setAction("Settings") {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
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
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.tag(TAG).d("onCreate savedInstanceState=%s", Utils.dumpBundleToString(savedInstanceState))

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater).apply {
            setContentView(root)
            fabMyLocation.setOnClickListener { onMyLocationClick() }
            fabShowChart.setOnClickListener { onShowChartClick() }

            setSupportActionBar(tbMain.tbMain)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setHomeButtonEnabled(true)
                val upArrow = appContext.getDrawable(R.drawable.ic_menu_white_24dp)
                setHomeAsUpIndicator(upArrow)
            }

            nvDrawer.setNavigationItemSelectedListener(onNavigationItemSelectedListener)
            elevationChartView.setOnCloseListener { animateHideChart() }

            val supportMapFragment = supportFragmentManager.findFragmentById(R.id.map2) as SupportMapFragment
            supportMapFragment.getMapAsync(this@MainActivity)
        }

        with(elevationViewModel) {
            elevationSamples.observe(this@MainActivity, { elevationSamples ->
                buildChart(elevationSamples)
            })
            hideChartEvent.observe(this@MainActivity, { event ->
                event.getContentIfNotHandled()?.let { hideChart() }
            })
        }
        with(addressViewModel) {
            connectionIssueEvent.observe(this@MainActivity, { event ->
                event.getContentIfNotHandled()?.let {
                    Utils.showAlertDialog(Settings.ACTION_SETTINGS,
                            it.title,
                            it.description,
                            it.positiveMessage,
                            it.negativeMessage,
                            this@MainActivity)
                }
            })
            progressVisibility.observe(this@MainActivity, { visible ->
                if (visible) showProgressDialog() else hideProgressDialog()
            })
            errorMessage.observe(this@MainActivity, { message ->
                Utils.toastIt(message, appContext)
            })
            addressFoundEvent.observe(this@MainActivity, { event ->
                event.getContentIfNotHandled()?.let { showPositionByName(it) }
            })
            multipleAddressesFoundEvent.observe(this@MainActivity, { event ->
                event.getContentIfNotHandled()?.let { showAddressSelectionDialog(it) }
            })
        }
        with(mainViewModel) {
            connectionIssueEvent.observe(this@MainActivity, { event ->
                event.getContentIfNotHandled()?.let {
                    Utils.showAlertDialog(Settings.ACTION_SETTINGS,
                            it.title,
                            it.description,
                            it.positiveMessage,
                            it.negativeMessage,
                            this@MainActivity)
                }
            })
            showLoadDistancesItem.observe(this@MainActivity, { visible ->
                val loadItem = binding.tbMain.tbMain.menu.findItem(R.id.action_load)
                loadItem?.isVisible = visible
            })
            showForceCrashItem.observe(this@MainActivity, { visible ->
                val crashItem = binding.tbMain.tbMain.menu.findItem(R.id.action_crash)
                crashItem?.isVisible = visible
            })
            selectFromDistancesLoaded.observe(this@MainActivity, { event ->
                event.getContentIfNotHandled()?.let { showLoadedDistancesDialog(it) }
            })
            drawDistance.observe(this@MainActivity, { model ->
                coordinates.clear()
                coordinates.addAll(Utils.convertPositionListToLatLngList(model.positionList))

                drawAndShowMultipleDistances(coordinates, model.distanceName + "\n", true)
            })
        }
        mainViewModel.onStart()

        handleIntents(intent)
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
            binding.fabMyLocation.isVisible = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isEmpty()) { // happens when the permissions request interaction with the user is interrupted
                Timber.tag(TAG).d("onRequestPermissionsResult INTERRUPTED")
                binding.fabMyLocation.isVisible = false
                binding.nvDrawer.menu.findItem(R.id.menu_any_position).isChecked = true
                onStartingPointSelected()
            } else {
                // no need to check both permissions, they fall under location group
                if (grantResults.first() == PERMISSION_GRANTED) {
                    Timber.tag(TAG).d("onRequestPermissionsResult GRANTED")

                    Utils.toastIt(R.string.toast_loading_position, appContext)
                    googleMap?.isMyLocationEnabled = true
                    binding.fabMyLocation.isVisible = true

                    registerReceiver(locationReceiver, IntentFilter(GeofencingService.GEOFENCE_RECEIVER_ACTION))
                    startService(Intent(this, GeofencingService::class.java))
                } else {
                    Timber.tag(TAG).d("onRequestPermissionsResult DENIED")
                    binding.fabMyLocation.isVisible = false
                    binding.nvDrawer.menu.findItem(R.id.menu_any_position).isChecked = true
                    onStartingPointSelected()
                }
            }
        }
    }

    override fun onMapLongClick(point: LatLng) {
        Timber.tag(TAG).d("onMapLongClick")

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
        Timber.tag(TAG).d("onMapClick")

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
        Timber.tag(TAG).d("onInfoWindowClick")

        ShowInfoActivity.open(this, coordinates, distanceMeasuredAsText)
    }

    private fun onStartingPointSelected() {
        if (selectedDistanceMode == DistanceMode.DISTANCE_FROM_CURRENT_POINT) {
            Timber.tag(TAG).d("onStartingPointSelected Distance from current point")
        } else {
            Timber.tag(TAG).d("onStartingPointSelected Distance from any point")
        }

        calculatingDistance = false

        coordinates.clear()

        googleMap?.clear()

        hideChart()
    }

    override fun onNewIntent(intent: Intent) {
        Timber.tag(TAG).d("onNewIntent %s", Utils.dumpIntentToString(intent))
        super.onNewIntent(intent)

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
        Timber.tag(TAG).d("handleSearchIntent")

        // Para controlar instancias únicas, no queremos que cada vez que
        // busquemos nos inicie una nueva instancia de la aplicación
        val query = intent.getStringExtra(SearchManager.QUERY)
        if (currentLocation != null) {
            addressViewModel.onAddressSearch(query)
        }
        binding.tbMain.tbMain.menu.findItem(R.id.action_search).collapseActionView()
    }

    private fun handleViewPositionIntent(intent: Intent) {
        val uri = intent.data ?: return
        Timber.tag(TAG).d("handleViewPositionIntent uri=$uri")

        when(uri.scheme) {
            "geo" -> handleGeoSchemeIntent(uri)
            "http", "https" -> handleHttpSchemeIntent(uri)
            else -> {
                Timber.tag(TAG).e(Exception("Unable to parse query $uri"))
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
                    addressViewModel.onAddressSearch(destination)
                    binding.tbMain.tbMain.menu.findItem(R.id.action_search).collapseActionView()
                    mustShowPositionWhenComingFromOutside = true
                }
            } else { // Manage geo:latitude,longitude or geo:latitude,longitude?z=zoom
                setDestinationPosition(matcher)
            }
        } else {
            val noSuchFieldException = NoSuchFieldException("Error al obtener las coordenadas. Matcher = $matcher")
            Timber.tag(TAG).e(noSuchFieldException)
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
                Timber.tag(TAG).e(noSuchFieldException)
                Utils.toastIt("Unable to parse address", this)
            }
        } else {
            val noSuchFieldException = NoSuchFieldException("Query sin parámetro q.")
            Timber.tag(TAG).e(noSuchFieldException)
            Utils.toastIt("Unable to parse address", this)
        }
    }

    private fun setDestinationPosition(matcher: Matcher) {
        Timber.tag(TAG).d("setDestinationPosition")

        sendDestinationPosition = LatLng(java.lang.Double.valueOf(matcher.group(1)), java.lang.Double.valueOf(matcher.group(2)))
        mustShowPositionWhenComingFromOutside = true
    }

    private fun getMatcherForUri(schemeSpecificPart: String): Matcher {
        Timber.tag(TAG).d("getMatcherForUri scheme=$schemeSpecificPart")

        val regex = "(-?\\d+(\\.\\d+)?),(-?\\d+(\\.\\d+)?)"
        val pattern = Pattern.compile(regex)
        return pattern.matcher(schemeSpecificPart)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        menu.findItem(R.id.action_search).apply {
            with(actionView as SearchView) {
                val searchManager = systemService<SearchManager>(Context.SEARCH_SERVICE)
                setSearchableInfo(searchManager.getSearchableInfo(componentName))
                isSubmitButtonEnabled = false
                isQueryRefinementEnabled = true
                setIconifiedByDefault(true)
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        mainViewModel.onMenuReady()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Timber.tag(TAG).d("onOptionsItemSelected home")
                binding.drawerLayout.openDrawer(GravityCompat.START)
                return true
            }
            R.id.action_search -> {
                Timber.tag(TAG).d("onOptionsItemSelected search")
                return true
            }
            R.id.action_load -> {
                Timber.tag(TAG).d("onOptionsItemSelected load distances from ddbb")
                mainViewModel.onLoadDistancesClick()
                return true
            }
            R.id.action_crash -> {
                Timber.tag(TAG).d("onOptionsItemSelected crash")
                mainViewModel.onForceCrashClick()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun showLoadedDistancesDialog(distances: List<Distance>) {
        DistanceSelectionDialogFragment()
                .apply {
                    setDistanceList(distances)
                    setOnDialogActionListener { position ->
                        val distance = distances[position]
                        mainViewModel.onDistanceToShowSelected(distance)
                    }
                }
                .show(supportFragmentManager, null)
    }

    private fun showRateDialog() {
        Timber.tag(TAG).d("showRateDialog")

        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.dialog_rate_app_title)
                .setMessage(R.string.dialog_rate_app_message)
                .setPositiveButton(getString(R.string.dialog_rate_app_positive_button)
                ) { dialog, _ ->
                    dialog.dismiss()
                    openPlayStoreAppPage()
                }
//                .setNegativeButton(getString(R.string.dialog_rate_app_negative_button)
//                ) { dialog, _ ->
//                    dialog.dismiss()
//                    openFeedbackActivity()
//                }
                .create()
                .show()
    }

    private fun openPlayStoreAppPage() {
        Timber.tag(TAG).d("openPlayStoreAppPage")

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=gc.david.dfm")))
        } catch (e: ActivityNotFoundException) {
            Timber.tag(TAG).e(Exception("Unable to open Play Store, rooted device?"))
        }
    }

    /**
     * Called when the Activity is no longer visible at all. Stop updates and
     * disconnect.
     */
    public override fun onStop() {
        Timber.tag(TAG).d("onStop")

        super.onStop()
        try {
            unregisterReceiver(locationReceiver)
        } catch (exception: IllegalArgumentException) {
            Timber.tag(TAG).d("onStop receiver not registered, do nothing")
        }

        stopService(Intent(this, GeofencingService::class.java))
    }

    /**
     * Called when the Activity is restarted, even before it becomes visible.
     */
    public override fun onStart() {
        Timber.tag(TAG).d("onStart")

        super.onStart()
        if (isLocationPermissionGranted) {
            registerReceiver(locationReceiver, IntentFilter(GeofencingService.GEOFENCE_RECEIVER_ACTION))
            startService(Intent(this, GeofencingService::class.java))
            googleMap?.isMyLocationEnabled = true
            binding.fabMyLocation.isVisible = true
        } else {
            binding.fabMyLocation.isVisible = false
        }
    }

    public override fun onResume() {
        super.onResume()
        mainViewModel.onResume()
    }

    public override fun onDestroy() {
        Timber.tag(TAG).d("onDestroy")

        hideChart()
        super.onDestroy()
    }

    fun onLocationChanged(location: Location) {
        Timber.tag(TAG).d("onLocationChanged")

        if (currentLocation != null) {
            currentLocation!!.set(location)
        } else {
            currentLocation = Location(location)
        }

        if (appHasJustStarted) {
            Timber.tag(TAG).d("onLocationChanged appHasJustStarted")

            if (mustShowPositionWhenComingFromOutside) {
                Timber.tag(TAG).d("onLocationChanged mustShowPositionWhenComingFromOutside")

                if (currentLocation != null && sendDestinationPosition != null) {
                    addressViewModel.onAddressSearch(sendDestinationPosition!!)

                    mustShowPositionWhenComingFromOutside = false
                }
            } else {
                Timber.tag(TAG).d("onLocationChanged NOT mustShowPositionWhenComingFromOutside")

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
        Timber.tag(TAG).d("drawAndShowMultipleDistances")

        googleMap?.clear()

        distanceMeasuredAsText = calculateDistance(coordinates)

        addMarkers(coordinates, distanceMeasuredAsText, message, isLoadingFromDB)

        addLines(coordinates, isLoadingFromDB)

        moveCameraZoom(coordinates)

        elevationViewModel.onCoordinatesSelected(coordinates)
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
        Timber.tag(TAG).d("fixMapPadding elevationChartShown ${binding.elevationChartView.isShown}")
        googleMap?.setPadding(
                0,
                if (binding.elevationChartView.isShown) binding.elevationChartView.height else 0,
                0,
                0)
    }

    private fun hideChart() {
        binding.elevationChartView.isInvisible = true
        binding.fabShowChart.isInvisible = true
        fixMapPadding()
    }

    private fun showChart() {
        binding.elevationChartView.isVisible = true
        fixMapPadding()
    }

    private fun buildChart(elevationList: List<Double>) {
        val locale = americanOrEuropeanLocale

        val normalizedElevation = elevationList.map { Haversine.normalizeAltitudeByLocale(it, locale) }
        binding.elevationChartView.setElevationProfile(normalizedElevation)
        binding.elevationChartView.setTitle(Haversine.getAltitudeUnitByLocale(locale))

        if (!isMinimiseButtonShown()) {
            showChart()
        }
    }

    private fun animateHideChart() {
        AnimatorUtil.replaceViews(binding.elevationChartView, binding.fabShowChart)
    }

    private fun animateShowChart() {
        AnimatorUtil.replaceViews(binding.fabShowChart, binding.elevationChartView)
    }

    private fun logError(errorMessage: String) {
        Timber.tag(TAG).e(Exception(errorMessage))
    }

    private fun onShowChartClick() {
        animateShowChart()
    }

    private fun onMyLocationClick() {
        currentLocation?.let {
            googleMap?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(it.latitude, it.longitude)))
        }
    }

    private fun showProgressDialog() {
        progressDialog = ProgressDialog(this).apply {
            setTitle(R.string.progressdialog_search_position_title)
            setMessage(getString(R.string.progressdialog_search_position_message))
            setCancelable(false)
            isIndeterminate = true
        }
        progressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (progressDialog?.isShowing == true) {
            progressDialog!!.dismiss()
        }
    }

    private fun showAddressSelectionDialog(addressList: List<gc.david.dfm.address.domain.model.Address>) {
        val addressSuggestionsDialogFragment = AddressSuggestionsDialogFragment()
        addressSuggestionsDialogFragment.setAddressList(addressList)
        addressSuggestionsDialogFragment.setOnDialogActionListener {
            position -> addressViewModel.onAddressSelected(addressList[position])
        }
        addressSuggestionsDialogFragment.show(supportFragmentManager, null)
    }

    private fun showPositionByName(address: gc.david.dfm.address.domain.model.Address) {
        Timber.tag(TAG).d("showPositionByName $selectedDistanceMode")

        val addressCoordinates = address.coordinates
        if (selectedDistanceMode == DistanceMode.DISTANCE_FROM_ANY_POINT) {
            coordinates.add(addressCoordinates)
            if (coordinates.isEmpty()) {
                Timber.tag(TAG).d("showPositionByName empty coordinates list")

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
                Timber.tag(TAG).d("showPositionByName appHasJustStarted")

                if (coordinates.isEmpty()) {
                    Timber.tag(TAG).d("showPositionByName empty coordinates list")

                    currentLocation?.let {
                        coordinates.add(LatLng(it.latitude, it.longitude))
                    }
                }
                coordinates.add(addressCoordinates)
                drawAndShowMultipleDistances(this.coordinates, address.formattedAddress + "\n", false)
            } else {
                Timber.tag(TAG).d("showPositionByName NOT appHasJustStarted")

                // Coming from View Action Intent
                sendDestinationPosition = addressCoordinates
            }
        }
    }

    private enum class DistanceMode {
        DISTANCE_FROM_CURRENT_POINT,
        DISTANCE_FROM_ANY_POINT
    }

    companion object {

        private const val TAG = "MainActivity"
        private val PERMISSIONS = arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
        private const val PERMISSIONS_REQUEST_CODE = 2
    }
}
