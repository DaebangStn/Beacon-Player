package com.beacon.player

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.media.MediaPlayer
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import org.w3c.dom.Text
import java.lang.StringBuilder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var listView : ListView
    private val listMusic = mutableListOf<String>()
    private val listId = mutableListOf<Long>()
    var mediaPlayer: MediaPlayer? = null

    lateinit var updateSeekBar: Thread

    private var BT_SCAN_ENABLED: Boolean = false
    var advPrevData: Int = -1

    private val MUSIC_TITLE_SUFFIX = ""
    private val MUSIC_TITLE_PREFIX = ""

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLauncher: ActivityResultLauncher<Intent>

    private val leScanCb = object: ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                }
                if(result!!.device.name != null && result.scanRecord!!.manufacturerSpecificData[0] != null){

                    val textConnTime: TextView = findViewById(R.id.textConnTime)
                    val advData: Int = result.scanRecord!!.manufacturerSpecificData[0].toHex().toInt() - 30
                    if(advPrevData != null && advData != advPrevData){
                        findViewById<TextView>(R.id.textSwitch).text = advData.toString()
                        Log.w("BLE", "Scanned " + result.device.name)
                        Log.w("BLE", "Data $advData")

                        textConnTime.text = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        listView.performItemClick(listView.getChildAt(advData), advData, listView.adapter.getItemId(advData))
                        advPrevData = advData
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.w("BLE SCAN CB", "Failed$errorCode")
            }
        }

    inner class MusicListAdapter: BaseAdapter() {
        override fun getCount(): Int {
            return listMusic.size
        }

        override fun getItem(p0: Int): Any? {
            return null
        }

        override fun getItemId(p0: Int): Long {
            return 0
        }

        override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
            val myView = layoutInflater.inflate(R.layout.list_song, null)

            val textSongName = myView?.findViewById<TextView>(R.id.textSongName)
            val textOrder = myView?.findViewById<TextView>(R.id.textOrder)

            textSongName?.text = listMusic[p0]
            textOrder?.text = p0.toString()

            return myView
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.listSong)

        mediaPlayer?.stop()
        mediaPlayer?.release()

        runtimePermission()
    }

    private fun runtimePermission() {
        Dexter.withContext(this)
            .withPermissions(Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            .withListener(object: MultiplePermissionsListener {
                override fun onPermissionsChecked(p0: MultiplePermissionsReport?) {
                    displaySong()
                    btJob()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: MutableList<PermissionRequest>?,
                    p1: PermissionToken?
                ) {
                    p1?.continuePermissionRequest()
                }
            }).check()
    }

    fun displaySong(){
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE
        )

        val selection = "${MediaStore.Audio.Media.TITLE} LIKE '${MUSIC_TITLE_PREFIX}%${MUSIC_TITLE_SUFFIX}'"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            proj, selection, null, sortOrder
        )

        cursor?.use {
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

            while (cursor.moveToNext()){
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)

                Log.w("dis", title)
                listId.add(id)
                listMusic.add(title)
            }
        }

        cursor?.close()

        listView.adapter = MusicListAdapter()

        listView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, p2, _ ->
                loadAndPlaySong(p2)
            }
    }

    fun loadAndPlaySong(idx: Int){
        val textPlaySongName: TextView = findViewById(R.id.textPlaySongName)
        val textSongEnd: TextView = findViewById(R.id.textSongEnd)
        val textSongPlayed: TextView = findViewById(R.id.textSongPlayed)
        val btnPlay: Button = findViewById(R.id.btnPlay)
        val seekBar: SeekBar = findViewById(R.id.seekBar)

        mediaPlayer?.stop()
        mediaPlayer?.release()
        btnPlay.setBackgroundResource(R.drawable.ic_pause)

        val uri = Uri.withAppendedPath(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            listId[idx].toString()
        )

        val songName = listMusic[idx]
        textPlaySongName.text = songName

        mediaPlayer = MediaPlayer.create(applicationContext, uri)
        mediaPlayer?.start()

        updateSeekBar = Thread(){
            val totalDuration = mediaPlayer!!.duration
            var posCurrent: Int = 0

            while (posCurrent < totalDuration){
                try {
                    Thread.sleep(200)
                    posCurrent = mediaPlayer!!.currentPosition
                    seekBar.progress = posCurrent
                }
                catch (e: Exception){
                    e.printStackTrace()
                }
            }
        }

        seekBar.max = mediaPlayer!!.duration
        updateSeekBar.start()

        seekBar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                mediaPlayer?.seekTo(p0!!.progress)
            }
        })

        val endTime = createTime(mediaPlayer!!.duration)
        textSongEnd.text = endTime

        val delay: Int = 1000
        val handler = Handler(Looper.getMainLooper())

        handler.postDelayed(object: Runnable {
            override fun run() {
                textSongPlayed.text = createTime(mediaPlayer!!.currentPosition)
                handler.postDelayed(this, delay.toLong())
            }
        }, delay.toLong())

        mediaPlayer?.setOnCompletionListener {
            loadAndPlaySong(idx)
        }

        btnPlay.setOnClickListener {
            if(mediaPlayer!!.isPlaying){
                btnPlay.setBackgroundResource(R.drawable.ic_play)
                mediaPlayer?.pause()
            }else{
                btnPlay.setBackgroundResource(R.drawable.ic_pause)
                mediaPlayer?.start()
            }
        }

    }



    fun btJob(){
        val scanBtn: Button = findViewById(R.id.btnScan)
        val statTxt: TextView = findViewById(R.id.textStat)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if(bluetoothAdapter == null){
            Toast.makeText(applicationContext, "No bluetoothAdapter", Toast.LENGTH_SHORT).show()
            Log.e("BLE", "No bluetoothAdapter")
            finish()
        }

        scanBtn.setOnClickListener {
            if(!BT_SCAN_ENABLED){
                if(!bluetoothAdapter.isEnabled){
                    Log.w("BT", "Bluetooth on")
                    val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    bluetoothLauncher.launch(enableIntent)
                }

                statTxt.text = "Scanner On"
                BT_SCAN_ENABLED = true

                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                }
                Log.w("BLE", "Scanner on")
                bluetoothAdapter?.bluetoothLeScanner?.startScan(leScanCb)

                scanBtn.setBackgroundResource(R.drawable.ic_btstop)
            }else{
                BT_SCAN_ENABLED = false

                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                }
                Log.w("BLE", "Scanner off")
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCb)

                statTxt.text = "Scanner Off"
                scanBtn.setBackgroundResource(R.drawable.ic_btsearch)
            }
        }
    }

    fun createTime(duration: Int): String{
        var time = StringBuilder()
        val min = duration/1000/60
        val sec = duration/1000%60

        time.append(min).append(":")

        if(sec < 10){
            time.append("0")
        }
        time.append(sec)

        return time.toString()
    }

    fun ByteArray.toHex(): String = joinToString(separator = "") { each -> "%02X".format(each)}
}