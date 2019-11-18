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

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import butterknife.BindView
import butterknife.ButterKnife.bind
import com.google.android.gms.maps.model.LatLng
import gc.david.dfm.ConnectionManager
import gc.david.dfm.DFMApplication
import gc.david.dfm.R
import gc.david.dfm.Utils
import gc.david.dfm.address.domain.GetAddressNameByCoordinatesUseCase
import gc.david.dfm.dagger.DaggerShowInfoComponent
import gc.david.dfm.dagger.RootModule
import gc.david.dfm.dagger.ShowInfoModule
import gc.david.dfm.dagger.StorageModule
import gc.david.dfm.distance.domain.InsertDistanceUseCase
import gc.david.dfm.logger.DFMLogger
import gc.david.dfm.showinfo.presentation.ShowInfo
import gc.david.dfm.showinfo.presentation.ShowInfoPresenter
import java.util.*
import javax.inject.Inject

class ShowInfoActivity : AppCompatActivity(), ShowInfo.View {

    @BindView(R.id.showinfo_activity_origin_address_textview)
    lateinit var tvOriginAddress: TextView
    @BindView(R.id.showinfo_activity_destination_address_textview)
    lateinit var tvDestinationAddress: TextView
    @BindView(R.id.showinfo_activity_distance_textview)
    lateinit var tvDistance: TextView
    @BindView(R.id.tbMain)
    lateinit var tbMain: Toolbar

    @Inject
    lateinit var appContext: Context
    @Inject
    lateinit var connectionManager: ConnectionManager
    @Inject
    lateinit var insertDistanceUseCase: InsertDistanceUseCase
    @Inject
    lateinit var getOriginAddressNameByCoordinatesUseCase: GetAddressNameByCoordinatesUseCase
    @Inject
    lateinit var getDestinationAddressNameByCoordinatesUseCase: GetAddressNameByCoordinatesUseCase

    private lateinit var showInfoPresenter: ShowInfo.Presenter

    private lateinit var positionsList: List<LatLng>
    private lateinit var distance: String

