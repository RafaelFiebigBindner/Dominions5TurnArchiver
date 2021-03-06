/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import static main.Main.logWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 *
 * @author Rafael
 */
public class Main {

    public static String version = "1.1";
    
    /*
	Internals
     */
    public static Dom5ArchiverLogger logWriter;
    private File archiverJarDirectory;
    private boolean launchGame;
    private boolean createLog;

    /*
	Required Options
     */
    private File dominionsExecutablePath;
    
    /*
	Optional Options
     */
    public static File saveDirectoryPath;
    public static MapExtractionOption mapFileExtractionModus;
    public static File mapDirectoryPath;
    public static String archiveNameSchema;
    public static int archiveTurnNumberAppendixMinimumLength;
    public static File longTermStorageDirectory;
    public static int readyArchiveDuration = -1;
    public static LongTermStorageOption longTermStorageModus;

    private ArrayList<String> whitelist;
    private ArrayList<String> blacklist;

    public void run(boolean launchGame, boolean createLog) {
	this.launchGame = launchGame;
	this.createLog = createLog;
	/*
	    Initialise values
	 */
	this.initialiseAdmin();
	this.setConfigDefaults();
	this.readConfig();
	this.sanityCheckConfigs();

	/*
	    Execute game
	 */
	if(this.launchGame)runDominions();
	
	/*
	    Read Turns from directories
	 */
	ArrayList<Turn> turns = readTurns();

	/*
	    Create games out of these Turns
	 */
	ArrayList<Game> games = generateGamesFromTurns(turns);

	/*
	    Go through games, Do Archiving
	 */
	for(Game game : games) {
	    logWriter.startNewSection("ARCHIVING GAME " + game.getName());
	    if(shallBeArchivedBasedOnBlackWhiteList(game)){
		game.doArchiving();
		Main.logWriter.log("Finished archiving game " + game.getName());
	    }else {
		logWriter.log("Skipped because of black/whitelist");
	    }
	}
	logWriter.startNewSection("FINISHED ARCHIVING SUCCESSFULLY");
    }
    
