<?php
session_start();
ini_set("display_errors", 1);
error_reporting(E_ALL);
require_once("config.php");

mysql_connect( $config["mysqlhost"].":".$config["mysqlport"], $config["mysqluser"], $config["mysqlpassword"]);
mysql_set_charset($config["mysqlcharset"]);
mysql_select_db($config["mysqldatabase"]);

setupSession();

if(isset($_POST['query'])) {
		$query = $_POST['query'];
		parseQuery($query);
} else if(isset($_GET['query'])) {
		$query = $_GET['query'];
		parseQuery($query);
} else {
		returnError("NO QUERY");
}

function setupSession() {
	if(!isset($_SESSION['session_id']))
	{
		$session_id = rand();
		$_SESSION['session_id'] = $session_id;
		$sql = mysql_query("INSERT INTO statTaggers SET session_id='$session_id';");
		mysql_query($sql);
		$tagger_id = mysql_insert_id();
		$_SESSION['tagger_id'] = $tagger_id;
	}
	else
	{
		$session_id = $_SESSION['session_id'];
		$tagger_id = getSingleValueFromDb("SELECT id FROM statTaggers WHERE session_id='$session_id';");
		$_SESSION['tagger_id'] = $tagger_id;
	}
}

function parseQuery($query) {
	if($query == "random") {
		getRandomTweet();
	} else if($query == "url" && isset($_POST['url'])) {
		getHtmlForTweet($_POST['url']);
	} else if($query == "tags") {
		getTags();
	} else if($query == "createtag" && isset($_POST['description']) && isset($_POST['parent_id'])){
		createTag();
	} else if($query == "updatetweet" && isset($_POST['tweet'])) {
		updatetweet();
	} else if($query == "updatetag" && isset($_POST['id']) && isset($_POST['tweet'])){
		updatetag();
	} else if($query == "updatelang" && isset($_POST['lang']) && isset($_POST['tweet'])){
		updatelang();
	} else if($query == "statistic") {
		statistic();
	} else {
		returnError("UNKNOWN QUERY");
	}
}

function statistic() {
	$orig = getSingleValueFromDb("SELECT COUNT(*) FROM `statTweets`");
	$rt = getSingleValueFromDb("SELECT COUNT(*) FROM `statRetweets`");
	$sum = $orig + $rt;
	$aufschrei = getSingleValueFromDb("SELECT COUNT(*) FROM `statTweets` WHERE text LIKE '%#aufschrei%'");
	$tagged_tweets = getSingleValueFromDb("SELECT COUNT(DISTINCT `tweet_id`) FROM `statTweetsToLabels`");
	$tagged_taggers = getSingleValueFromDb("SELECT COUNT(DISTINCT `tagger_id`) FROM `statTweetsToLabels`");
	$tagged_tags = getSingleValueFromDb("SELECT COUNT(*) FROM `statTweetsToLabels`");

	$tagger_id = $_SESSION['tagger_id'];
	returnSingleValue("<ul><li>Original-Tweets: $orig</li>".
			    "<li>Retweets: $rt</li>".
			    "<li>Gesamt-Tweets: $sum</li>".
			    "<li>#aufschrei: $aufschrei</li>".
			    "<li>Getaggte Tweets: $tagged_tweets</li>".
			    "<li>Aktive Nutzer: $tagged_taggers</li>".
			    "<li>Zugewiesene Tags: $tagged_tags</li>".
			    "<li>Deine Nutzer-ID: $tagger_id</li></ul>");
}

function getSingleValueFromDb($query) {
	$sql = mysql_query($query);
	$row = mysql_fetch_row($sql);
	return $row[0];
}

function getRandomTweet() {
	return getTweet("1");
}

function getTweet($where)
{
	// This query is build according http://jan.kneschke.de/projects/mysql/order-by-rand/

	$columns = "r1.id, user_id, text, created_at, in_reply_to_status_id, retweet_count, name, screen_name, description, profile_image_url, url";
	$query = "SELECT $columns FROM statTweets AS r1, statUsers JOIN (SELECT CEIL(RAND() * (SELECT MAX(internal_id) FROM statTweets)) AS internal_id) AS r2 WHERE r1.internal_id >= r2.internal_id AND $where AND statUsers.id = r1.user_id ORDER BY r1.internal_id ASC LIMIT 1;";

	$sql = mysql_query($query);
	$return = array();
	while($result = mysql_fetch_assoc($sql)) {
    	    $return[] = $result;
	}
	return returnArray(json_encode($return));
}

function getTags() {
	$sql = mysql_query("SELECT id AS id, label AS label, description as description, parent_id FROM statLabels" . /*WHERE reviewed = 1*/ " ORDER BY parent_id, label ASC");
	$return = array();
	while($result = mysql_fetch_assoc($sql)) {
	    //$encodedResult = array_map(utf8_encode, $result);
	    $return[] = $result;
	}
	return returnArray(json_encode($return));
}

function createTag() {
	$label =  mysql_real_escape_string($_POST['label']);
	$description =  mysql_real_escape_string($_POST['description']);
	$parent_id =  mysql_real_escape_string($_POST['parent_id']);
	$sql = mysql_query("INSERT INTO statLabels SET label='$label', description='$description', parent_id='$parent_id';");
	mysql_query($sql);
	$id = mysql_insert_id();
	$return['id'] = $id;
	$return['label'] = $label;
	$return['description'] = $description;
	$return['parent_id'] = $parent_id;
	return returnArray(json_encode($return));
}

function getHtmlForTweet($url) {
	print file_get_contents($url);
}

function updatetweet() {
	$tweet =  mysql_real_escape_string($_POST['tweet']);
	$update_tweet = mysql_query("UPDATE statTweets SET tagged=tagged+1 WHERE id='$tweet'");
}

function updatetag() {
	$id = mysql_real_escape_string($_POST['id']);
	$tweet =  mysql_real_escape_string($_POST['tweet']);
	$tagger_id = $_SESSION['tagger_id'];
	$update_label = mysql_query("INSERT INTO statTweetsToLabels SET label_id='$id', tweet_id='$tweet', tagger_id='$tagger_id';");
}

function updatelang() {
	$lang = mysql_real_escape_string($_POST['lang']);
	$tweet =  mysql_real_escape_string($_POST['tweet']);
	$tagger_id = $_SESSION['tagger_id'];
	$update_lang = mysql_query("INSERT INTO statTweetsToLangs SET lang='$lang', tweet_id='$tweet', tagger_id='$tagger_id';");
}

function returnArray($result) {
	print "{\"status\": \"ok\", \"result\": ".$result."}";
}

function returnValues($result) {
	print "{\"status\": \"ok\", \"result\": {".$result."}}";
}

function returnSingleValue($result) {
	print "{\"status\": \"ok\", \"result\": \"".$result."\"}";
}

function returnError($error) {
	print "{\"status\": \"error\", \"type\": \"".$error."\"}";
}