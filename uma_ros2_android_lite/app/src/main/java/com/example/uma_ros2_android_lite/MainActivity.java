package com.example.uma_ros2_android_lite;


// Author: Manuel Cordoba Ramos <manuelcordoba123@gmail.com>
// Define la interfaz de la aplicación y su funcionalidad.
// Describe the app's front end and functionality.


import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.util.concurrent.HandlerExecutor;
import com.google.common.util.concurrent.ListenableFuture;


import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import org.ros2.rcljava.RCLJava;

public class MainActivity extends ROSActivity {

    // Executors
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor();

    // XML
    private TextView text_provide_name, text_additional_publishers, text_camera_settings, text_info, text_data;
    private Button Connect, SelectResolution;
    private ImageButton info, data;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch BatterySwitch, LocationSwitch, IMUSwitch, IntermittentSwitch;
    private PreviewView previewView;
    private EditText nameInput, batteryInput, locationInput, IMUInput;

    // ROS2
    private AndroidNode androidNode;

    // Variables
    private final Size[] available_resolutions = new Size[6];
    private int selectedResolution = 0;
    private String AgentName = "PX7";
    private String NodeName;
    private String inputText,inputBattery, inputLocation,inputIMU;
    private boolean BatteryON, LocationON, IMUON, showInfo, showData;

    private boolean IntermittentMode;

    private long lastAnalyzedTime = 0;

    private long currentTime;

    private final long analysisIntervalMillis = 2000; // 2 seconds

    private double batteryHz = 0.1;

    private double locationHz = 1;

    private double IMUHz = 1;

    private long batteryPeriod, locationPeriod, IMUPeriod;


    @Override
    public final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestLocationPermission();


        //Inicializa el array de resoluciones disponibles
        //Initialize the array of available resolutions
        initializeAvailableResolutions();

        //Conecta los elementos del XML con el MainActivity
        //Connect the XML elements with the MainActivity
        connectToXML();

        //Impide que el movil se apague mientras esta la app en primer plano
        //Prevent the mobile device from turning off while the app is in the foreground
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        Connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //Al pulsar el boton Connect se comprueba que el nombre es correcto
                //para avanzar, en caso contrario se pedirá que se vuelva a ingresar
                //el nombre

                // When the Connect button is pressed, it verifies that the name is correct
                // to proceed, otherwise, it will prompt to re-enter the name.


