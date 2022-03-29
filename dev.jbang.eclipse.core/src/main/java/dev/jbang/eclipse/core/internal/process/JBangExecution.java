package dev.jbang.eclipse.core.internal.process;

import static dev.jbang.eclipse.core.internal.JBangFileUtils.getFile;
import static dev.jbang.eclipse.core.internal.JBangFileUtils.getJavaVersion;
import static dev.jbang.eclipse.core.internal.JBangFileUtils.getSource;
import static dev.jbang.eclipse.core.internal.JBangFileUtils.isJBangInstruction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.eclipse.core.internal.runtime.JBangRuntime;

public class JBangExecution {

	private JBangRuntime jbang;
	private String file;
	private String javaHome;

	private static final Pattern RESOLUTION_ERROR_OLD = Pattern
			.compile("Resolving (.*)\\.\\.\\.\\[ERROR\\] Could not resolve dependency");

	private static final Pattern RESOLUTION_ERROR = Pattern.compile("\\[ERROR\\] Could not resolve dependency (.*)");
	
	public JBangExecution(JBangRuntime jbang, File file, String javaHome) {
		this.jbang = jbang;
		this.javaHome = javaHome;
		this.file = file.toString();
	}

	public JBangInfoResult getInfo(IProgressMonitor monitor) {
		List<JBangError> resolutionErrors = new ArrayList<>();
		JBangInfoResult result = new JBangInfoResult();
		result.setResolutionErrors(resolutionErrors);
		result.setBackingResource(file);
		try {
			ProcessBuilder processBuilder = new ProcessBuilder(jbang.getExecutable().toOSString(), "--verbose", "info", "tools", file);
			var env = processBuilder.environment();
			var processJavaHome = env.get("JAVA_HOME");
			if (processJavaHome == null || processJavaHome.isBlank()) {
				if (javaHome == null || javaHome.isBlank()) {
					javaHome = System.getProperty("java.home");
				}
				var envPath = env.get("PATH");
				if (javaHome != null) {
					env.put("JAVA_HOME", javaHome);
					 envPath =  envPath +File.pathSeparator+javaHome+ (javaHome.endsWith(File.separator)?"bin":File.separator +"bin");
				}
				var localBin = "/usr/local/bin";
				if (Files.isExecutable(Path.of(localBin, "jbang"))) {
					//envPath = localBin + File.pathSeparator + envPath;
				}
				env.put("PATH", envPath +File.pathSeparator+javaHome+"bin");
			}
			
			processBuilder.redirectErrorStream(true);
			Process process = processBuilder.start();
			StringBuilder processOutput = new StringBuilder();

			try (BufferedReader processOutputReader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));) {
				String readLine;
				while ((readLine = processOutputReader.readLine()) != null) {
					if(readLine.startsWith("Resolving")) {
						monitor.setTaskName(readLine);
					}
					System.err.println(readLine);
					if (readLine.contains("[ERROR]")) {
						resolutionErrors.add(sanitizeError(readLine));
					} else if (!readLine.startsWith("[jbang]") && !readLine.startsWith("Done" )) {
						processOutput.append(readLine + System.lineSeparator());
					}
				}

				process.waitFor();
			}
			String output = processOutput.toString().trim();
			if (resolutionErrors.isEmpty() && output.isBlank()) {
				resolutionErrors.add(new JBangError("Failed to get JBang informations"));
			}
			if (!output.isBlank() && !output.startsWith("{")) {
				resolutionErrors.add(new JBangError(output));
			}

			if (!resolutionErrors.isEmpty()) {
				return result;
			}

			Gson gson = new GsonBuilder().create();
			result = gson.fromJson(output, JBangInfoResult.class);
			result.setBackingResource(file);
			scanForAdditionalInfos(result);
		} catch (IOException | InterruptedException e) {
			resolutionErrors.add(new JBangError("Failed to execute JBang:" + e.getMessage()));
		}
		return result;
	}

	private void scanForAdditionalInfos(JBangInfoResult info) {
		try (BufferedReader reader = new BufferedReader(new FileReader(info.getBackingResource()))) {
			String line;
			List<String> sources = new ArrayList<>();
			Map<String,String> files = new HashMap<>();
			java.nio.file.Path baseDir = Paths.get(info.getBackingResource()).getParent();

			while ((line = reader.readLine()) != null) {
				if (!line.isBlank() && !isJBangInstruction(line)) {
					break;
				}
				if (info.getRequestedJavaVersion() == null) {
					var javaVersion = getJavaVersion(line);
					if (javaVersion != null) {
						info.setRequestedJavaVersion(javaVersion);
					}
				}
				if (info.getSources() == null) {
					String source = getSource(line);
					if (source != null) {
						String sourcePath = baseDir.resolve(source).toString();
						sources.add(sourcePath);
					}
				}
				if (info.getFiles() == null) {
					String[] tuple = getFile(line);
					if (tuple != null && tuple.length == 2) {
						String linkPath = tuple[0];
						String sourcePath = baseDir.resolve(tuple[1]).toString();
						files.put(linkPath, sourcePath);
					}
				}
			}
			if (!sources.isEmpty()) {
				collectAdditionalSources(sources);
				info.setSources(sources);
			}
			if (!files.isEmpty()) {
				info.setFiles(files);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void collectAdditionalSources(Collection<String> sources) {
		Set<String> existingSources = new HashSet<>(sources);
		for (String file : existingSources) {
			sources.addAll(collectAdditionalSources(file, sources));
		}
	}
	
	private  Collection<String> collectAdditionalSources(String file, Collection<String> existingSources){
		Set<String> newSources = new HashSet<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			java.nio.file.Path baseDir = Paths.get(file).getParent();
			while ((line = reader.readLine()) != null) {
				if (!line.isBlank() && !isJBangInstruction(line)) {
					break;
				}
				String source = getSource(line);
				if (source != null) {
					String sourcePath = baseDir.resolve(source).toString();
					if (!existingSources.contains(sourcePath)) {
						newSources.add(sourcePath);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (!newSources.isEmpty()) {
			collectAdditionalSources(newSources);
		}
		return newSources;

	}
	private JBangError sanitizeError(String errorLine) {
		// [jbang] Resolving eu.hansolo:tilesfx:1.3.4...[jbang] [ERROR] Could not
		// resolve dependency
		
		String error = errorLine.replaceAll("\\[jbang\\] ", "");
		//[ERROR] Could not resolve dependency info.picocli:picocli:4.4965.0
		Matcher matcher = RESOLUTION_ERROR.matcher(error);
		String dependency = null;
		if (matcher.find()) {
			dependency = matcher.group(1);
		} else {
			matcher = RESOLUTION_ERROR_OLD.matcher(error);
			if (matcher.find()) {
				dependency = matcher.group(1);
			}
		}
		return (dependency == null)? new JBangError(error): new JBangDependencyError(dependency); 
	}

}
