<?php

include_once './db_functions.php';

// Create Object for DB_Functions class
$db = new DB_Functions();

// Get JSON posted by Android Application
$json = $_POST["dataJSON"];

// Remove Slashes
if (get_magic_quotes_gpc()) {
    $json = stripslashes($json);
}

// Decode JSON into an Array
$data = json_decode($json);

// Util arrays to create response JSON
$a = array();
$b = array();

// Loop through an Array and insert data read from JSON into MySQL DB
for ($i = 0; $i < count($data); $i++) {

    // Store data into MySQL DB
    $res = $db->storeData($data[$i]->ID, $data[$i]->Device_Address, $data[$i]->WLAN0, $data[$i]->ETH0, $data[$i]->DateTime, $data[$i]->Heart_Rate_Measurement);

    // Based on insertion, create JSON response
    if ($res) {
        $b["id"] = $data[$i]->userId;
        $b["status"] = 'yes';
        array_push($a, $b);
    } else {
        $b["id"] = $data[$i]->userId;
        $b["status"] = 'no';
        array_push($a, $b);
    }
}

// Post JSON response back to Android Application
echo json_encode($a);
