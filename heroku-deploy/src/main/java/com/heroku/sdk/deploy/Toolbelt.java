package com.heroku.sdk.deploy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.transport.NetRC;

public class Toolbelt {

  public static String getApiToken() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    try {
      return String.valueOf(readNetrcFile().getEntry("api.heroku.com").password);
    } catch (Throwable e) {
      return runHerokuCommand(new File(System.getProperty("user.home"), "auth:token"));
    }
  }

  public static String getAppName(File projectDir) throws IOException, InterruptedException, ExecutionException, TimeoutException {
    Map<String,String> remotes = getGitRemotes(projectDir);
    if (remotes.containsKey("heroku")) {
      return parseAppFromRemote(remotes.get("heroku"));
    } else {
      throw new RuntimeException("No 'heroku' remote found.");
    }
  }

  private static String runHerokuCommand(final File projectDir, final String... command) throws InterruptedException, ExecutionException, TimeoutException {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    FutureTask<String> future =
        new FutureTask<>(new Callable<String>() {
          public String call() throws IOException {
            String herokuCmd = SystemSettings.isWindows() ? "heroku.bat" : "heroku";

            // crazy Java
            String[] fullCommand = new String[command.length + 1];
            fullCommand[0] = herokuCmd;
            System.arraycopy(command, 0, fullCommand, 1, command.length);

            ProcessBuilder pb = new ProcessBuilder().command(fullCommand);
            pb.directory(projectDir);
            Process p = pb.start();

            BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = bri.readLine()) != null) {
              output.append(line);
            }
            return output.toString();
          }
        });

    executor.execute(future);

    return future.get(10, TimeUnit.SECONDS);
  }

  private static Map<String,String> getGitRemotes(File projectDir) throws IOException {
    File gitConfigFile = new File(new File(projectDir, ".git"), "config");

    if (!gitConfigFile.exists()) {
      File userDir = new File(System.getProperty("user.home"));
      if (userDir.equals(projectDir) || projectDir.getParent() == null) {
        throw new RuntimeException("Git repo not found. Did you init one before creating your app?");
      }
      return getGitRemotes(projectDir.getParentFile());
    }

    Map<String,String> remotes = new HashMap<String, String>();

    String remote = null;
    for (String line : FileUtils.readLines(gitConfigFile)) {
      if (line != null && !line.trim().isEmpty()) {
        if (line.startsWith("[remote")) {
          remote = line.replace("[remote \"", "").replace("\"]", "");
        } else if (remote != null && line.contains("url =")) {
          String[] keyValue = line.trim().split("=");
          remotes.put(remote, keyValue[1].trim());
        }
      }
    }

    return remotes;
  }

  private static String parseAppFromRemote(String remote) {
    if (remote.startsWith("https")) {
      return remote.replace("https://git.heroku.com/", "").replace(".git", "");
    } else if (remote.startsWith("git")) {
      return remote.replace("git@heroku.com:", "").replace(".git", "");
    }
    return null;
  }

  public static NetRC readNetrcFile() throws IOException {
    String homeDir = System.getProperty("user.home");
    String netrcFilename = SystemSettings.isWindows() ? "_netrc" : ".netrc";
    File netrcFile = new File(new File(homeDir), netrcFilename);

    return readNetrcFile(netrcFile);
  }

  public static NetRC readNetrcFile(File netrcFile) throws IOException {
    if (!netrcFile.exists()) {
      throw new FileNotFoundException(netrcFile.toString());
    }

    return new NetRC(netrcFile);
  }
}
