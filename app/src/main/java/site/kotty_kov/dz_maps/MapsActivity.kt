package site.kotty_kov.dz_maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.maps.android.ktx.addMarker
import com.google.maps.android.ktx.awaitAnimation
import com.google.maps.android.ktx.awaitMap
import com.google.maps.android.ktx.model.cameraPosition
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.EasyPermissions
import site.kotty_kov.dz_maps.databinding.ActivityMapsBinding
import java.lang.reflect.Type
import java.util.*


class MapsActivity : AppCompatActivity() {

    private lateinit var googleMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private var canPlaceNew: Boolean = false
    private var editMode = false
    private var removeingMode = false
    private var markers = mutableListOf<MarkerOptions>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        loadMarkers()

        lifecycleScope.launchWhenCreated {
            googleMap = mapFragment.awaitMap()

            markers.forEach {

                googleMap.addMarker(MarkerOptions().position(it.position).title(it.title))
            }

            googleMap.setOnMarkerClickListener { marker ->
                marker.showInfoWindow()

                if (removeingMode) {
                    marker.hideInfoWindow()
                    marker.remove()
                    markers = markers.filterNot { it.title == marker.title }.toMutableList()
                    removeingMode = false
                }

                if (editMode) {
                    marker.hideInfoWindow()
                    showChangeDialog(marker.title) { newName ->
                        markers = markers.map {
                            if (it.title == marker.title) {
                                MarkerOptions().position(it.position).title(newName)
                            } else {
                                it
                            }
                        }.toMutableList()
                        marker.title = newName
                        marker.showInfoWindow()
                        editMode = false
                    }
                }

                true
            }

            googleMap.isBuildingsEnabled = true
            googleMap.isTrafficEnabled = true
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.uiSettings.setAllGesturesEnabled(true)

            googleMap.setOnMapClickListener { coords ->
                if (canPlaceNew) {
                    val position = LatLng(coords.latitude, coords.longitude)
                    showNewDialog { name ->
                        googleMap.addMarker(MarkerOptions().position(position).title(name))
                        markers.add(MarkerOptions().position(position).title(name))
                    }
                    canPlaceNew = false

                }
            }
        }
    }

    private fun loadMarkers() {
        val founderListType: Type = object : TypeToken<ArrayList<MarkersV>?>() {}.type
        getPreferences(MODE_PRIVATE)?.let { hp ->
            hp.getString("markers", "[]")?.let { list ->
                val prepList: ArrayList<MarkersV> = GsonBuilder()
                    .create()
                    .fromJson(list, founderListType)
                markers = prepList.map { MarkerOptions().position(it.coords).title(it.title) }
                    .toMutableList()

            }

        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menumap, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.newMarker -> {
                canPlaceNew = true
                editMode = true
                removeingMode = false
                Toast.makeText(this, "Отметить место на карте", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.editMarker -> {
                editMode = true
                canPlaceNew = false
                removeingMode = false
                Toast.makeText(this, "Укажите редактируемый маркер", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.deleteMarker -> {
                removeingMode = true
                canPlaceNew = false
                editMode = false
                Toast.makeText(this, "Укажите маркер для удаления", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.listAllMarkers -> {
                showAreasDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun showChangeDialog(oldName: String, newname: (String) -> Unit) {
        var returntext = "-1"

        val dialogBuilder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.new_place__dialog, null)
        dialogBuilder.setView(dialogView)
            .setPositiveButton("OK") { a, b ->
                returntext = dialogView.findViewById<EditText>(R.id.newPlace).text.toString()
                newname(returntext)
            }
            .setNegativeButton("Отменить") { a, b -> }

        dialogView.findViewById<EditText>(R.id.newPlace).setText(oldName)
        dialogBuilder.create().show()
    }

    fun showNewDialog(name: (String) -> Unit) {
        var returntext = "-1"
        val dialogBuilder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.new_place__dialog, null)
        dialogBuilder.setView(dialogView)
            .setPositiveButton("OK") { a, b ->
                returntext = dialogView.findViewById<EditText>(R.id.newPlace).text.toString()
                name(returntext)
            }
            .setNegativeButton("Отменить") { a, b -> }

        dialogBuilder.create().show()
    }

    fun showAreasDialog() {
        val charSequence: Array<CharSequence> =
            markers.map { it.title as CharSequence }.toTypedArray()

        val builder = AlertDialog.Builder(this);
        builder.setTitle("Перейти к маркеру")
            .setSingleChoiceItems(charSequence, 0) { d, i ->
                lifecycleScope.launch {
                    googleMap.awaitAnimation(CameraUpdateFactory.newCameraPosition(cameraPosition {
                        target(markers.filter { it.title == charSequence[i].toString() }[0].position)
                        zoom(9F)
                    }))
                }
                d.dismiss()
            }
            .setNegativeButton("Отменить") { a, b -> }

        builder.create().show()
    }
}

data class MarkersV(val title: String, val coords: LatLng)



