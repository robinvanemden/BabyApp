<?php

include_once('fix_mysql.inc.php');

class DB_Functions
{

    private $db;

    // put your code here

    // Constructor
    function __construct()
    {
        include_once './db_connect.php';
        // Connecting to database
        $this->db = new DB_Connect();
        $this->db->connect();
    }

    // Destructor
    function __destruct()
    {

    }

    /**
     * Storing new user
     * returns user details
     * @param $ID
     * @param $Device_Address
     * @param $WLAN0
     * @param $ETH0
     * @param $DateTime
     * @param $Heart_Rate_Measurement
     * @return bool
     */
    public function storeData($ID, $Device_Address, $WLAN0, $ETH0, $DateTime, $Heart_Rate_Measurement)
    {
        // Insert user into database
        $result = mysql_query("INSERT INTO Heart_Rate VALUES($ID, '$Device_Address', '$WLAN0', '$ETH0', '$DateTime', $Heart_Rate_Measurement)");


        if ($result) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Getting all users
     */
    public function getAllData()
    {
        $result = mysql_query("select * FROM Heart_Rate");
        return $result;
    }

}