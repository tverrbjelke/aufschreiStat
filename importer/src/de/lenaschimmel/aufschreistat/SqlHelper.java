package de.lenaschimmel.aufschreistat;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Properties;

import twitter4j.Status;
import twitter4j.User;

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;

public class SqlHelper {
	static Connection con;
	private static String connectString;
	private static String username;
	private static String password;
	private static PreparedStatement insertTweetStmt;
	private static PreparedStatement insertUserStmt;
//	private static PreparedStatement getUserIdStmt;
	private static PreparedStatement insertRetweetStmt;
	private static PreparedStatement insertLabelStmt;

	static void initDbParams() throws IOException {
		Properties properties = new Properties();
		BufferedInputStream stream = new BufferedInputStream(new FileInputStream("db.properties"));
		properties.load(stream);
		stream.close();
		connectString = properties.getProperty("connectString");
		username = properties.getProperty("username");
		password = properties.getProperty("password");
	}

	public static Connection getConnection() {
		if (con != null) {
			try {
				java.sql.Statement stmt = con.createStatement();
				ResultSet rset = stmt.executeQuery("SELECT NOW() FROM DUAL");
			} catch (SQLException e) {
				System.err.println("Detected db error, trying to reconnect.");
				con = null;
			}
		}
		if (con == null) {
			try {
				Class.forName("com.mysql.jdbc.Driver");
				con = DriverManager.getConnection(connectString, username, password);
				insertRetweetStmt = con.prepareStatement("INSERT INTO statRetweets SET status_id = ?, user_id = ?, retweeted_at = ?;");
				insertTweetStmt = con.prepareStatement("INSERT INTO statTweets SET id = ?, coordinates_lat = ?, coordinates_lon = ?, text = ?, retweet_count = ?, created_at = ?, in_reply_to_status_id = ?, user_id = ?;");
				insertUserStmt = con.prepareStatement("INSERT INTO statUsers SET id = ?, screen_name = ?, name = ?, description = ?, lang = ?, followers_count = ?, statuses_count = ?, url = ?, profile_image_url = ?;");
				insertLabelStmt = con.prepareStatement("INSERT INTO statLabels SET label = ?, parent_id = ?, reviewed = 1, description = '(Basis-Tag, siehe Hilfe)';", Statement.RETURN_GENERATED_KEYS);
				
				//getUserIdStmt =  con.prepareStatement("SELECT id FROM users WHERE screen_name = ?;");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return con;
	}

	public static PreparedStatement getInsertTweetStmt() {
		if(insertTweetStmt == null)
			getConnection();
		return insertTweetStmt;
	}

	public static PreparedStatement getInsertUserStmt() {
		if(insertUserStmt == null)
			getConnection();
		return insertUserStmt;
	}

	public static PreparedStatement getInsertRetweetStmt() {
		if(insertRetweetStmt == null)
			getConnection();
		return insertRetweetStmt;
	}
	
	public static PreparedStatement getInsertLabelStmt() {
		if(insertLabelStmt == null)
			getConnection();
		return insertLabelStmt;
	}
//
//	public static PreparedStatement getGetUserIdStmt() {
//		if(getUserIdStmt == null)
//			getConnection();
//		return getUserIdStmt;
//	}
	
	public static void insertTweet(Status tweet) throws SQLException {
		try {
			System.out.println(tweet.getCreatedAt() + ": " + tweet.getText());

			if (tweet.getRetweetedStatus() != null) {
				insertRetweet(tweet);
				return;
			}
			
			PreparedStatement insertTweetStmt = SqlHelper.getInsertTweetStmt();
			insertTweetStmt.setLong(1, tweet.getId());
			if(tweet.getGeoLocation() != null)
			{
				insertTweetStmt.setFloat(2, (float)tweet.getGeoLocation().getLatitude());
				insertTweetStmt.setFloat(3, (float)tweet.getGeoLocation().getLongitude());
			}
			else
			{
				insertTweetStmt.setFloat(2, 0);
				insertTweetStmt.setFloat(3, 0);
			}
			insertTweetStmt.setString(4, tweet.getText());
			insertTweetStmt.setInt(5, (int)tweet.getRetweetCount());
			insertTweetStmt.setTimestamp(6, new Timestamp(tweet.getCreatedAt().getTime()));
			insertTweetStmt.setLong(7, tweet.getInReplyToStatusId());
			insertTweetStmt.setLong(8, tweet.getUser().getId());
			insertTweetStmt.executeUpdate();
		} catch (MySQLIntegrityConstraintViolationException e) {
			// ignore, this is just a duplicate tweet entry, that's rather normal
		}
	}

	public static void insertUser(User user) throws SQLException {
		try {
			PreparedStatement insertUserStmt = SqlHelper.getInsertUserStmt();
			insertUserStmt.setLong(1, user.getId());
			insertUserStmt.setString(2, user.getScreenName());
			insertUserStmt.setString(3, user.getName());
			insertUserStmt.setString(4, user.getDescription());
			insertUserStmt.setString(5, user.getLang());
			insertUserStmt.setInt(6, user.getFollowersCount());
			insertUserStmt.setInt(7, user.getStatusesCount());
			insertUserStmt.setString(8, user.getURL());
			insertUserStmt.setString(9, user.getProfileImageURL());
			insertUserStmt.executeUpdate();

		} catch (MySQLIntegrityConstraintViolationException e) {
			// ignore, this is just a duplicate user entry, that's normal
		}
	}

	public static void insertRetweet(Status status) throws SQLException {
		try {
			PreparedStatement insertRetweetStmt = SqlHelper.getInsertRetweetStmt();
			insertRetweetStmt.setLong(1, status.getRetweetedStatus().getId());
			insertRetweetStmt.setLong(2, status.getUser().getId());
			insertRetweetStmt.setTimestamp(3, new Timestamp(status.getCreatedAt().getTime()));
			insertRetweetStmt.executeUpdate();
		} catch (MySQLIntegrityConstraintViolationException e) {
			// ignore, this is just a duplicate tweet entry, that's rather normal
		}
	}
	
	public static Long insertLabel(Long parent_id, String name) throws SQLException {
		try {
			//System.out.println("Insert: " + name + ", " + parent_id);
			PreparedStatement insertLabelStmt = SqlHelper.getInsertLabelStmt();
			insertLabelStmt.setString(1, name);
			if(parent_id != null)
				insertLabelStmt.setLong(2, parent_id);
			else
				insertLabelStmt.setNull(2, java.sql.Types.BIGINT);
			insertLabelStmt.executeUpdate();
			ResultSet keys = insertLabelStmt.getGeneratedKeys();
			keys.next();
			return keys.getLong(1);
		} catch (MySQLIntegrityConstraintViolationException e) {
			// ignore, this is just a duplicate tweet entry, that's rather normal
		}
		return null;
	}

	public static void insertDummyTweet(long id, String message) throws SQLException {
		System.out.println("INSERT DUMMY TWEET");

		PreparedStatement insertTweetStmt = SqlHelper.getInsertTweetStmt();
		insertTweetStmt.setLong(1, id);
		insertTweetStmt.setFloat(2, 0);
		insertTweetStmt.setFloat(3, 0);
		insertTweetStmt.setString(4, "[ERROR TWEET]: " + message.substring(message.indexOf("message - ") + 10));
		insertTweetStmt.setInt(5, 0);
		insertTweetStmt.setTimestamp(6, new Timestamp(new Date().getTime()));
		insertTweetStmt.setLong(7, -1);
		insertTweetStmt.setLong(8, -1);
		insertTweetStmt.executeUpdate();
	}

}