    public boolean shallBeArchivedBasedOnBlackWhiteList(Game game) {
	String gameName = game.getName();
	for (int i = 0; i < blacklist.size(); i++) {
            if (gameName.matches(blacklist.get(i))) {
                return false;
            }
        }

        if (whitelist.size() > 0) {
            for (int i = 0; i < whitelist.size(); i++) {
                if (gameName.matches(whitelist.get(i))) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    public ArrayList<Game> generateGamesFromTurns(ArrayList<Turn> turns) {
	logWriter.startNewSection("GENERATING GAMES");
	ArrayList<Game> games = new ArrayList<>();
	for (Turn turn : turns) {
	    String gameName = turn.getGameName();
	    boolean isNewGame = true;
	    for (Game game : games) {
		if (gameName.matches(game.getName())) {
		    game.registerTurn(turn);
		    isNewGame = false;
		    break;
		}
	    }
	    if (isNewGame) {
		Game g = new Game(gameName);
		games.add(g);
		g.registerTurn(turn);
	    }
	}
	return games;
    }

    /**
     * Reads all turn directories
     *
     * @return
     */
    public ArrayList<Turn> readTurns() {
	logWriter.startNewSection("READING TURN FILES");
	logWriter.log("Extracting from: " + this.saveDirectoryPath);
	File[] directories = this.saveDirectoryPath.listFiles(new FilenameFilter() {
	    @Override
	    public boolean accept(File current, String name) {
		File f = new File(current, name);
		return f.isDirectory() && !f.getName().equals("newlords");
	    }
	});

	ArrayList<Turn> turns = new ArrayList<>(directories.length);

	for (File f : directories) {
	    try {
		turns.add(new Turn(f));
	    } catch (NotATurnDirectoryException ex) {
	    }
	    
	}

	return turns;
    }

    /**
     * Initialises stuff needed for the program itself
     */
    public void initialiseAdmin() {
	archiverJarDirectory = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getParentFile();

	logWriter = new Dom5ArchiverLogger(new File(archiverJarDirectory + "\\Log.txt"), this.createLog);
    }

    public void logConfigs() {
	logWriter.log("archiverJarDirectory:" + this.archiverJarDirectory);
	logWriter.log("dominionsExecutablePath:" + this.dominionsExecutablePath);
	logWriter.log("extractMapFiles:" + Main.mapExtractionOptionToString(this.mapFileExtractionModus));
	logWriter.log("longTermStorageDirectory:" + this.longTermStorageDirectory);
	logWriter.log("mapDirectoryPath:" + this.mapDirectoryPath);
	logWriter.log("readyArchiveDuration:" + this.readyArchiveDuration);
	logWriter.log("saveDirectoryPath:" + this.saveDirectoryPath);
	logWriter.log("useLongTermStorage:" + Main.longTermStorageOptionToString(this.longTermStorageModus));
	logWriter.log("archiveNameSchema:" + this.archiveNameSchema);
	logWriter.log("archiveTurnNumberMinimumlength:" + this.archiveTurnNumberAppendixMinimumLength);
	String acc = "";
	for(String s : this.blacklist) {
	    acc += s + ";";
	}
	logWriter.log("blacklist:" + acc);
	acc = "";
	for(String s : this.whitelist) {
	    acc += s + ";";
	}
	logWriter.log("whitelist:" + acc);
    }

    private void logFinalConfigs() {
	logWriter.startNewSection("FINAL CONFIGS");
	this.logConfigs();
    }

    public void logInitialConfigs() {
	logWriter.startNewSection("DEFAULT CONFIGS");
	this.logConfigs();
    }

    public void setConfigDefaults() {
	File defaultDominionsDataPath = new File(System.getenv("APPDATA") + "\\Dominions5");
	this.blacklist = new ArrayList<>();
	this.whitelist = new ArrayList<>();
	Main.mapFileExtractionModus = MapExtractionOption.never;
	Main.longTermStorageDirectory = null;
	Main.mapDirectoryPath = new File(defaultDominionsDataPath + "\\maps");
	Main.readyArchiveDuration = -1;
	Main.saveDirectoryPath = new File(defaultDominionsDataPath + "\\savedGames");
	Main.longTermStorageModus = LongTermStorageOption.deactivated;
	Main.archiveNameSchema = "%name%_%turn%";
	Main.archiveTurnNumberAppendixMinimumLength = 2;
	this.dominionsExecutablePath = new File("C:\\Program Files (x86)\\Steam\\steamapps\\common\\Dominions5\\Dominions5.exe");
	this.logInitialConfigs();
    }

    public void readConfig() {
	logWriter.startNewSection("READING CONFIG FILE");
	this.readConfigFile();
	this.readBlackWhitelist(whitelist, new File(archiverJarDirectory + "\\Whitelist.txt"));
	this.readBlackWhitelist(blacklist, new File(archiverJarDirectory + "\\Blacklist.txt"));
	this.logFinalConfigs();
    }

    public void readConfigFile() {
	File configFile = new File(archiverJarDirectory + "\\Config.txt");
	Scanner read;

	try {
	    read = new Scanner(configFile);
	    read.useDelimiter("[\n\r]");
	    String argumentPattern = "(.*)=(.*)";
	    Matcher matcher;
	    while (read.hasNext()) {
		String line = read.next();
		matcher = Pattern.compile(argumentPattern).matcher(line);
		if (matcher.matches()) {
		    String key = matcher.group(1).trim();
		    String value = matcher.group(2).trim();
		    processConfigEntry(key, value);

		} else {
		    logWriter.log("Configfile line did not match expected pattern: " + line);

		}

	    }

	} catch (FileNotFoundException ex) {
	    logWriter.error("Could not find Config File at: " + configFile.getAbsolutePath());
	}
    }

    public void processConfigEntry(String key, String value) {
	logWriter.log("Processing Config for key:" + key + "; value:" + value);
	if (key.matches("dominionsExecutablePath")) {
	    this.dominionsExecutablePath = new File(value);
	    if (!dominionsExecutablePath.exists()) {
		logWriter.error("Could not find Dominions executable at: " + dominionsExecutablePath.getAbsolutePath());
	    }
	} else if (key.matches("saveDirectoryPath")) {
	    saveDirectoryPath = new File(value);
	    if (!saveDirectoryPath.exists()) {
		logWriter.error("SavefileDirectoryPath does not exist: " + saveDirectoryPath.getAbsolutePath());
	    }
	} else if (key.matches("extractMapFiles")) {
	    if (value.matches("never")) {
		mapFileExtractionModus = MapExtractionOption.never;
	    } else if (value.matches("cautious")) {
		mapFileExtractionModus = MapExtractionOption.cautious;
	    } else if (value.matches("force")) {
		mapFileExtractionModus = MapExtractionOption.force;
	    } else {
		logWriter.error("Could not interprete extractMapFiles, does not match never, cautious or force: " + value);
	    }
	} else if (key.matches("mapDirectoryPath")) {
	    mapDirectoryPath = new File(value);
	    if (!mapDirectoryPath.exists()) {
		logWriter.error("MapDirectoryPath does not exist: " + mapDirectoryPath.getAbsolutePath());
	    }
	} else if (key.matches("archiveNameSchema")) {
	    archiveNameSchema = value;
	    if(!archiveNameSchema.contains("%name%")) {
		archiveNameSchema = archiveNameSchema + "name";
	    }
	    if(!archiveNameSchema.contains("%turn%")) {
		archiveNameSchema = archiveNameSchema + "turn";
	    }
	} else if (key.matches("longTermStorageDirectory")) {
	    longTermStorageDirectory = new File(value);
	    if (!longTermStorageDirectory.exists()) {
		logWriter.error("LongTermStorageDirectory does not exist: " + longTermStorageDirectory.getAbsolutePath());
	    }
	} else if (key.matches("readyArchiveDuration")) {
	    readyArchiveDuration = Integer.parseInt(value);
	} else if (key.matches("useLongTermStorage")) {
	    if (value.matches("deactivated")) {
		longTermStorageModus = LongTermStorageOption.deactivated;
	    } else if (value.matches("move")) {
		longTermStorageModus = LongTermStorageOption.move;
	    } else if (value.matches("delete")) {
		longTermStorageModus = LongTermStorageOption.delete;
	    } else {
		logWriter.error("Could not interprete useLongTermStorage, does not match deactivated, move or delete: " + value);
	    }
	}else if (key.matches("archiveTurnNumberMinimumlength")) {
	    this.archiveTurnNumberAppendixMinimumLength = Integer.parseInt(value);
	} else {
	    logWriter.log("Could not identify key:" + key);
	}
    }

    public void sanityCheckConfigs() {
	this.logWriter.startNewSection("CHECKING CONFIGS FOR DETECTED PROBLEMS");

	this.checkExistence(this.dominionsExecutablePath, "DominionsExecutable");
	this.checkExistence(this.saveDirectoryPath, "SaveFilesDirectory");
	
    }
    
    public void checkExistence(File toCheck, String name) {
	if (toCheck == null) {
	    logWriter.error(name + " not set");
	} else if (!toCheck.exists()) {
	    logWriter.error(name + " does not exist at " + this.dominionsExecutablePath);
	} else {
	    logWriter.log(name + ": " + this.dominionsExecutablePath.getAbsolutePath());
	}
    }

    public void readBlackWhitelist(ArrayList<String> list, File toRead) {
	Scanner read;
	try {
	    read = new Scanner(toRead);
	    while (read.hasNext()) {
		String tmp = read.nextLine();
		logWriter.log("Adding " + tmp + " to list");
		list.add(tmp);
	    }
	    read.close();
	} catch (FileNotFoundException ex) {
	    logWriter.log("Could not read Black/Whitelist from " + toRead + "; " + ex.getMessage());
	}
    }

    /**
     * Runs the Dominions application and waits for it to close
     */
    public void runDominions() {
	Runtime run = Runtime.getRuntime();
	Process proc = null;
	try {
	    proc = run.exec(dominionsExecutablePath.getAbsolutePath());
	} catch (IOException ex) {
	    logWriter.error("Could not launch Dominions, is the path correct? " + ex.getMessage());
	}
	try {
	    proc.waitFor();
	} catch (InterruptedException ex) {
	    logWriter.error("Something unexpected happened while waiting for Dominions application to terminate. " + ex.getMessage());
	}
    }

    public static void main(String[] args) {
	boolean runGame = true;
	boolean createLog = false;
	for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-a")) {
                runGame = false;
                continue;
            }
            if (args[i].equals("-l")) {
                createLog = true;
                continue;
            }
        }
	new Main().run(runGame, createLog);
    }
    
    public static String mapExtractionOptionToString(MapExtractionOption option){
	switch (option){
	    case cautious: return "cautious";
	    case force: return "force";
	    case never: return "never";
	}
	return "ERROR mapExtractionOptionToString";	
    }
    
    public static String longTermStorageOptionToString(LongTermStorageOption option){
	switch (option){
	    case deactivated: return "dactivated";
	    case delete: return "delete";
	    case move: return "move";
	}
	return "ERROR mapExtractionOptionToString";	
    }
}
