<?php
$target_dir = "upload/";
$target_file = $target_dir . basename($_FILES["fileToUpload"]["name"]);

$uploadOk = 1;
$fileType = pathinfo($target_file, PATHINFO_EXTENSION);

// Check if file already exists
if (file_exists($target_file)) {
    $uploadOk = 0;
}

// Allow certain file formats (only .3gpp)
if ($fileType != "3gpp") {
    $uploadOk = 0;
}

// Check if $uploadOk isn't set to 0 by an error
if ($uploadOk == 1 && FALSE) {
    if (move_uploaded_file($_FILES["fileToUpload"]["tmp_name"], $target_file)) {
        echo "Success";
    } else {
        echo "Fail";
    }
}