    // TODO transform this into a ViewState
    private var refreshMenuItem: MenuItem? = null
    private var savingInDBDialog: Dialog? = null
    private var etAlias: EditText? = null
    private var originAddress: String? = ""
    private var destinationAddress: String? = ""
    private var wasSavingWhenOrientationChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        DFMLogger.logMessage(TAG, "onCreate savedInstanceState=" + Utils.dumpBundleToString(savedInstanceState))

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_info)
        DaggerShowInfoComponent.builder()
                .rootModule(RootModule(application as DFMApplication))
                .storageModule(StorageModule())
                .showInfoModule(ShowInfoModule())
                .build()
                .inject(this)
        bind(this)

        setSupportActionBar(tbMain)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        showInfoPresenter = ShowInfoPresenter(this,
                getOriginAddressNameByCoordinatesUseCase,
                getDestinationAddressNameByCoordinatesUseCase,
                insertDistanceUseCase,
                connectionManager)

        getIntentData()

        if (savedInstanceState == null) {
            DFMLogger.logMessage(TAG, "onCreate savedInstanceState null, filling addresses info")

            fillAddressesInfo()
        } else {
            restorePreviousState(savedInstanceState)
        }

        fillDistanceInfo()
    }

    private fun restorePreviousState(savedInstanceState: Bundle) {
        originAddress = savedInstanceState.getString(ORIGIN_ADDRESS_STATE_KEY)
        destinationAddress = savedInstanceState.getString(DESTINATION_ADDRESS_STATE_KEY)

        tvOriginAddress.text = formatAddress(
                originAddress,
                positionsList.first().latitude,
                positionsList.first().longitude)
        tvDestinationAddress.text = formatAddress(
                destinationAddress,
                positionsList.last().latitude,
                positionsList.last().longitude)
        distance = savedInstanceState.getString(DISTANCE_STATE_KEY) ?: error("Invalid null distance")

        wasSavingWhenOrientationChanged = savedInstanceState.getBoolean(WAS_SAVING_STATE_KEY)
        if (wasSavingWhenOrientationChanged) {
            val aliasHint = savedInstanceState.getString(DISTANCE_DIALOG_NAME_STATE_KEY)
            saveDataToDB(aliasHint)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        DFMLogger.logMessage(TAG, "onSaveInstanceState outState=" + Utils.dumpBundleToString(outState))

        super.onSaveInstanceState(outState)

        outState.putString(ORIGIN_ADDRESS_STATE_KEY, originAddress)
        outState.putString(DESTINATION_ADDRESS_STATE_KEY, destinationAddress)
        outState.putString(DISTANCE_STATE_KEY, distance)

        if (wasSavingWhenOrientationChanged) {
            outState.putBoolean(WAS_SAVING_STATE_KEY, wasSavingWhenOrientationChanged)
            // TODO oh god why... move this to a dialog fragment and handle it there
            outState.putString(DISTANCE_DIALOG_NAME_STATE_KEY, etAlias?.text.toString())

            savingInDBDialog?.dismiss()
            savingInDBDialog = null
        }
    }

    private fun getIntentData() {
        DFMLogger.logMessage(TAG, "getIntentData")

        val inputDataIntent = intent
        positionsList = inputDataIntent.getParcelableArrayListExtra(POSITIONS_LIST_EXTRA_KEY) ?: error("No positions available")
        distance = inputDataIntent.getStringExtra(DISTANCE_EXTRA_KEY)
    }

    private fun fillAddressesInfo() {
        DFMLogger.logMessage(TAG, "fillAddressesInfo")

        showInfoPresenter.searchPositionByCoordinates(positionsList.first(), positionsList.last())
    }

    private fun formatAddress(address: String?, latitude: Double, longitude: Double): String {
        return "$address\n\n($latitude,$longitude)"
    }

    private fun fillDistanceInfo() {
        DFMLogger.logMessage(TAG, "fillDistanceInfo")

        tvDistance.text = getString(R.string.info_distance_title, distance)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.show_info, menu)

        refreshMenuItem = menu.findItem(R.id.refresh)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_social_share -> {
                showShareDialog()
                true
            }
            R.id.refresh -> {
                fillAddressesInfo()
                true
            }
            R.id.menu_save -> {
                saveDataToDB("")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showShareDialog() {
        startActivity(
                Intent.createChooser(
                        getDefaultShareIntent(),
                        getString(R.string.action_bar_item_social_share_title)
                )
        )
    }

    private fun getDefaultShareIntent(): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Distance From Me (http://goo.gl/0IBHFN)")
            val extraText = "\nDistance From Me (http://goo.gl/0IBHFN)\n${getString(R.string.share_distance_from_message)}\n$originAddress\n\n${getString(R.string.share_distance_to_message)}\n$destinationAddress\n\n${getString(R.string.share_distance_there_are_message)}\n$distance"
            putExtra(Intent.EXTRA_TEXT, extraText)
        }
    }

    private fun saveDataToDB(defaultText: String?) {
        wasSavingWhenOrientationChanged = true

        val builder = AlertDialog.Builder(this@ShowInfoActivity)
        etAlias = EditText(appContext).apply {
            setTextColor(Color.BLACK)
            inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(defaultText)
        }

        builder.setMessage(getString(R.string.alias_dialog_message))
                .setTitle(getString(R.string.alias_dialog_title))
                .setView(etAlias)
                .setOnCancelListener { wasSavingWhenOrientationChanged = false }
                .setPositiveButton(getString(R.string.alias_dialog_accept)) { _, _ ->
                    showInfoPresenter.saveDistance(etAlias?.text.toString(), distance, positionsList)
                    wasSavingWhenOrientationChanged = false
                }
                .create().apply { savingInDBDialog = this }
                .show()
    }

    override fun setPresenter(presenter: ShowInfo.Presenter) {
        this.showInfoPresenter = presenter
    }

    override fun showNoInternetError() {
        Utils.toastIt(getString(R.string.toast_network_problems), applicationContext)
    }

    override fun showProgress() {
        refreshMenuItem?.setActionView(R.layout.actionbar_indeterminate_progress)
    }

    override fun hideProgress() {
        refreshMenuItem?.actionView = null
    }

    override fun setAddress(address: String, isOrigin: Boolean) {
        if (isOrigin) {
            originAddress = address
            tvOriginAddress.text = formatAddress(
                    originAddress,
                    positionsList.first().latitude,
                    positionsList.first().longitude)
        } else {
            destinationAddress = address
            tvDestinationAddress.text = formatAddress(
                    destinationAddress,
                    positionsList.last().latitude,
                    positionsList.last().longitude)
        }
    }

    override fun showNoMatchesMessage(isOrigin: Boolean) {
        if (isOrigin) {
            tvOriginAddress.setText(R.string.error_no_address_found_message)
        } else {
            tvDestinationAddress.setText(R.string.error_no_address_found_message)
        }
    }

    override fun showError(errorMessage: String, isOrigin: Boolean) {
        if (isOrigin) {
            tvOriginAddress.setText(R.string.toast_no_location_found)
        } else {
            tvDestinationAddress.setText(R.string.toast_no_location_found)
        }
        DFMLogger.logException(Exception(errorMessage))
    }

    override fun showSuccessfulSave() {
        Utils.toastIt(R.string.alias_dialog_no_name_toast, appContext)
    }

    override fun showSuccessfulSaveWithName(distanceName: String) {
        Utils.toastIt(getString(R.string.alias_dialog_with_name_toast, distanceName), appContext)
    }

    override fun showFailedSave() {
        Utils.toastIt("Unable to save distance. Try again later.", appContext)
        DFMLogger.logException(Exception("Unable to insert distance into database."))
    }

    companion object {

        private val TAG = ShowInfoActivity::class.java.simpleName

        private const val POSITIONS_LIST_EXTRA_KEY = "positionsList"
        private const val DISTANCE_EXTRA_KEY = "distance"
        private const val ORIGIN_ADDRESS_STATE_KEY = "originAddressState"
        private const val DESTINATION_ADDRESS_STATE_KEY = "destinationAddressState"
        private const val DISTANCE_STATE_KEY = "distanceState"
        private const val WAS_SAVING_STATE_KEY = "wasSavingState"
        private const val DISTANCE_DIALOG_NAME_STATE_KEY = "distanceDialogName"

        fun open(activity: Activity, coordinates: List<LatLng>, distanceAsText: String) {
            val openShowInfoActivityIntent = Intent(activity, ShowInfoActivity::class.java)
            openShowInfoActivityIntent.putParcelableArrayListExtra(POSITIONS_LIST_EXTRA_KEY,
                    ArrayList<Parcelable>(coordinates))
            openShowInfoActivityIntent.putExtra(DISTANCE_EXTRA_KEY, distanceAsText)
            activity.startActivity(openShowInfoActivityIntent)
        }
    }
}
