package com.example.uma_ros2_android_lite;

// Author: Manuel Cordoba Ramos <manuelcordoba123@gmail.com>
// Crea el nodo de ROS 2.
// Create the ROS 2 node.

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;
import androidx.core.app.ActivityCompat;


import android.graphics.YuvImage;

import org.ros2.rcljava.node.BaseComposableNode;
import org.ros2.rcljava.publisher.Publisher;
import org.ros2.rcljava.qos.QoSProfile;
import org.ros2.rcljava.timer.WallTimer;
import org.ros2.rcljava.subscription.Subscription;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import std_msgs.msg.UInt8MultiArray;

public class AndroidNode extends BaseComposableNode {

    private static String logtag = AndroidNode.class.getName();

    public Publisher<std_msgs.msg.UInt8> battery_publisher; //Publicador de la bateria

    public Publisher<std_msgs.msg.UInt8MultiArray> image_publisher; //Publicador de la imagen

    public Publisher<std_msgs.msg.Float64MultiArray> location_publisher; //Publicador de la localizacion

    public Publisher<std_msgs.msg.Float32MultiArray> imu_publisher; //Publicador de la IMU

    private Subscription<std_msgs.msg.UInt8MultiArray> subscriber;

    private Subscription<std_msgs.msg.String> subscriber_data;


    private WallTimer timer_battery;

    private WallTimer timer_location;

    private WallTimer timer_imu;

    private Context context;

    private LocationManager locationManager;

    std_msgs.msg.UInt8MultiArray msg_image = new std_msgs.msg.UInt8MultiArray();

    private SensorManager sensorManager;
    private Sensor orientationSensor;
    private Sensor gyroscopeSensor;
    private Sensor accelerometerSensor;
    private String AgentName;

    private int targetResolution;
    private boolean BatteryON;
    private boolean LocationON;
    private boolean IMUON;

    private String lastData;

    private boolean IntermittentMode;
    private Runnable onMessageReceivedRunnable;

    private Runnable onDataReceivedRunnable;

    private long batteryPeriod, locationPeriod, IMUPeriod;

    public AndroidNode(final String NodeName, final String AgentName,
                      final boolean BatteryON, final boolean LocationON, final boolean IMUON,
                      int targetResolution,
                      long batteryPeriod, long locationPeriod, long IMUPeriod,
                      boolean IntermittentMode,
                      Context context) {

        super(NodeName);
        this.AgentName = AgentName;
        this.BatteryON = BatteryON;
        this.LocationON = LocationON;
        this.IMUON = IMUON;
        this.targetResolution = targetResolution;
        this.batteryPeriod = batteryPeriod;
        this.locationPeriod = locationPeriod;
        this.IMUPeriod = IMUPeriod;
        this.IntermittentMode = IntermittentMode;
        this.context = context;

        // SensorDataProfile
        QoSProfile qosProfile = QoSProfile.sensorData();

        //Localizacion
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        //Sensores
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // Obtener los sensores necesarios
        // Get the necessary sensors
        orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // PUBLICADORES
        // PUBLISHERS

        this.image_publisher = this.node.createPublisher(
                std_msgs.msg.UInt8MultiArray.class,  "/" + this.AgentName + "/camera",qosProfile);

        if (this.BatteryON){
            this.battery_publisher = this.node.<std_msgs.msg.UInt8>createPublisher(
                    std_msgs.msg.UInt8.class, "/" + this.AgentName + "/battery");
        }

        if (this.LocationON){
            this.location_publisher = this.node.createPublisher(
                    std_msgs.msg.Float64MultiArray.class,  "/" + this.AgentName + "/location");
        }

        if (this.IMUON){
            this.imu_publisher = this.node.createPublisher(
                    std_msgs.msg.Float32MultiArray.class,  "/" + this.AgentName + "/IMU");
        }

        // SUSCRIPTORES
        // SUBSCRIBERS

        this.subscriber = this.node.<UInt8MultiArray>createSubscription(
                std_msgs.msg.UInt8MultiArray.class, "/" + this.AgentName + "/setcamera", this::resolutionCallback);

        this.subscriber_data = this.node.<std_msgs.msg.String>createSubscription(
                std_msgs.msg.String.class, "/" + this.AgentName + "/pose_data/F5L6", this::dataCallback);

    }


