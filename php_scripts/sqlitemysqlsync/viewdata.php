<html>

<head>

    <title>View Data</title>

    <style>
        body {
            font: normal medium/1.4 sans-serif;
        }

        table {
            border-collapse: collapse;
            width: 20%;
            margin-left: auto;
            margin-right: auto;
        }

        tr > td {
            padding: 0.25rem;
            text-align: center;
            border: 1px solid #ccc;
        }

        tr:nth-child(even) {
            background: #FAE1EE;
        }

        tr:nth-child(odd) {
            background: #edd3ff;
        }

        tr#header {
            background: #c1e2ff;
        }

        div.header {
            padding: 10px;
            background: #e0ffc1;
            width: 30%;
            color: #008000;
            margin: 5px;
        }

        div.refresh {
            margin-top: 10px;
            width: 5%;
            margin-left: auto;
            margin-right: auto;
        }

        div#norecord {
            margin-top: 10px;
            width: 15%;
            margin-left: auto;
            margin-right: auto;
        }
    </style>

    <script>
        function refreshPage() {
            location.reload();
        }
    </script>

</head>

<body>

<div style="text-align: center;">

    <div class="header">
        Android SQLite and MySQL Sync Results
    </div>

</div>

<?php
include_once './db_functions.php';
$db = new DB_Functions();
$data = $db->getAllData();
if ($data != false) {
    $no_of_data = mysql_num_rows($data);
} else {
    $no_of_data = 0;
}
?>

<?php
if ($no_of_data > 0) {
    ?>

    <table>
        <tr id="header">
            <td>ID</td>
            <td>Device_Address</td>
            <td>WLAN0</td>
            <td>ETH0</td>
            <td>DateTime</td>
            <td>Heart_Rate_Measurement</td>
        </tr>

        <?php
        while ($row = mysql_fetch_array($data)) {
            ?>
            <tr>
                <td>
							<span>
								<?php echo $row["ID"] ?>
							</span>
                </td>
                <td>
							<span>
								<?php echo $row["Device_Address"] ?>
							</span>
                </td>
                <td>
							<span>
								<?php echo $row["WLAN0"] ?>
							</span>
                </td>
                <td>
							<span>
								<?php echo $row["ETH0"] ?>
							</span>
                </td>
                <td>
							<span>
								<?php echo $row["DateTime"] ?>
							</span>
                </td>
                <td>
							<span>
								<?php echo $row["Heart_Rate_Measurement"] ?>
							</span>
                </td>
            </tr>
            <?php
        }
        ?>
    </table>

    <?php
} else {
    ?>

    <div id="norecord">
        No records in MySQL DB
    </div>

    <?php
}
?>

<div class="refresh">
    <button onclick="refreshPage()">Refresh</button>
</div>

</body>

</html>