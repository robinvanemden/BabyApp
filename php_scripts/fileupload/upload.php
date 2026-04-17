<?php
$target_dir = "upload/";
$target_file = $target_dir . basename($_FILES["fileToUpload"]["name"]);
$fileType = strtolower(pathinfo($target_file, PATHINFO_EXTENSION));

if ($fileType !== "3gpp") {
    http_response_code(415);
    echo "Unsupported file type";
    exit;
}

if (file_exists($target_file)) {
    http_response_code(409);
    echo "Already exists";
    exit;
}

if (move_uploaded_file($_FILES["fileToUpload"]["tmp_name"], $target_file)) {
    echo "Success";
} else {
    http_response_code(500);
    echo "Fail";
}