    public void start() {

        if (this.timer_battery != null) {
            this.timer_battery.cancel();
        }

        if (this.timer_location != null) {
            this.timer_location.cancel();
        }

        if (this.timer_imu != null) {
            this.timer_imu.cancel();
        }

        if (this.BatteryON){
            this.timer_battery = node.createWallTimer(this.batteryPeriod, TimeUnit.MILLISECONDS, this::onTimerBattery);
        }

        if (this.LocationON){
            this.timer_location = node.createWallTimer(this.locationPeriod, TimeUnit.MILLISECONDS, this::onTimerLocation);
        }

        if (this.IMUON){
            this.timer_imu = node.createWallTimer(this.IMUPeriod, TimeUnit.MILLISECONDS, this::onTimerIMU);
        }

    }

    private void resolutionCallback(std_msgs.msg.UInt8MultiArray msg) {

        List<Byte> dataList = msg.getData();
        if ((dataList.size() == 3)  && (dataList.get(0) <= 5) && (dataList.get(1) <= 1) && (dataList.get(2) == 111)) {

                this.targetResolution = dataList.get(0);

                if (dataList.get(1) == 1){
                    this.IntermittentMode = true;
                }
                else {
                    this.IntermittentMode = false;
                }

                receivedMessage();
        }


    }

    private void dataCallback(std_msgs.msg.String msg) {

        this.lastData = msg.getData();
        receivedData();

    }

    private void onTimerBattery() {

        float batteryPct = readBatteryLevel();
        std_msgs.msg.UInt8 battery_msg = new std_msgs.msg.UInt8();
        battery_msg.setData((byte) batteryPct);
        this.battery_publisher.publish(battery_msg);

    }

    private void onTimerLocation() {

        if (ActivityCompat.checkSelfPermission(this.context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this.context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {

            public void onLocationChanged(@NonNull Location location) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                double altitude = location.getAltitude();

                Log.d("Localizacion", "latitude: " + latitude);
                Log.d("Localizacion", "longitude: " + longitude);
                Log.d("Localizacion", "altitude: " + altitude);

                std_msgs.msg.Float64MultiArray location_msg = new std_msgs.msg.Float64MultiArray();
                double[] data = new double[]{latitude,longitude,altitude};
                location_msg.setData(data);
                location_publisher.publish(location_msg);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(@NonNull String provider) {}

            @Override
            public void onProviderDisabled(@NonNull String provider) {}
        }, null);
    }

    private void onTimerIMU(){

        final float[] orientationData = new float[4]; // Orientation quaternion
        final float[] gyroscopeData = new float[3]; // Angular velocity in the X, Y, Z axes
        final float[] accelerometerData = new float[3]; // Acceleration in the X, Y, Z axes



        SensorEventListener sensorEventListener = new SensorEventListener() {
            boolean getOrientation = false;
            boolean getGyroscope = false;
            boolean getAccelerometer = false;

            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {

                    // Obtener el cuaternión de orientación
                    // Obtain the orientation quaternion
                    float[] quaternion = new float[4];
                    SensorManager.getQuaternionFromVector(quaternion, event.values);
                    System.arraycopy(quaternion, 0, orientationData, 0, orientationData.length);
                    getOrientation = true;


                } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    // Obtener la velocidad angular en los ejes X, Y, Z
                    // Obtain the angular velocity in the X, Y, Z axes
                    System.arraycopy(event.values, 0, gyroscopeData, 0, gyroscopeData.length);
                    getGyroscope= true;

                } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    // Obtener la aceleración en los ejes X, Y, Z
                    // Obtain the acceleration in the X, Y, Z axes
                    System.arraycopy(event.values, 0, accelerometerData, 0, accelerometerData.length);
                    getAccelerometer = true;
                }

                // Procesa los datos y los manda
                // Process the data and send it

                if (getOrientation && getAccelerometer && getGyroscope) {

                    SendIMUData(orientationData, gyroscopeData, accelerometerData);

                    //Desregistro
                    // Unregister
                    sensorManager.unregisterListener(this);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // No necesitamos implementar este método en este caso
                // We don't need to implement this method in this case
            }

        };


