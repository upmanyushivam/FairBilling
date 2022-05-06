package com.FairBilling;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FairBillingDemo {

	public static final String START_ACTION = "Start";
	public static final String END_ACTION = "End";

	public List<String> readAllLinesFromFile() {

		List<String> lineList = Collections.EMPTY_LIST;

		try {
			lineList = Files.readAllLines(Paths.get("session.log"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

		}

		// System.out.println(lineList);
		return lineList;
	}

	protected List<LineInPieces> breakUpAllTheLines(List<String> lines) {

		List<LineInPieces> lineInPiecesList = new ArrayList<>();

		// Process all lines
		for (String line : lines) {
			// System.out.println("For each Loop lines"+line);
			LineInPieces lineInPieces = new LineInPieces();
			lineInPieces = breakUpLine(line);
			// System.out.println("lineInPieces of line is-->"+lineInPieces.getAction());
			// Validate action
			if (!START_ACTION.equals(lineInPieces.getAction()) && !END_ACTION.equals(lineInPieces.getAction())) {
				System.err.println(
						"Invalid action: action not set to either Start or End in line " + line + " - skipped");
				lineInPieces.setValid(false);
			}

			// Ignore invalid lines
			if (lineInPieces.isValid()) {
				lineInPiecesList.add(lineInPieces);
			}
		}
		// System.out.println("BreakUpAllTheLines------>" + lineInPiecesList);
		return lineInPiecesList;
	}

	/**
	 * The main processing
	 */
	protected List<UserResult> processFileAsList(List<String> lines) {

		if (lines == null || lines.isEmpty()) {
			return new ArrayList<>();
		}

		List<LineInPieces> piecesList = breakUpAllTheLines(lines);

		// Get first and last times in file.
		// System.out.println("value of pieceList is------>" + piecesList);
		LocalTime firstTimeInFile = null;
		LocalTime lastTimeInFile = null;
		if (piecesList.size() > 0) {
//System.out.println("First Time = "+piecesList.get(0).getHours() + ":" + piecesList.get(0).getMinutes() + ":"+ piecesList.get(0).getSeconds());

			firstTimeInFile = LocalTime.parse(piecesList.get(0).getHours() + ":" + piecesList.get(0).getMinutes() + ":"
					+ piecesList.get(0).getSeconds());

			// System.out.println("last Time in File ---->"+piecesList.get(0) );
			lastTimeInFile = LocalTime.parse(piecesList.get(piecesList.size() - 1).getHours() + ":"
					+ piecesList.get(piecesList.size() - 1).getMinutes() + ":"
					+ piecesList.get(piecesList.size() - 1).getSeconds());

		}

		// Process list into map of UserSession records
		Map<String, List<UserSession>> map = processLines(piecesList);

		List<UserResult> results = new ArrayList<>();

		// Calculate how long each session lasted in seconds
		for (String userid : map.keySet()) {
			int total = 0;
			int numberOfSessions = 0;
			for (UserSession us : map.get(userid)) {
				numberOfSessions++;
				if (us.getStartTime() == null) {
					us.setStartTime(firstTimeInFile);
				}
				if (us.getEndTime() == null) {
					us.setEndTime(lastTimeInFile);
				}
				total += +Duration.between(us.getStartTime(), us.getEndTime()).getSeconds();
			}
			results.add(new UserResult(userid, numberOfSessions, total));
		}

		return results;
	}

	/**
	 * Process the list of lines from the file into a map of sessions by userid
	 */
	protected Map<String, List<UserSession>> processLines(List<LineInPieces> lines) {

		Map<String, List<UserSession>> userSessionMap = new LinkedHashMap<>();

		for (LineInPieces line : lines) {

			// Get the sessions so far for the user
			List<UserSession> userSessionList = userSessionMap.get(line.getUserid());

			// Process into the sessions array
			userSessionList = processLine(line, userSessionList);
			userSessionMap.put(line.getUserid(), userSessionList);
		}

		return userSessionMap;
	}

	/**
	 * Core logic - accumulate the user records
	 */
	protected List<UserSession> processLine(LineInPieces line, List<UserSession> userSessionList) {

		if (userSessionList == null) {
			userSessionList = new ArrayList<UserSession>();
		}

		LocalTime lineTime = LocalTime.parse(line.getHours() + ":" + line.getMinutes() + ":" + line.getSeconds());

		// If it is a start, add a row regardless
		if (START_ACTION.equals(line.getAction())) {
			UserSession userSession = new UserSession(line.getUserid());
			userSession.setStartTime(lineTime);
			userSessionList.add(userSession);
			return userSessionList;
		}

		// Otherwise it is an end. Loop from the top to see if any starts
		// If we have an end, don't pair it with the latest start,
		// but with the first unfinished start
		for (UserSession userSession : userSessionList) {

			// Matches first Start - is it unfinished?
			if (userSession.getEndTime() == null) {
				userSession.setEndTime(lineTime);
				return userSessionList;
			}
		}

		// Otherwise just add a new End record
		UserSession userSession = new UserSession(line.getUserid());
		userSession.setEndTime(lineTime);
		userSessionList.add(userSession);
		return userSessionList;
	}

	protected LineInPieces breakUpLine(String line) {

		LineInPieces lineInPieces = new LineInPieces();
		lineInPieces.setValid(false);

		if (line == null || line.isEmpty()) {
			return lineInPieces;
		}

		String patternString = "^(\\d\\d):(\\d\\d):(\\d\\d) (.*) (.*)$";
		Pattern pattern = Pattern.compile(patternString);
		Matcher matcher = pattern.matcher(line);

		while (matcher.find()) {
			lineInPieces.setHours(matcher.group(1));
			lineInPieces.setMinutes(matcher.group(2));
			lineInPieces.setSeconds(matcher.group(3));
			lineInPieces.setUserid(matcher.group(4));
			lineInPieces.setAction(matcher.group(5));

		}

		lineInPieces.setValid(matcher.matches());

		// System.out.println("Matcher.matches" + matcher.matches());
		// System.out.println("Break Up Lines ------>" + lineInPieces);
		return lineInPieces;
	}

	public static void main(String[] args) {

		FairBillingDemo fairBillingDemo = new FairBillingDemo();
		List<String> lines = fairBillingDemo.readAllLinesFromFile();

		// md.breakUpAllTheLines(lines);

		List<UserResult> results = fairBillingDemo.processFileAsList(lines);

		// Output results
		for (UserResult result : results) {
			System.out.println(
					result.getUserId() + " " + result.getNumberOfSessions() + " " + result.getBillableTimeInSeconds());
		}

		// TODO Auto-generated method stub

	}

}
