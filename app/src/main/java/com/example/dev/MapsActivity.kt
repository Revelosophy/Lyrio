package com.example.dev

import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.location.LocationManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import kotlin.random.Random
import android.widget.Button
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.*
import android.content.pm.PackageManager
import android.Manifest
import android.widget.Toast
import android.content.Intent
import android.content.Context
import android.provider.Settings
import android.view.LayoutInflater
import android.view.animation.AnimationUtils.loadAnimation
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.dialog_layout.*
import kotlinx.android.synthetic.main.dialog_layout.view.*
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_maps.*
import kotlinx.android.synthetic.main.dialog_layout.view.lyric_view

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var lastLocation: Location
    private lateinit var currentLatLng: LatLng
    private lateinit var classicSongTitles: Array<String>
    private lateinit var currentSongTitles: Array<String>
    private lateinit var currentSong: String
    private var darkMap: Boolean = true
    private var classicMode: Boolean = true
    private var markerNumber: Int = 0
    private var currentScore: Double = 0.0
    private var numberOfSkips: Int = 3
    private var scoreMultiplier: Double = 1.0
    private var collectedLyrics = emptyArray<String>()

    companion object {
        private const val PERMISSION_ID = 42
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // List of all songs for each category
        classicSongTitles = assets.list("Classic")
        currentSongTitles = assets.list("Current")

        // References to buttons and TextViews
        val guessButton = findViewById<Button>(R.id.button1)
        val mapStyleButton = findViewById<Button>(R.id.button2)
        val skipButton = findViewById<Button>(R.id.button3)
        val gameModeButton = findViewById<Button>(R.id.button4)
        val scoreText = findViewById<TextView>(R.id.score)
        val multiplierText = findViewById<TextView>(R.id.multiplier)
        scoreText.text = currentScore.toString()
        multiplierText.text = scoreMultiplier.toString()

        // Handles skip button. Skips if user has enough skips.
        skipButton.setOnClickListener {
            if (numberOfSkips > 0) {
                mMap.clear()
                setScore(3)
                numberOfSkips --
                createLyricMarkers()
            } else {
                Toast.makeText(this, R.string.skip_toast, Toast.LENGTH_SHORT).show()
            }
        }

        // Calls the guess dialog function
        guessButton.setOnClickListener {
            guessFunction()
        }

        // Changes map style
        mapStyleButton.setOnClickListener {
            if (!darkMap) {
                mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
                darkMap = true
            } else {
                mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))
                darkMap = false
            }
        }

        // Chnages the game mode and refreshes.
        gameModeButton.setOnClickListener {
            classicMode = !classicMode
            mMap.clear()
            createLyricMarkers()
            if (classicMode){
                Toast.makeText(this,R.string.classic_toast, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.current_toast, Toast.LENGTH_SHORT).show()
            }
        }

        // If location is not enabled, go to settings and change that
        if (!isLocationEnabled()) {
            Toast.makeText(this, R.string.location_services, Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }

        checkLocationPermission()
    }
    
    
    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.isMyLocationEnabled = true
        createLyricMarkers()


        // All of this is inside a on click listener for each marker.
        mMap.setOnMarkerClickListener(object : GoogleMap.OnMarkerClickListener {
            override fun onMarkerClick(marker: Marker): Boolean {
                                                    
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location : Location ->
                        lastLocation = location

                        // Calculates distance between user and marker
                        val x = distanceBetweenPoints(marker.position.latitude, marker.position.longitude,
                            lastLocation.latitude, lastLocation.longitude)

                        if (x < 0.01) {
                            collectedLyrics += marker.title
                            marker.remove()
                            Toast.makeText(baseContext, marker.title.toString(), Toast.LENGTH_LONG).show()
                            setScore(0)
                        } else {
                            Toast.makeText(baseContext, R.string.too_far, Toast.LENGTH_LONG).show()
                        }
                    }
                return true
            }
        })

    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf
                    (Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_ID)
            return
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    //  Function responsible for randomly creating and placing each marker on the map.
    private fun createLyricMarkers() {

        collectedLyrics = emptyArray()

        if (classicMode){
             currentSong = classicSongTitles.random()
        } else {
            currentSong = currentSongTitles.random()
        }

        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            if (location != null) {
                lastLocation = location
                currentLatLng = LatLng(location.latitude, location.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng))

                val listOfLyrics : MutableList<String>?

                listOfLyrics = getSongLyrics(currentSong)

                for (i in 0 until listOfLyrics.size - 1) {

                    val newLat = Random.nextDouble((location.latitude-00.000500), (location.latitude+00.000500))
                    val newLng = Random.nextDouble((location.longitude-0.00200), (location.longitude+0.00200))
                    val final = LatLng(newLat, newLng)

                    val lyric = listOfLyrics.random()
                    listOfLyrics.remove(lyric)

                    mMap.addMarker(MarkerOptions()
                        .position(final)
                        .title(lyric))
                        .setIcon(BitmapDescriptorFactory.fromResource(R.drawable.new_icon))

                    markerNumber++
                }
            }
        }
    }

    // Function that calculates the disntance between the user and the marker the user has clicked.
    private fun distanceBetweenPoints(lat1:Double, lng1:Double, lat2:Double, lng2:Double): Double {

        val radiusOfEarth = 3958.75

        val latitudeDistance = Math.toRadians(lat2 - lat1)
        val longitudeDistance = Math.toRadians(lng2 - lng1)
        val sLatitudeDistance = Math.sin(latitudeDistance / 2)
        val sLongitudeDistance = Math.sin(longitudeDistance / 2)

        val a = Math.pow(sLatitudeDistance, 2.0) + Math.pow(sLongitudeDistance, 2.0) *
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))

        val b = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return radiusOfEarth * b

    }

    // Creates the dialog box that allows user to guess song or artist.
    // Also handles all of the logic behind guessing, string manipulation and setting points.
    private fun guessFunction() {

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_layout, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        dialogView.lyric_view.movementMethod = ScrollingMovementMethod()


        val dialogShaker = loadAnimation(this, R.anim.shake)

        val guessDialog = dialog.show()

        val artistName = currentSong.substringBefore("(")
            .replace("_"," ", ignoreCase = true)

        val songName = currentSong.substring(currentSong.indexOf("(") + 1,
            currentSong.indexOf(")"))
            .replace("_"," ", ignoreCase = true)


        if (collectedLyrics.count() > 0) {
            val lyricsView = dialogView.lyric_view
            for (i in 0 until collectedLyrics.count()) {
                lyricsView.append(collectedLyrics[i] + "\n")
            }
        }


        dialogView.guess_button.setOnClickListener {

            val userGuess = guessDialog.guess_input.text.toString()


            if (userGuess.equals(artistName, ignoreCase = true) || userGuess.equals(songName, ignoreCase = true)) {
                guessDialog.dismiss()
                mMap.clear()
                if (collectedLyrics.count() < 2) {
                    scoreMultiplier += .5
                    multiplier.text = scoreMultiplier.toString()
                    setScore(1)
                } else {
                    setScore(collectedLyrics)
                }
                createLyricMarkers()
                Toast.makeText(baseContext, R.string.correct_guess, Toast.LENGTH_LONG).show()

            } else {
                scoreMultiplier = 0.0
                multiplier.text = scoreMultiplier.toString()
                setScore(2)
                guessDialog.guess_input.setText("")
                dialogView.guess_input.startAnimation(dialogShaker)
                Toast.makeText(baseContext, R.string.wrong_guess, Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.back_button.setOnClickListener{
            guessDialog.dismiss()
        }
    }

    // Gets all of the lyrics for any song and drops them in a mutable list.
    private fun getSongLyrics(songTitle: String) : MutableList<String> {

        val songFileName = StringBuilder()
        val listOfLyrics = mutableListOf<String>()

        if (classicMode) {
            songFileName.append("Classic/").append(songTitle)
        } else {
            songFileName.append("Current/").append(songTitle)
        }
        applicationContext.assets.open(songFileName.toString()).bufferedReader().useLines{
            lines -> lines.forEach { listOfLyrics.add(it) }
        }
        return listOfLyrics
    }

    // Sets score based on what is passed into the function.
    private fun setScore (scoreType: Int) {
        when(scoreType) {
            0 -> currentScore += 20 * scoreMultiplier // Score for single lyric pick-up
            1 -> currentScore += 200 * scoreMultiplier // Score for guessing with one lyric
            2 -> currentScore -= 25 * scoreMultiplier // Penalty for wrong guess
            3 -> currentScore = 0.0 * scoreMultiplier // Penalty for skipping song
        }
        score.text = currentScore.toString()
        if (scoreType == 0 && numberOfSkips < 1 && currentScore > 50) {
            numberOfSkips = 3
            Toast.makeText(this, R.string.earned_skips,Toast.LENGTH_SHORT).show()
        }
    }

    private fun setScore (amountOfLyrics: Array<String>) {
        currentScore += (200 - amountOfLyrics.count()) * scoreMultiplier
        score.text = currentScore.toString()
    }
}