        // Registrar los SensorEventListeners para cada sensor
        // Register the SensorEventListeners for each sensor
        sensorManager.registerListener(sensorEventListener, orientationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(sensorEventListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(sensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void SendIMUData(float[] orientationData, float[] gyroscopeData, float[] accelerometerData) {
        // Crear un array para almacenar todos los datos de los sensores
        // Create an array to store all the sensor data
        float[] allSensorData = new float[orientationData.length + gyroscopeData.length + accelerometerData.length];

        // Copiar los datos de los sensores a allSensorData
        // Copy the sensor data to allSensorData
        System.arraycopy(orientationData, 0, allSensorData, 0, orientationData.length);
        System.arraycopy(gyroscopeData, 0, allSensorData, orientationData.length, gyroscopeData.length);
        System.arraycopy(accelerometerData, 0, allSensorData, orientationData.length + gyroscopeData.length, accelerometerData.length);

        // Crear un mensaje Float32MultiArray
        // Create a Float32MultiArray message
        std_msgs.msg.Float32MultiArray sensorDataMsg = new std_msgs.msg.Float32MultiArray();

        // Asignar los datos al mensaje y enviarlo
        // Assign the data to the message and send it
        sensorDataMsg.setData(allSensorData);
        imu_publisher.publish(sensorDataMsg);

    }



    public void stop() {

        if (this.timer_battery != null) {
            this.timer_battery.cancel();
        }

        if (this.timer_location != null) {
            this.timer_location.cancel();
        }

        if (this.timer_imu != null) {
            this.timer_imu.cancel();
        }

    }

    private float readBatteryLevel() {

        Intent batteryIntent = this.context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return (level / (float) scale) * 100;
    }


    public void publish_frame(ImageProxy image){

        // Dimensiones de la imagen
        // Image size
        int w = image.getWidth();
        int h = image.getHeight();

        // Comprime y manda la imagen
        // Compresses and sends the image
        ByteBuffer Ybuffer=image.getPlanes()[0].getBuffer();
        ByteBuffer Ubuffer=image.getPlanes()[1].getBuffer();
        ByteBuffer Vbuffer=image.getPlanes()[2].getBuffer();

        //buffer remaining
        int Yr=Ybuffer.remaining();
        int Ur=Ubuffer.remaining();
        int Vr=Vbuffer.remaining();


        byte[] nv21 = new byte[Yr + Ur + Vr];

        Ybuffer.get(nv21, 0, Yr);
        Vbuffer.get(nv21, Yr, Vr);
        Ubuffer.get(nv21, Yr + Vr, Ur);

        //method1 YuvImage
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, w, h, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 90, out);
        byte[] imageBytes = out.toByteArray();

        //Bitmap
        Bitmap img= BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        img.compress(Bitmap.CompressFormat.JPEG, 50 , baos);
        byte[] b = baos.toByteArray();

        List<Byte> img_list = new ArrayList<>();
        for (byte item : b) {
            img_list.add(item);
        }
        msg_image.setData(img_list);
        image_publisher.publish(msg_image);

        img.recycle(); //close img
        image.close(); //close image
    }


    public int getResolution(){
        return this.targetResolution;
    }
    public boolean getMode() {return this.IntermittentMode; }

    // Método para configurar el Runnable
    // Method to set up the Runnable
    public void setOnMessageReceivedRunnable(Runnable runnable) {
        this.onMessageReceivedRunnable = runnable;
    }

    // Método que se llama cuando se recibe un mensaje
    // Method called when a message is received
    private void receivedMessage() {
        // Verifica que el Runnable no sea nulo antes de ejecutarlo
        // Check that the Runnable is not null before executing it
        if (onMessageReceivedRunnable != null) {
            onMessageReceivedRunnable.run();
        }
    }

    // Método para configurar el Runnable de datos
    // Method to set up the data Runnable
    public void setOnDataReceivedRunnable(Runnable runnable) {
        this.onDataReceivedRunnable = runnable;
    }

    // Método que se llama cuando se recibe un mensaje
    // Method called when a message is received
    private void receivedData() {
        // Verifica que el Runnable no sea nulo antes de ejecutarlo
        // Check that the Runnable is not null before executing it
        if (onDataReceivedRunnable != null) {
            onDataReceivedRunnable.run();
        }
    }

    public String getData(){

        return this.lastData;
    }

}