                if (nameCondition() && HzCondition()) { //Nombre correcto

                    //Comprueba los publichers adicionales y obtiene la frecuencia introducida
                    // Checks additional publishers and retrieves the entered frequency.

                    getAdditionalPublishers();

                    // Cambia visibilidad de los elementos
                    // Changes the visibility of the elements.
                    changeScreen();

                    //inputText es asignado en la funcion nameCondition
                    //si el usuario no introduce nada, el nombre por defecto es PX7

                    // inputText is assigned in the nameCondition function.
                    // If the user does not input anything, the default name is set to "PX7".

                    if (inputText != null && !inputText.isEmpty()){

                        AgentName = inputText;
                    }

                    NodeName = AgentName + "_Android";
                    text_info.setText(getInfoMessage(NodeName,AgentName,BatteryON,LocationON,IMUON,
                                                    selectedResolution,
                                                    String.valueOf(batteryHz),
                                                    String.valueOf(locationHz),
                                                    String.valueOf(IMUHz),
                                                    IntermittentMode));

                    //Inicializa ROS2
                    // Initializes ROS 2.
                    initializeROS2();


                } else {

                    //Nombre o freceuncias incorrectas
                    //Invalid name or frequencies

                    if (!nameCondition()){
                        //Muestra un mensaje por pantalla indicando los requisitos el nombre
                        //Display a message indicating the name requirements on the screen
                        showNameError();
                        try {
                            Thread.sleep(2000); // Wait for 2 seconds
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    if (!HzCondition()){
                        // Muestra un mensaje por pantalla indicando los requisitos de las frecuencias
                        // Display a message on the screen indicating the frequency requirements.
                        showHzError();
                    }


                }


            }
        });


        SelectResolution.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Cada vez que es pulsado el botón, se actualiza el valor
                // Each time the button is pressed, the value is updated.
                if (selectedResolution == 5){
                    selectedResolution = 0;
                }
                else {
                    selectedResolution ++;
                }

                // Se actualiza la apariencia del botón
                // The appearance of the button is updated.
                if (selectedResolution == 0){
                    SelectResolution.setText(R.string.resolution800_600);
                }
                else if (selectedResolution == 1) {
                    SelectResolution.setText(R.string.resolution1280_720);
                }
                else if (selectedResolution == 2) {
                    SelectResolution.setText(R.string.resolution1280_960);
                }
                else if (selectedResolution == 3) {
                    SelectResolution.setText(R.string.resolution1920_1080);
                }
                else if (selectedResolution == 4) {
                    SelectResolution.setText(R.string.resolution2048_1536);
                }
                else if (selectedResolution == 5) {
                    SelectResolution.setText(R.string.resolution3840_2160);
                }

            }
        });

        info.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Cada vez que es pulsado el botón info
                // se muestra o se oculta el mensaje de informacion
                // se cambia la apariencia del botón para dar efecto de presión

                // Each time the info button is pressed:
                // - The information message is shown or hidden.
                // - The appearance of the button is changed to give a pressed effect.
                if (showInfo){
                    showInfo = false;
                    text_info.setVisibility(View.INVISIBLE);
                    info.setImageResource(R.drawable.white_info);
                }
                else{
                    showInfo = true;
                    text_info.setVisibility(View.VISIBLE);
                    info.setImageResource(R.drawable.white_info_pressed);

                    showData = false;
                    text_data.setVisibility(View.INVISIBLE);
                    data.setImageResource(R.drawable.data_logo);

                }

            }
        });

        data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Cada vez que es pulsado el botón datos
                // se muestra o se oculta el mensaje de datos
                // se cambia la apariencia del botón para dar efecto de presión

                // Whenever the data button is pressed:
                // - Show or hide the data message.
                // - Change the button appearance to give a pressed effect.


                if (showData){
                    showData = false;
                    text_data.setVisibility(View.INVISIBLE);
                    data.setImageResource(R.drawable.data_logo);
                }
                else{
                    showData = true;
                    text_data.setVisibility(View.VISIBLE);
                    data.setImageResource(R.drawable.data_logo_pressed);

                    showInfo = false;
                    text_info.setVisibility(View.INVISIBLE);
                    info.setImageResource(R.drawable.white_info);
                }

            }
        });

        BatterySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Si el switch está activado, hacemos visible el objeto controlado
                // If the switch is activated, we make the controlled object visible.
                batteryInput.setVisibility(View.VISIBLE);
            } else {
                // Si el switch está desactivado, ocultamos el objeto controlado
                // If the switch is deactivated, we hide the controlled object.
                batteryInput.setVisibility(View.INVISIBLE);
                batteryInput.setText(""); // Clear the value that had been set
            }
        });

        LocationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Si el switch está activado, hacemos visible el objeto controlado
                // If the switch is activated, we make the controlled object visible.
                locationInput.setVisibility(View.VISIBLE);
            } else {
                // Si el switch está desactivado, ocultamos el objeto controlado
                // If the switch is deactivated, we hide the controlled object.
                locationInput.setVisibility(View.INVISIBLE);
                locationInput.setText(""); // Clear the value that had been set
            }
        });

        IMUSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Si el switch está activado, hacemos visible el objeto controlado
                // If the switch is activated, we make the controlled object visible.
                IMUInput.setVisibility(View.VISIBLE);
            } else {
                // Si el switch está desactivado, ocultamos el objeto controlado
                // If the switch is deactivated, we hide the controlled object.
                IMUInput.setVisibility(View.INVISIBLE);
                IMUInput.setText(""); // Clear the value that had been set
            }
        });






    }

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
        @Override
        public void onActivityResult(Boolean result) {

            if (!result) {
                // No se ha otorgado el permiso para la cámara
                // Camera permission has not been granted.
                Toast.makeText(getApplicationContext(), "You must grant the required permissions for proper operation", Toast.LENGTH_LONG).show();
            }
        }
    });

    private final ActivityResultLauncher<String> requestLocationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
        @Override
        public void onActivityResult(Boolean result) {
            if (result) {
                // El permiso de ubicacion fue otorgado
                //The location permission was granted
                requestCameraPermission(); // request camera permission

            } else {
                //El permiso de ubicacion no fue otorgado
                //The location permission was not granted
                Toast.makeText(getApplicationContext(), "You must grant the required permissions for proper operation", Toast.LENGTH_LONG).show();
                requestCameraPermission(); // request camera permission

            }
        }
    });

    private void requestCameraPermission() {
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void requestLocationPermission() {
        requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public void startCamera(int selecResolution, boolean intermitent_mode) {


        int aspectRatio = aspectRatio(previewView.getWidth(), previewView.getHeight());
        ListenableFuture<ProcessCameraProvider> listenableFuture = ProcessCameraProvider.getInstance(this);

        listenableFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = (ProcessCameraProvider) listenableFuture.get();

                Preview preview = new Preview.Builder().setTargetAspectRatio(aspectRatio).build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
                        .setTargetRotation(Surface.ROTATION_90)
                        .setTargetResolution(available_resolutions[selecResolution])
                        .build();


                    imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                        @Override
                        public void analyze(@NonNull ImageProxy image) {

                            if (intermitent_mode) {

                                currentTime = SystemClock.elapsedRealtime();
                                if (currentTime - lastAnalyzedTime > analysisIntervalMillis) {

                                    androidNode.publish_frame(image); // Publish the frame
                                    lastAnalyzedTime = currentTime;
                                    //Log.d("IntermittentMode", "Manda Imagen");

                                }

                            } else {

                            androidNode.publish_frame(image);

                            }


                            image.close();


                        }
                    });



                List<CameraInfo> availableCameraInfos = cameraProvider.getAvailableCameraInfos();
                CameraSelector cameraSelector = availableCameraInfos.get(0).getCameraSelector();

                cameraProvider.unbindAll();

                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);


                preview.setSurfaceProvider(previewExecutor,previewView.getSurfaceProvider());
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));

    }


    private int aspectRatio(int width, int height) {
        double previewRatio = (double) Math.max(width, height) / Math.min(width, height);
        if (Math.abs(previewRatio - 4.0 / 3.0) <= Math.abs(previewRatio - 16.0 / 9.0)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }


    private final Runnable onMessageReceivedRunnable = new Runnable() {
        @Override
        public void run() {
            startCamera(androidNode.getResolution(),androidNode.getMode());
            text_info.setText(getInfoMessage(NodeName,AgentName,
                                            BatteryON,LocationON,IMUON,
                                            androidNode.getResolution(),
                                            String.valueOf(batteryHz),
                                            String.valueOf(locationHz),
                                            String.valueOf(IMUHz),
                                            androidNode.getMode()));

            Toast.makeText(getApplicationContext(), "Camera settings have been updated", Toast.LENGTH_LONG).show();
        }
    };

    private final Runnable onDataReceivedRunnable = new Runnable() {
        @Override
        public void run() {

            text_data.setText(getDataMessage(androidNode.getData()));

        }
    };

    private String getDataMessage(String msg){
        String data_msg = "";

        String[] stringList = msg.split(";");

        String detectionID = stringList[0];
        String arucoNano = stringList[1];
        String[] markerList = stringList[2].split(",");
        String[] imuList = stringList[3].split(",");
        String battery = stringList[4];
        String[] locationList = stringList[5].split(",");
        String[] locationUtmList = stringList[6].split(",");

        data_msg += "PROCESSED IMAGE DATA\n\n";

        data_msg += "Agent name: " + AgentName + "\n\n";

        data_msg += "Detection ID: " + detectionID + "\n\n";

        data_msg += "Image Data:\n";
        data_msg += "Resolution: " + markerList[11] + "x" + markerList[12] + "\n";
        data_msg += "Used camera: " + getCameraName(markerList[13]) + "\n\n";
        data_msg += "Aruco ID: " + arucoNano + "\n\n";
        data_msg += "Fractal Data:\n";
        data_msg += "Detected submarkers: " + markerList[0] + "-" + markerList[1] + "-" + markerList[2] + "-" + markerList[3] + "-" + markerList[4] + "\n";
        data_msg += "x: " + markerList[5] + " cm  \ny: " + markerList[6] + " cm  \nz: " + markerList[7] + " cm\n";
        data_msg  += "a: " + markerList[8] + "\nb: " + markerList[9] + "\ng: " + markerList[10] + "\n\n";
        data_msg  += "Battery: " + battery + " %\n\n";
        data_msg  += "IMU:\n";
        data_msg  += "Orientation: \nw: " + imuList[0] + " \nx: " + imuList[1] + " \ny: " + imuList[2] + " \nz: " + imuList[3] + "\n";
        data_msg  += "Gyroscope: \nx: " + imuList[4] + " rad/s\ny: " + imuList[5] + " rad/s\nz: " + imuList[6] + " rad/s\n";
        data_msg += "Accelerometer: \nx: " + imuList[7] + " m/s²\ny: " + imuList[8] + " m/s²\nz: " + imuList[9] + " m/s²\n\n";
        data_msg  += "Location:\n";
        data_msg  += "Latitude: " + locationList[0] + " º\n";
        data_msg  += "Longitude: " + locationList[1] + " º\n";
        data_msg += "Altitude: " + locationList[2] + " m\n\n";
        data_msg  += "UTM:\n";
        data_msg  += "X: " + locationUtmList[0] + " m\n";
        data_msg  += "Y: " + locationUtmList[1] + " m\n";
        data_msg += "Zone: " + locationUtmList[3] + locationUtmList[4] + "\n";

        return data_msg;
    }

    private String getCameraName(String id){
        String camera = "";

        if (Objects.equals(id, "1")){
            camera = "Wide-Angle";
        }
        else{
            camera = "Normal";
        }
        return camera;
    }

    private String getInfoMessage(String NodeName,String AgentName, boolean Battery, boolean Location, boolean IMU, int resolution,
                                  String HzBattery, String HzLocation, String HzIMU, boolean modeIntermittent){

        String msg_info = "INFO\n\n";

        msg_info += "Node name: " + NodeName + "\n";

        msg_info += "Camera in topic: /" + AgentName + "/camera\n";

        if (Battery) {
            msg_info += "Battery in topic: /" + AgentName + "/battery [" + HzBattery + " Hz]\n";
        }

        if (Location) {
            msg_info += "Location in topic: /" + AgentName + "/location [" + HzLocation + " Hz]\n";
        }

        if (IMU) {
            msg_info += "IMU in topic: /" + AgentName + "/IMU [" + HzIMU + " Hz]\n";
        }

        msg_info += "\nCurrent camera: Normal \n";

        msg_info += "Current resolution: " + getResolutionTag(resolution) + "\n";

        if (modeIntermittent){
            msg_info += "Intermittent mode: enabled\n\n";
        }
        else {
            msg_info += "Intermittent mode: disabled\n\n";
        }

        msg_info += "Camera settings: /" + AgentName + "/setcamera";



        return msg_info;
    }

    private String getResolutionTag(int resolution){
        String resolution_name = "";

        if (resolution == 0){
            resolution_name = "800x600";
        } else if (resolution == 1) {
            resolution_name = "1280x720";
        } else if (resolution == 2) {
            resolution_name = "1280x960";
        } else if (resolution == 3) {
            resolution_name = "1920x1080";
        } else if (resolution == 4) {
            resolution_name = "2048x1536";
        } else if (resolution == 5) {
            resolution_name = "3840x2160";
        }
        return resolution_name;
    }

    private void initializeAvailableResolutions(){
        available_resolutions[0] = new Size(800,600);
        available_resolutions[1] = new Size(1280,720);
        available_resolutions[2] = new Size(1280,960);
        available_resolutions[3] = new Size(1920,1080);
        available_resolutions[4] = new Size(2048,1536);
        available_resolutions[5] = new Size(3840,2160);
    }

    private boolean nameCondition(){

        // Se configura el patron necesario que debe tener para evitar errores relacionados con
        // la creacion del nodo

        // The required pattern is configured to prevent errors related to node creation.
        String regex = "^(?:[a-zA-Z][a-zA-Z0-9]*)?$";
        Pattern pattern = Pattern.compile(regex);

        inputText = nameInput.getText().toString();

        Matcher matcher = pattern.matcher(inputText);

        return matcher.matches();
    }

    private boolean HzCondition(){

        // Se configura el patron necesario que debe tener para evitar errores relacionados con
        // la creacion del nodo

        // The required pattern is configured to prevent errors related to node creation.
        String regex = "^(?:(?!0(?:\\.0{1,2})?$)(?:0(?:\\.[1-9][0-9]?|\\.\\d{2})?|[1-4](?:\\.\\d{1,2})?|5(?:\\.0{1,2})?)|)$";
        Pattern pattern = Pattern.compile(regex);

        inputBattery = batteryInput.getText().toString();
        inputLocation = locationInput.getText().toString();
        inputIMU = IMUInput.getText().toString();

        Matcher matcherBattery = pattern.matcher(inputBattery);
        Matcher matcherLocation = pattern.matcher(inputLocation);
        Matcher matcherIMU = pattern.matcher(inputIMU);

        return matcherBattery.matches() && matcherLocation.matches() && matcherIMU.matches();
    }

    private void connectToXML(){
        //Conecta los elementos del XML con el MainActivity
        // Connects the elements from the XML with the MainActivity.


        // Preview de la camara
        // Camera preview
        previewView = findViewById(R.id.cameraPreview);

        // Entradas de texto
        // Text inputs
        nameInput = findViewById(R.id.nameInput);
        batteryInput = findViewById(R.id.batteryInput);
        locationInput = findViewById(R.id.locationInput);
        IMUInput = findViewById(R.id.IMUInput);

        // Switchs
        BatterySwitch = findViewById(R.id.BatterySwitch);
        LocationSwitch = findViewById(R.id.LocationSwitch);
        IMUSwitch = findViewById(R.id.IMUSwitch);
        IntermittentSwitch = findViewById(R.id.IntermittentSwitch);

        // Botones
        // Buttons
        info = findViewById(R.id.info);
        data = findViewById(R.id.data);
        Connect = findViewById(R.id.Connect);
        SelectResolution = findViewById(R.id.SelectResolution);

        // Textos
        // Texts
        text_additional_publishers = findViewById(R.id.text_additional_publishers);
        text_camera_settings = findViewById(R.id.text_camera_settings);
        text_provide_name = findViewById(R.id.text_provide_name);
        text_info = findViewById(R.id.text_info);
        text_data = findViewById(R.id.data_info);

    }

    private void getAdditionalPublishers(){

        // Check additional publishers

        // Bateria
        //Battery
        BatteryON = BatterySwitch.isChecked();
        if (inputBattery != null && !inputBattery.isEmpty()){

            batteryHz = Double.parseDouble(inputBattery);
        }
        batteryPeriod = Math.round(1000*(1/batteryHz)); // [ms]


        // Localizacion
        // Location
        LocationON = LocationSwitch.isChecked();
        if (inputLocation != null && !inputLocation.isEmpty()){

            locationHz = Double.parseDouble(inputLocation);
        }
        locationPeriod = Math.round(1000*(1/locationHz)); // [ms]


        // IMU
        IMUON = IMUSwitch.isChecked();
        if (inputIMU != null && !inputIMU.isEmpty()){

            IMUHz = Double.parseDouble(inputIMU);
        }
        IMUPeriod = Math.round(1000*(1/IMUHz)); // [ms]


        // Modo de camara
        // Camera mode
        IntermittentMode = IntermittentSwitch.isChecked();
    }

    private void changeScreen(){
        // Hace invisibles los elementos
        // Makes the elements invisible
        SelectResolution.setVisibility(View.INVISIBLE);
        Connect.setVisibility(View.INVISIBLE);
        BatterySwitch.setVisibility(View.INVISIBLE);
        LocationSwitch.setVisibility(View.INVISIBLE);
        IntermittentSwitch.setVisibility(View.INVISIBLE);
        IMUSwitch.setVisibility(View.INVISIBLE);
        nameInput.setVisibility(View.INVISIBLE);
        batteryInput.setVisibility(View.INVISIBLE);
        locationInput.setVisibility(View.INVISIBLE);
        IMUInput.setVisibility(View.INVISIBLE);
        text_additional_publishers.setVisibility(View.INVISIBLE);
        text_camera_settings.setVisibility(View.INVISIBLE);
        text_provide_name.setVisibility(View.INVISIBLE);


        // Hace visible el preview e info
        // Makes the preview and info visible
        previewView.setVisibility(View.VISIBLE);
        info.setVisibility(View.VISIBLE);
        data.setVisibility(View.VISIBLE);
    }

    private void initializeROS2(){

        RCLJava.rclJavaInit();
        androidNode = new AndroidNode(NodeName, AgentName,
                                    BatteryON, LocationON ,IMUON,
                                    selectedResolution,
                                    batteryPeriod, locationPeriod,IMUPeriod,
                                    IntermittentMode,
                                    getApplicationContext());

        getExecutor().addNode(androidNode);
        androidNode.start();
        androidNode.setOnMessageReceivedRunnable(onMessageReceivedRunnable);
        androidNode.setOnDataReceivedRunnable(onDataReceivedRunnable);
        startCamera(androidNode.getResolution(),androidNode.getMode());
    }

    private void showNameError(){

        String error_message_name = "The name must start with a letter and may only contain letters or numbers.";
        Toast.makeText(getApplicationContext(), error_message_name, Toast.LENGTH_LONG).show();

    }

    private void showHzError(){

        String error_message_hz = "The frequency of the publishers must be between 0.01 and 5 Hz.";
        Toast.makeText(getApplicationContext(), error_message_hz, Toast.LENGTH_LONG).show();
    }


}
