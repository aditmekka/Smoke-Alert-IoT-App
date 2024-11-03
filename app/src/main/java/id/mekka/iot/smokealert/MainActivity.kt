package id.mekka.iot.smokealert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.slider.Slider
import com.google.firebase.Firebase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity(){
    private lateinit var database: DatabaseReference
    private lateinit var progressBarSensor1: ProgressBar
    private lateinit var progressBarSensor2: ProgressBar
    private lateinit var progressBarSensor3: ProgressBar
    private lateinit var progressBarSensor4: ProgressBar
    private lateinit var textSensorVal1: TextView
    private lateinit var textSensorVal2: TextView
    private lateinit var textSensorVal3: TextView
    private lateinit var textSensorVal4: TextView
    private lateinit var textSmokeThreshold: TextView
    private lateinit var textDeviceStatus: TextView
    private lateinit var sliderSmokeThreshold: Slider
    private lateinit var switchBuzzerTest: Switch

    private val handler = Handler()
    private val updateRunnable: Runnable = object : Runnable {
        override fun run() {
            readData()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBarSensor1 = findViewById(R.id.progressBarSensor1)
        progressBarSensor2 = findViewById(R.id.progressBarSensor2)
        progressBarSensor3 = findViewById(R.id.progressBarSensor3)
        progressBarSensor4 = findViewById(R.id.progressBarSensor4)
        textSensorVal1 = findViewById(R.id.textSensorVal1)
        textSensorVal2 = findViewById(R.id.textSensorVal2)
        textSensorVal3 = findViewById(R.id.textSensorVal3)
        textSensorVal4 = findViewById(R.id.textSensorVal4)
        textSmokeThreshold = findViewById(R.id.textSmokeThreshold)
        sliderSmokeThreshold = findViewById(R.id.sliderSmokeThreshold)
        switchBuzzerTest = findViewById(R.id.switchBuzzerTest)
        textDeviceStatus = findViewById(R.id.deviceStatus)

        database = FirebaseDatabase.getInstance().getReference("sensorValue")

        fetchThresholdFromDatabase()

        sliderSmokeThreshold.addOnChangeListener { slider, value, fromUser ->
            textSmokeThreshold.text = "Sensor Threshold: ${value.toInt()}%"
            updateSmokeThreshold(value.toInt())
        }

        switchBuzzerTest.setOnCheckedChangeListener { _, isChecked ->
            updateBuzzerTestState(isChecked)
        }

        checkDeviceAlive()

        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    private fun fetchThresholdFromDatabase() {
        database = FirebaseDatabase.getInstance().getReference("userInput")
        database.child("smokeThreshold").get().addOnSuccessListener { dataSnapshot ->
            if (dataSnapshot.exists()) {
                val thresholdValue = dataSnapshot.getValue(Int::class.java) ?: 0
                sliderSmokeThreshold.value = thresholdValue.toFloat()
                textSmokeThreshold.text = "Sensor Threshold: $thresholdValue%"
            } else {
                Toast.makeText(this, "Threshold data not found!", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load threshold data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkThreshold(sensorValues: Map<String, Int>, threshold: Int){
        val exceededSensor = mutableListOf<String>()

        for((sensor, value) in sensorValues){
            if(value > threshold){
                exceededSensor.add(sensor)
            }
        }

        if(exceededSensor.isNotEmpty()){
            val sensorList = exceededSensor.joinToString(", ")
            val message = "Warning! ${sensorList} exceeded the threshold of $threshold%"

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            sendNotification(message)
        }
    }

    private fun sendNotification(message: String){
        val channelId = "SensorAlertChannel"
        val notificationId = 1

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val name = "Sensor Alert Channel"
            val descriptionText = "Notifies when sensor values exceed threshold"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply{
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationManager = NotificationManagerCompat.from(this)

        if(ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ){
            return
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Sensor Threshold Exceeded")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun checkDeviceAlive() {
        val handler = Handler()
        val runnableCode = object : Runnable {
            override fun run() {
                database = FirebaseDatabase.getInstance().getReference("thingStat")
                database.child("lastSeen").get().addOnSuccessListener() { dataSnapshot ->
                    val lastSeenTimestamp = dataSnapshot.getValue(Long::class.java)

                    val currentTime = Calendar.getInstance().timeInMillis / 1000

                    if(lastSeenTimestamp != null){
                        val timeDifference = currentTime - lastSeenTimestamp
                        Log.d("DeviceStatus", "Current time: $currentTime, last seen: $lastSeenTimestamp, Difference: $timeDifference")

                        if(timeDifference <= 10) {
                            textDeviceStatus.text = "Device is: ON"
                        }else{
                            textDeviceStatus.text = "Device is: OFF"
                        }
                    }else{
                        textDeviceStatus.text = "Device is: OFF"
                    }
                }.addOnFailureListener{
                    textDeviceStatus.text = "Device is: OFF"
                    Log.e("DeviceStatus", "Failed to read lastSeen")
                }
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(runnableCode)
    }

    private fun updateBuzzerTestState(isOn: Boolean) {
        database = FirebaseDatabase.getInstance().getReference("userInput")
        database.child("buzzerTest").setValue(isOn)
            .addOnSuccessListener{
                Toast.makeText(this, "Buzzer test toggled!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener{
                Toast.makeText(this, "FAILED to toggle buzzer test", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateSmokeThreshold(value: Int) {
        database = FirebaseDatabase.getInstance().getReference("userInput")
        database.child("smokeThreshold").setValue(value)
            .addOnFailureListener{
                Toast.makeText(this, "Failed to update smoke threshold", Toast.LENGTH_SHORT).show()
            }
    }

    private fun readData() {
        database = FirebaseDatabase.getInstance().getReference("sensorValue")
        val thresholdValue = sliderSmokeThreshold.value.toInt()
        val sensorValues = mutableMapOf<String, Int>()

        database.child("sensor1").get().addOnSuccessListener{ dataSnapshot ->
            if(dataSnapshot.exists()){
                val sensorVal1: Int = dataSnapshot.value.toString().toInt()
                progressBarSensor1.progress = sensorVal1
                textSensorVal1.text = "Sensor A: $sensorVal1%"
                Toast.makeText(this, "Successfully read sensor A", Toast.LENGTH_SHORT).show()

                sensorValues["Sensor A"] = sensorVal1
                checkThreshold(sensorValues, thresholdValue)
            }else {
                Toast.makeText(this, "Path does not exist!", Toast.LENGTH_SHORT).show()
            }

        }.addOnFailureListener{
            Toast.makeText(this, "FAILED to read data for sensor A", Toast.LENGTH_SHORT).show()
        }

        database = FirebaseDatabase.getInstance().getReference("sensorValue")
        database.child("sensor2").get().addOnSuccessListener{ dataSnapshot ->
            if(dataSnapshot.exists()){
                val sensorVal2: Int = dataSnapshot.value.toString().toInt()
                progressBarSensor2.progress = sensorVal2
                textSensorVal2.text = "Sensor B: $sensorVal2%"
                Toast.makeText(this, "Successfully read sensor B", Toast.LENGTH_SHORT).show()

                sensorValues["Sensor B"] = sensorVal2
                checkThreshold(sensorValues, thresholdValue)
            }else{
                Toast.makeText(this, "Path does not exist!", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener{
            Toast.makeText(this, "FAILED to read data for sensor B", Toast.LENGTH_SHORT).show()
        }

        database = FirebaseDatabase.getInstance().getReference("sensorValue")
        database.child("sensor3").get().addOnSuccessListener{ dataSnapshot ->
            if(dataSnapshot.exists()){
                val sensorVal3: Int = dataSnapshot.value.toString().toInt()
                progressBarSensor3.progress = sensorVal3
                textSensorVal3.text = "Sensor C: $sensorVal3%"
                Toast.makeText(this, "Successfully read sensor C", Toast.LENGTH_SHORT).show()

                sensorValues["Sensor C"] = sensorVal3
                checkThreshold(sensorValues, thresholdValue)
            }else{
                Toast.makeText(this, "Path does not exist!", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener{
            Toast.makeText(this, "FAILED to read data for sensor C", Toast.LENGTH_SHORT).show()
        }

        database = FirebaseDatabase.getInstance().getReference("sensorValue")
        database.child("sensor4").get().addOnSuccessListener{ dataSnapshot ->
            if(dataSnapshot.exists()){
                val sensorVal4: Int = dataSnapshot.value.toString().toInt()
                progressBarSensor4.progress = sensorVal4
                textSensorVal4.text = "Sensor D: $sensorVal4%"
                Toast.makeText(this, "Successfully read sensor D", Toast.LENGTH_SHORT).show()

                sensorValues["Sensor D"] = sensorVal4
                checkThreshold(sensorValues, thresholdValue)
            }else{
                Toast.makeText(this, "Path does not exist!", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener{
            Toast.makeText(this, "FAILED to read data for sensor D", Toast.LENGTH_SHORT).show()
        }
    }
}