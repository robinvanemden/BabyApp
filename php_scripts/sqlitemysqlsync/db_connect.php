<?php

class DB_Connect
{
    // Constructor
    function __construct()
    {
    }

    // Destructor
    function __destruct()
    {
        // $this->close();
    }

    // Connecting to database
    public function connect()
    {
        require_once './config.php';
        // Connecting to mysql
        $con = mysql_connect(DB_HOST, DB_USER, DB_PASSWORD);
        // Selecting database
        mysql_select_db(DB_DATABASE);
        // Return database handler
        return $con;
    }

    // Closing database connection
    public function close()
    {
        mysql_close();
    }
}